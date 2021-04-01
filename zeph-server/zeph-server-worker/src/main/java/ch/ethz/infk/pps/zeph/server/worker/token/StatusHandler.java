package ch.ethz.infk.pps.zeph.server.worker.token;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.zeph.server.worker.WorkerApp;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.MemberDiffType;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.Window;

public class StatusHandler {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;

	private final long MEMBERSHIP_STORE_WINDOW_KEY = -1l;
	private final String NO_RESULT_MARKER = "noresult";

	private final int MEMBER_STATUS_NEW = MemberDiffType.ADD.code();
	private final int MEMBER_STATUS_DROPPED = MemberDiffType.DEL.code();

	private final long universeId;
	private final int statusMerged = WindowStatus.MERGED.code();

	private ProcessorContext ctx;
	private String taskId;

	private WindowStore<Long, ValueAndTimestamp<Digest>> ciphertextSumStore;
	private KeyValueStore<Long, Digest> committingSumStore;
	private KeyValueStore<Long, Digest> transformingSumStore;
	private WindowStore<Long, Integer> commitBufferStore;
	private WindowStore<Long, Integer> membershipDeltaStore;
	private KeyValueStore<Long, Long> membershipStore;

	@SuppressWarnings("unchecked")
	public StatusHandler(ProcessorContext ctx, Names n) {
		this.ctx = ctx;
		this.universeId = n.UNIVERSE_ID;
		this.taskId = ctx.taskId().toString();

		this.ciphertextSumStore = (WindowStore<Long, ValueAndTimestamp<Digest>>) this.ctx
				.getStateStore(n.CIPHERTEXT_SUM_STORE);
		this.committingSumStore = (KeyValueStore<Long, Digest>) this.ctx.getStateStore(n.COMMITTING_SUM_STORE);
		this.transformingSumStore = (KeyValueStore<Long, Digest>) this.ctx.getStateStore(n.TRANSFORMING_SUM_STORE);
		this.commitBufferStore = (WindowStore<Long, Integer>) this.ctx.getStateStore(n.COMMIT_BUFFER_STORE);

		this.membershipDeltaStore = (WindowStore<Long, Integer>) this.ctx.getStateStore(n.MEMBER_DELTA_STORE);
		this.membershipStore = (KeyValueStore<Long, Long>) this.ctx.getStateStore(n.MEMBER_STORE);

		this.m = MarkerManager.getMarker("taskId" + taskId);
		this.m.addParents(WorkerApp.GLOBAL_MARKER);
	}

	public Iterable<KeyValue<Long, UniversePartitionUpdate>> handleWindowCommitted(Window window) {

		LOG.info(m, "{} - handle window committed", () -> WindowUtil.f(window));

		final long windowStart = window.getStart();
		// could verify that it's from proper origin

		Boolean taskSendsResult = null;

		// move committed sum to transforming store
		// (prevents later commits to be added to committed sum)
		Digest committedSum = committingSumStore.delete(windowStart);
		if (committedSum != null) {
			transformingSumStore.put(windowStart, committedSum);
			taskSendsResult = true;
		} else {
			LOG.warn(m, "{} - no producer committed", () -> WindowUtil.f(window));
			taskSendsResult = false;
		}

		// cleanup ciphertext sum store
		handleUncommittedProducers(window);
		handleIncompleteValues(window);

		// calculate member diff, setup member diff for next window and build output
		Map<String, Integer> memberDiffs = handleMembershipDiff(window);
		if (!taskSendsResult) {
			memberDiffs.put(NO_RESULT_MARKER, 0);
		}
		UniversePartitionUpdate update = new UniversePartitionUpdate(taskId, window, statusMerged, memberDiffs, null);
		return Collections.singleton(new KeyValue<>(universeId, update));
	}

