package ch.ethz.infk.pps.zeph.server.worker.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.zeph.server.worker.token.CommitHandler;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.Window;

public class CiphertextSumTransformer
		implements Transformer<Windowed<Long>, Digest, Iterable<KeyValue<Long, UniversePartitionUpdate>>> {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;

	private final long universeId;
	private final Names n;
	private final long windowSizeMs;
	private final long expirationMs;
	private final int statusOpen = WindowStatus.OPEN.code();
	private final int statusStaged = WindowStatus.STAGED.code();

	private ProcessorContext ctx;
	private String taskId;

	private PriorityQueue<Long> openWindows = new PriorityQueue<>();
	private PriorityQueue<Long> stagedWindows = new PriorityQueue<>();
	private long observedStreamTime = ConsumerRecord.NO_TIMESTAMP;
	private long observedWindowStart = ConsumerRecord.NO_TIMESTAMP;

	private CommitHandler commitHandler;
	private WindowStore<Long, Integer> commitBufferStore;

	public CiphertextSumTransformer(TimeWindows windows, Names n) {
		this.universeId = n.UNIVERSE_ID;
		this.n = n;
		this.windowSizeMs = windows.size();
		this.expirationMs = windowSizeMs + windows.gracePeriodMs();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void init(ProcessorContext context) {
		this.ctx = context;
		this.taskId = this.ctx.taskId().toString();
		this.m = MarkerManager.getMarker("taskId" + taskId);

		this.commitHandler = new CommitHandler(this.ctx, this.n);
		this.commitBufferStore = (WindowStore<Long, Integer>) context
				.getStateStore(n.COMMIT_BUFFER_STORE);

		loadOpenWindows();
	}

	@Override
	public Iterable<KeyValue<Long, UniversePartitionUpdate>> transform(Windowed<Long> windowedProducerId,
			Digest value) {

		final long producerId = windowedProducerId.key();
		final long timestamp = this.ctx.timestamp();
		final long windowStart = windowedProducerId.window().start();

		LOG.debug(m, "{} - task={}  ciphertext sum transform [t={}] [observed-stream-time={}]",
				() -> WindowUtil.f(windowStart, windowSizeMs), () -> taskId, () -> timestamp, () -> observedStreamTime);

		// check if commit is stored for this -> if yes handle the value, commit
		// combination
		Integer commit = commitBufferStore.fetch(producerId, windowStart);
		if (commit != null) {
			final long windowEnd = windowedProducerId.window().end();
			HeacHeader header = value.getHeader();
			if (header.getStart() == windowStart - 1 && header.getEnd() == windowEnd - 1) {
				commitBufferStore.put(producerId, null, windowStart);
				Window window = new Window(windowStart, windowEnd);
				this.commitHandler.commitWindowedAggregate(windowedProducerId.key(), value, window);
			} else {
				LOG.warn(m, "{} - incomplete aggregate value producer={} header={}",
						() -> WindowUtil.f(windowStart, windowSizeMs), () -> producerId, () -> header);
			}
		}

		List<KeyValue<Long, UniversePartitionUpdate>> out = new ArrayList<>();

		if (windowStart > observedWindowStart) {
			// window is new -> output status OPEN

			if (observedWindowStart == ConsumerRecord.NO_TIMESTAMP) {
				// need to handle first window specially
				LOG.info(m, "{} - task={} first window", WindowUtil.f(windowStart, windowSizeMs), taskId);
				observedWindowStart = windowStart - windowSizeMs;
			}

			LOG.info(m, "task={} new windows observedWindowStart={} windowStart={}", taskId, observedWindowStart,
					windowStart);
			for (long t = observedWindowStart + windowSizeMs; t <= windowStart; t += windowSizeMs) {
				Window window = new Window(t, t + windowSizeMs);
				UniversePartitionUpdate update = new UniversePartitionUpdate(taskId, window, statusOpen, null, null);
				out.add(new KeyValue<>(universeId, update));
				openWindows.add(t);
				LOG.info(m, "{} - task={} new window", () -> WindowUtil.f(window), () -> taskId);
			}

			observedWindowStart = windowStart;

		}

		observedStreamTime = Math.max(observedStreamTime, timestamp);

		long openExpiryTime = observedStreamTime - windowSizeMs;
		while (openWindows.peek() != null && openWindows.peek() <= openExpiryTime) {
			// window is staged -> output status STAGED
			long stagedWindowStart = openWindows.poll();
			stagedWindows.add(stagedWindowStart);
			Window window = new Window(stagedWindowStart, stagedWindowStart + windowSizeMs);
			UniversePartitionUpdate update = new UniversePartitionUpdate(taskId, window, statusStaged, null, null);
			out.add(new KeyValue<>(universeId, update));
			LOG.info(m, "{} - send staged", WindowUtil.f(window));
		}

		long stagedExpiryTime = observedStreamTime - expirationMs; // -(window + grace)
		while (stagedWindows.peek() != null && stagedWindows.peek() <= stagedExpiryTime) {
			// window is staged -> output status STAGED
			long stagedWindowStart = stagedWindows.poll();

			Window stagedWindow = new Window(stagedWindowStart, stagedWindowStart + windowSizeMs);
			LOG.info(m, "{} - mark as grace over", () -> WindowUtil.f(stagedWindow));
			KeyValue<Long, UniversePartitionUpdate> kv = commitHandler.checkTaskStatusCommitted(stagedWindow, true);

			if (kv != null) {
				out.add(kv);
			} else {
				commitBufferStore.put(CommitHandler.GRACE_OVER_MARKER, 0, stagedWindowStart);
			}
		}

		if (out.isEmpty()) {
			out = null;
		}

		return out;
	}

	private void loadOpenWindows() {

		@SuppressWarnings("unchecked")
		WindowStore<Long, ValueAndTimestamp<Digest>> store = (TimestampedWindowStore<Long, Digest>) this.ctx
				.getStateStore(n.CIPHERTEXT_SUM_STORE);

		// if a window is in this store, then it can be either open or staged ->
		// its possible that output receives a stage more than once

		Set<Long> windows = new HashSet<>();

		KeyValueIterator<Windowed<Long>, ValueAndTimestamp<Digest>> iter = store.all();
		while (iter.hasNext()) {
			long windowStart = iter.next().key.window().start();
			windows.add(windowStart);
		}
		iter.close();

		openWindows.addAll(windows);
	}

	@Override
	public void close() {
		openWindows.clear();
	}

	public static class CiphertextSumTransformerSupplier
			implements TransformerSupplier<Windowed<Long>, Digest, Iterable<KeyValue<Long, UniversePartitionUpdate>>> {

		private TimeWindows timeWindows;
		private Names names;

		public CiphertextSumTransformerSupplier(TimeWindows timeWindows, Names names) {
			this.timeWindows = timeWindows;
			this.names = names;
		}

		@Override
		public Transformer<Windowed<Long>, Digest, Iterable<KeyValue<Long, UniversePartitionUpdate>>> get() {
			return new CiphertextSumTransformer(timeWindows, names);
		}

	}

}