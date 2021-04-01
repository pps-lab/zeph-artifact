package ch.ethz.infk.pps.zeph.server.worker.token;

import java.util.Collections;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.zeph.server.worker.WorkerApp;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.MemberDiffType;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.Window;

public class CommitHandler {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;

	private final int MEMBER_STATUS_NEW = MemberDiffType.ADD.code();
	private final int MEMBER_STATUS_DROPPED = MemberDiffType.DEL.code();
	private final int STATUS_COMMITTED = WindowStatus.COMMITTED.code();

	private ProcessorContext ctx;
	private String taskId;
	private long universeId;

	private WindowStore<Long, ValueAndTimestamp<Digest>> ciphertextSumStore;
	private KeyValueStore<Long, Digest> committingSumStore;
	private WindowStore<Long, Integer> commitBufferStore;
	private WindowStore<Long, Integer> expectedTransformationTokenStore;
	private WindowStore<Long, Integer> memberDeltaStore;

	@SuppressWarnings("unchecked")
	public CommitHandler(ProcessorContext context, Names n) {
		this.ctx = context;
		this.taskId = ctx.taskId().toString();
		this.universeId = n.UNIVERSE_ID;

		this.ciphertextSumStore = (TimestampedWindowStore<Long, Digest>) this.ctx
				.getStateStore(n.CIPHERTEXT_SUM_STORE);

		this.committingSumStore = (KeyValueStore<Long, Digest>) this.ctx
				.getStateStore(n.COMMITTING_SUM_STORE);

		this.commitBufferStore = (WindowStore<Long, Integer>) context
				.getStateStore(n.COMMIT_BUFFER_STORE);

		this.expectedTransformationTokenStore = (WindowStore<Long, Integer>) context
				.getStateStore(n.EXPECTED_TRANSFORMATION_TOKEN_STORE);

		this.memberDeltaStore = (WindowStore<Long, Integer>) context.getStateStore(n.MEMBER_DELTA_STORE);

		this.m = MarkerManager.getMarker("taskId" + context.taskId().toString());
		this.m.addParents(WorkerApp.GLOBAL_MARKER);
	}

	public Iterable<KeyValue<Long, UniversePartitionUpdate>> commitWindowedAggregate(long producerId,
			Digest windowedAggregate, Window window) {

		final long windowStart = window.getStart();

		// remove from store
		this.ciphertextSumStore.put(producerId, null, windowStart);

		Digest aggregator = this.committingSumStore.get(windowStart);

		if (aggregator == null) {
			// first commit
			aggregator = DigestOp.empty();
		}

		aggregator = DigestOp.add(aggregator, windowedAggregate);
		this.committingSumStore.put(windowStart, aggregator);

		this.expectedTransformationTokenStore.put(producerId, 1, windowStart);

		// update the delta membership store with the info that producer sent commit
		handleDeltaMembershipStore(producerId, windowStart);

		LOG.debug(m, "{} - commit from producer={}", () -> WindowUtil.f(window), () -> producerId);

		KeyValue<Long, UniversePartitionUpdate> update = checkTaskStatusCommitted(window, null);
		if (update != null) {
			LOG.info(m, "{} - last commit of window", () -> WindowUtil.f(window));
			return Collections.singleton(update);
		}
		return null;
	}

	public static final Long GRACE_OVER_MARKER = -1l;

	public KeyValue<Long, UniversePartitionUpdate> checkTaskStatusCommitted(Window window, Boolean isGraceOver) {
		// mark as status committed if:
		// - no value is in ciphertextsumstore
		// - marker is in commit buffer store (which is otherwise empty)

		final long windowStart = window.getStart();
		if (isGraceOver == null) {
			// check commit buffer for marker
			isGraceOver = this.commitBufferStore.fetch(GRACE_OVER_MARKER, windowStart) != null;
		}

		if (isGraceOver) {

			KeyValueIterator<Windowed<Long>, ValueAndTimestamp<Digest>> iter = this.ciphertextSumStore
					.fetchAll(windowStart, windowStart);

			boolean hasUncommittedValue = iter.hasNext();
			iter.close();

			if (!hasUncommittedValue) {
				// if the grace period is over and there is no uncommitted value in the store
				// for this window
				// => the task can send the information that he has status committed for this
				// window

				return new KeyValue<>(universeId,
						new UniversePartitionUpdate(taskId, window, STATUS_COMMITTED, null, null));
			}
		}
		return null;
	}

	public Iterable<KeyValue<Long, UniversePartitionUpdate>> handleCommit(long producerId, Window window) {

		final long windowStart = window.getStart();

		ValueAndTimestamp<Digest> record = this.ciphertextSumStore.fetch(producerId, windowStart);

		if (record == null) {
			// ignored commit
			LOG.warn(m, "{} - ignored commit from producer={} (no uncommitted value in store)",
					() -> WindowUtil.f(window), () -> producerId);
			return null;
		}

		Digest windowedAggregate = record.value();
		HeacHeader header = windowedAggregate.getHeader();
		if (header.getStart() != window.getStart() - 1 || header.getEnd() != window.getEnd() - 1) {
			this.commitBufferStore.put(producerId, 1, windowStart);

			LOG.debug(m, "{} - commit placed in buffer producer={} header={}",
					() -> WindowUtil.f(window), () -> producerId, () -> header);

			return null;
		}

		return commitWindowedAggregate(producerId, windowedAggregate, window);
	}

	/**
	 * During the status change from staged to committed of the previous window
	 * (i.e. window-windowSize) all members of the universe at that time were added
	 * to the delta membership store marked temporarily as DROPPED. Now on an actual
	 * commit, if it was marker as DROPPED, it is removed from the delta membership
	 * store because the member committed as expected. If it was not marked at all
	 * in the membership delta store, it means that the producer newly joined the
	 * universe in this window and hence is added with status NEW.
	 * 
	 * @param producerId
	 * @param window
	 */
	private void handleDeltaMembershipStore(final Long producerId, final long window) {
		Integer membershipStatus = memberDeltaStore.fetch(producerId, window);

		if (membershipStatus == null) {
			// producer committed unexpected -> is new member
			memberDeltaStore.put(producerId, MEMBER_STATUS_NEW, window);
		} else if (membershipStatus == MEMBER_STATUS_DROPPED) {
			// producer committed and was marked as missing before -> remove from
			// membershipDeltaStore
			memberDeltaStore.put(producerId, null, window);
		} else {
			LOG.error(m, "{} - IllegalState in Membership Delta Store: producerId={} membershipStatus={}", window,
					producerId, membershipStatus);
			throw new IllegalStateException("Membership Delta Store");
		}
	}

}