	private void handleUncommittedProducers(Window window) {
		final long windowStart = window.getStart();

		KeyValueIterator<Windowed<Long>, ValueAndTimestamp<Digest>> iter = ciphertextSumStore.fetchAll(windowStart,
				windowStart);
		Set<Long> uncommittedProducers = new HashSet<>();
		iter.forEachRemaining(kv -> uncommittedProducers.add(kv.key.key()));
		iter.close();

		String wStr = WindowUtil.f(window);
		uncommittedProducers.forEach(pId -> {
			LOG.debug(m, "{} - producer {} did not commit (-> is ignored)", wStr, pId);

			// delete in store
			ciphertextSumStore.put(pId, null, windowStart);
		});

	}

	private void handleIncompleteValues(Window window) {
		final long windowStart = window.getStart();

		// remove the marker
		this.commitBufferStore.put(CommitHandler.GRACE_OVER_MARKER, null, windowStart);

		KeyValueIterator<Windowed<Long>, Integer> iter = this.commitBufferStore.fetchAll(windowStart, windowStart);
		Set<Long> producerIds = new HashSet<>();
		iter.forEachRemaining(kv -> producerIds.add(kv.key.key()));
		iter.close();
		String wStr = WindowUtil.f(window);
		producerIds.forEach(pId -> {
			LOG.debug(m, "{} - producer {} did have complete value (-> commit is ignored)", wStr, pId);

			// delete in store
			this.commitBufferStore.put(pId, null, windowStart);
		});
	}

	private Map<String, Integer> handleMembershipDiff(Window window) {

		final long windowStart = window.getStart();
		Map<String, Integer> diff = new HashMap<>();

		// apply changes from delta store to membership store
		KeyValueIterator<Windowed<Long>, Integer> iter = membershipDeltaStore.fetchAll(windowStart, windowStart);
		iter.forEachRemaining(kv -> {
			Long producerId = kv.key.key();
			Integer status = kv.value;

			if (status == MEMBER_STATUS_DROPPED) {
				Long oldValue = membershipStore.delete(producerId);
				if (oldValue == null) {
					throw new IllegalStateException("membership error: -");
				}

			} else if (status == MEMBER_STATUS_NEW) {
				Long oldValue = membershipStore.putIfAbsent(producerId, windowStart);
				if (oldValue != null) {
					LOG.info(m, "{} ");
					throw new IllegalStateException("membership error: +");
				}
			} else {
				throw new IllegalStateException("membership error: unknown member status");
			}
			diff.put(producerId + "", status);
		});
		iter.close();

		// update membership store window
		Long prevWindowStart = membershipStore.get(MEMBERSHIP_STORE_WINDOW_KEY);
		membershipStore.put(MEMBERSHIP_STORE_WINDOW_KEY, windowStart);
		LOG.trace(m, "{} - update membership store window (prev start={})",
				() -> WindowUtil.f(window), () -> prevWindowStart);
		if (prevWindowStart != null && windowStart <= prevWindowStart) {
			throw new IllegalStateException(
					"membership error: window prev=" + prevWindowStart + "    windowStart=" + windowStart);
		}

		// loop over membership and place all in delta store of next window
		// (a commit in the next window will remove them again)
		final long nextWindowStart = window.getEnd();
		KeyValueIterator<Long, Long> mIter = membershipStore.all();

		mIter.forEachRemaining(kv -> {
			if (kv.key != MEMBERSHIP_STORE_WINDOW_KEY) { // ignore the dummy entry marking the window
				long producerId = kv.key;

				Integer status = membershipDeltaStore.fetch(producerId, nextWindowStart);
				if (status == null) {
					// mark member temporarily as dropped for next window
					// (if this member commits in the next window, then this mark will be removed)
					membershipDeltaStore.put(producerId, MEMBER_STATUS_DROPPED, nextWindowStart);
				} else if (status == MEMBER_STATUS_NEW) {
					// if the member already committed in the next window
					// we can change him from new to "normal" by deleting him
					membershipDeltaStore.put(producerId, null, nextWindowStart);
				} else {
					LOG.error(m, "{} - Illegal MembershipStore State", () -> WindowUtil.f(window));
					throw new IllegalStateException("membership error: next window");
				}
			}
		});
		mIter.close();

		return diff;
	}

}
