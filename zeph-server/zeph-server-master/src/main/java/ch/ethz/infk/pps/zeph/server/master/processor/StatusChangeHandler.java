package ch.ethz.infk.pps.zeph.server.master.processor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.TransformationResultOp;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Membership;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.UniversePartitionStatus;
import ch.ethz.infk.pps.shared.avro.Window;

public class StatusChangeHandler {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;

	private final int statusOpen = WindowStatus.OPEN.code();
	private final int statusStaged = WindowStatus.STAGED.code();
	private final int statusCommitted = WindowStatus.COMMITTED.code();
	private final int statusMerged = WindowStatus.MERGED.code();
	private final int statusClosed = WindowStatus.CLOSED.code();

	private final ProcessorContext ctx;
	private final Duration timeForCommit;

	private final KeyValueStore<WindowedUniverseId, Integer> statusStore;
	private final KeyValueStore<WindowedUniverseId, UniversePartitionStatus> taskStatusStore;

	private final KeyValueStore<WindowedUniverseId, Digest> resultStore;
	private final KeyValueStore<WindowedUniverseId, Membership> membershipDiffStore;

	private Map<Window, Cancellable> cancellableMap = new HashMap<>();

	@SuppressWarnings("unchecked")
	public StatusChangeHandler(ProcessorContext ctx, Duration timeForCommit) {

		this.ctx = ctx;
		this.timeForCommit = timeForCommit;

		this.statusStore = (KeyValueStore<WindowedUniverseId, Integer>) this.ctx
				.getStateStore(Names.UNIVERSE_STATUS_STORE);
		this.taskStatusStore = (KeyValueStore<WindowedUniverseId, UniversePartitionStatus>) this.ctx
				.getStateStore(Names.UNIVERSE_TASK_STATUS_STORE);

		this.resultStore = (KeyValueStore<WindowedUniverseId, Digest>) this.ctx
				.getStateStore(Names.UNIVERSE_RESULT_STORE);
		this.membershipDiffStore = (KeyValueStore<WindowedUniverseId, Membership>) this.ctx
				.getStateStore(Names.UNIVERSE_MEMBERSHIP_STORE);

		this.m = MarkerManager.getMarker("status_change_handler");

	}

	public void handleStatusOpen(Long universeId, String taskId, Window window) {

		LOG.debug(m, "{} - universe={} task={} - status open", () -> WindowUtil.f(window), () -> universeId,
				() -> taskId);

		WindowedUniverseId key = new WindowedUniverseId(universeId, window);
		UniversePartitionStatus record = taskStatusStore.get(key);

		if (record == null) {
			record = new UniversePartitionStatus(new HashMap<>());
			statusStore.put(key, statusOpen);
			LOG.info(m, "{} - universe={} - status open", () -> WindowUtil.f(window), () -> universeId);

			this.ctx.forward(key, new DeltaUniverseState(statusOpen, null, null), To.child("SINK.u-info"));
		}

		Integer taskStatus = record.getTaskStatus().get(taskId);

		if (taskStatus != null && taskStatus != statusOpen) {
			throw new IllegalStateException("open: task status must be null or open");
		}

		record.getTaskStatus().put(taskId, statusOpen);
		taskStatusStore.put(key, record);

	}

	public void handleStatusStaged(long universeId, String taskId, Window window) {

		LOG.debug(m, "{} - universe={} task={} - status staged", () -> WindowUtil.f(window), () -> universeId,
				() -> taskId);

		WindowedUniverseId key = new WindowedUniverseId(universeId, window);
		UniversePartitionStatus record = taskStatusStore.get(key);

		Integer taskStatus = record.getTaskStatus().get(taskId);

		if (taskStatus != statusOpen && taskStatus != statusStaged) {
			throw new IllegalStateException("staged: task status must be open or staged");
		}

		if (taskStatus == statusOpen) {
			record.getTaskStatus().put(taskId, statusStaged);
			taskStatusStore.put(key, record);

			boolean hasOpenTask = record.getTaskStatus().containsValue(statusOpen);

			boolean hasAllTasks = true;

			if (!hasOpenTask && hasAllTasks) {
				// all tasks are staged
				startCommittingPhase(key);
			}

		} else {
			// taskStatus == statusStaged -> do nothing
		}
	}

	private void startCommittingPhase(WindowedUniverseId key) {
		Cancellable c = ctx.schedule(timeForCommit, PunctuationType.WALL_CLOCK_TIME,
				new CommittingPhasePunctuator(this, cancellableMap, key.getWindow(), key.getUniverseId(), m));
		cancellableMap.put(key.getWindow(), c);

		LOG.info(m, "{} - universe={} - status staged", () -> WindowUtil.f(key.getWindow()), () -> key.getUniverseId());
		statusStore.put(key, statusStaged);

		this.ctx.forward(key, new DeltaUniverseState(statusStaged, null, null), To.child("SINK.u-info"));
	}

	public void handleStatusCommitted(long universeId, String taskId, Window window) {
		LOG.debug(m, "{} - universe={} task={} - status committed", () -> WindowUtil.f(window), () -> universeId,
				() -> taskId);

		WindowedUniverseId key = new WindowedUniverseId(universeId, window);
		UniversePartitionStatus record = taskStatusStore.get(key);

		Integer taskStatus = record.getTaskStatus().get(taskId);

		if (taskStatus != statusStaged && taskStatus != statusCommitted) {
			LOG.warn(m, "{} - universe={} task={} committed: task status must be staged or committed: taskStatus={}",
					() -> WindowUtil.f(window), () -> universeId, () -> taskId, () -> taskStatus);
			return;
		}

		if (taskStatus == statusStaged) {
			record.getTaskStatus().put(taskId, statusCommitted);
			boolean hasStagedTask = record.getTaskStatus().containsValue(statusStaged);
			if (!hasStagedTask) {
				Cancellable c = cancellableMap.remove(window);
				c.cancel();
				handleUniverseStatusCommitted(universeId, window);
			} else {
				taskStatusStore.put(key, record);
			}
		}
	}

	protected void handleUniverseStatusCommitted(long universeId, Window window) {

		LOG.info(m, "{} - universe={} - status committed", () -> WindowUtil.f(window), () -> universeId);

		final WindowedUniverseId key = new WindowedUniverseId(universeId, window);

		// change status to committed
		UniversePartitionStatus record = taskStatusStore.get(key);

		record.getTaskStatus().replaceAll((taskId, oldStatus) -> {

			if (oldStatus != statusStaged && oldStatus != statusCommitted) {
				LOG.error(m,
						"{} - failed handle status committed -> not all status were staged or committed  [old status = {}]",
						WindowUtil.f(window), oldStatus);
				// throw new IllegalStateException("failed handle status committed");
			}

			return statusCommitted;
		});

		// init membership diff store for diffs that will arrive later
		Membership membership = new Membership(new HashMap<>());
		membershipDiffStore.put(key, membership);

		// build committed token and forward to all tasks
		Token committedToken = new Token(window, universeId + "", null, null, statusCommitted);
		To to = To.child("SINK.u-tokens");
		record.getTaskStatus().keySet().stream()
				.map(taskIdStr -> TaskId.parse(taskIdStr))
				.forEach(taskId -> {
					this.ctx.forward((long) taskId.partition, committedToken, to);
				});

		taskStatusStore.put(key, record);
		statusStore.put(key, statusCommitted);

		this.ctx.forward(key, new DeltaUniverseState(statusCommitted, null, null), To.child("SINK.u-info"));
	}

	private static final String NO_RESULT_MARKER = "noresult";

	public void handleStatusMerged(long universeId, String taskId, Window window, Map<String, Integer> diffs) {
		Integer marker = diffs.remove(NO_RESULT_MARKER);
		boolean taskSendsResult = marker == null;

		LOG.debug(m, "{} - universe={} task={} - status merged [send result = {}] [diffs = {}]",
				() -> WindowUtil.f(window), () -> universeId, () -> taskId, () -> taskSendsResult, () -> diffs);

		WindowedUniverseId key = new WindowedUniverseId(universeId, window);
		UniversePartitionStatus record = taskStatusStore.get(key);

		Integer taskStatus = record.getTaskStatus().get(taskId);

		if (taskStatus != statusCommitted) {
			throw new IllegalStateException("merged: task status must be committed");
		}

		if (taskSendsResult) {
			record.getTaskStatus().put(taskId, statusMerged);
		} else {
			// if the task does not have any producers that committed -> the task is not
			// going to send a result (hence remove it
			record.getTaskStatus().remove(taskId);
		}
		taskStatusStore.put(key, record);

		// update membership store
		Membership membership = membershipDiffStore.get(key);
		membership.getDiff().putAll(diffs);
		membershipDiffStore.put(key, membership);

		boolean hasUnmergedTask = record.getTaskStatus().containsValue(statusCommitted);
		if (!hasUnmergedTask) {
			// all tasks are merged ->
			LOG.info(m, "{} - universe={} - status merged   (membership-diff={})", () -> WindowUtil.f(window),
					() -> universeId, () -> membership);
			statusStore.put(key, statusMerged);

			this.ctx.forward(key, new DeltaUniverseState(statusMerged, membership.getDiff(), null),
					To.child("SINK.u-info"));
		}
	}

	public void handleStatusClosed(long universeId, String taskId, Window window, Digest taskResult) {

		LOG.debug(m, "{} - universe={} task={} - status closed (task result = {})", () -> WindowUtil.f(window),
				() -> universeId, () -> taskId, () -> taskResult);

		WindowedUniverseId key = new WindowedUniverseId(universeId, window);
		UniversePartitionStatus record = taskStatusStore.get(key);

		Integer taskStatus = record.getTaskStatus().remove(taskId);

		if (taskStatus != statusMerged) {
			throw new IllegalStateException("closed: task status must be merged");
		}

		// apply the taskResult
		Digest aggregator = resultStore.get(key);
		if (aggregator == null) {
			// first task result in this window
			aggregator = DigestOp.empty();
		}
		aggregator = DigestOp.add(aggregator, taskResult);

		boolean hasAllTaskResult = record.getTaskStatus().isEmpty();
		if (hasAllTaskResult) {
			taskStatusStore.delete(key);
			statusStore.put(key, statusClosed);
			resultStore.delete(key);

			LOG.info(m, "{} - universe={} - status closed - result={}  delay={}", WindowUtil.f(window), universeId,
		
					
					aggregator, Duration.ofMillis(System.currentTimeMillis() - window.getEnd()));
			TransformationResult result = TransformationResultOp.of(aggregator, window);
			long timestamp = System.currentTimeMillis();
			this.ctx.forward(universeId, result, To.child("SINK.results").withTimestamp(timestamp));
			this.ctx.forward(key, new DeltaUniverseState(statusClosed, null, aggregator), To.child("SINK.u-info"));

		} else {
			taskStatusStore.put(key, record);
			resultStore.put(key, aggregator);
		}
	}

}
