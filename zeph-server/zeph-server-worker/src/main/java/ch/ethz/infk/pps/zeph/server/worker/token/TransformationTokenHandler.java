package ch.ethz.infk.pps.zeph.server.worker.token;

import java.util.Collections;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.zeph.server.worker.WorkerApp;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.Window;

public class TransformationTokenHandler {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;
	private String taskId;
	private final long universeId;
	private final int statusClosed = WindowStatus.CLOSED.code();

	private WindowStore<Long, Integer> expectedTransformationTokenStore;
	private KeyValueStore<Long, Digest> transformingSumStore;

	@SuppressWarnings("unchecked")
	public TransformationTokenHandler(ProcessorContext ctx, Names n) {

		this.universeId = n.UNIVERSE_ID;
		this.taskId = ctx.taskId().toString();

		this.expectedTransformationTokenStore = (WindowStore<Long, Integer>) ctx
				.getStateStore(n.EXPECTED_TRANSFORMATION_TOKEN_STORE);

		this.transformingSumStore = (KeyValueStore<Long, Digest>) ctx.getStateStore(n.TRANSFORMING_SUM_STORE);

		this.m = MarkerManager.getMarker("taskId" + taskId);
		this.m.addParents(WorkerApp.GLOBAL_MARKER);
	}

	public Iterable<KeyValue<Long, UniversePartitionUpdate>> handleTransformationToken(long producerId, Window window,
			Digest transformationToken) {

		final long windowStart = window.getStart();

		// could verify a mac here

		// check if token is expected
		Integer record = expectedTransformationTokenStore.fetch(producerId, windowStart);
		Digest aggregator = transformingSumStore.get(windowStart);
		if (record == null || aggregator == null) {
			// ignored transformation token because not expected
			LOG.warn(m, "{} - ignored transformation token: producerId={} token={} (expected={} aggregator={})",
					WindowUtil.f(window), producerId, transformationToken, record, aggregator);
			return null;
		}
		// remove producer from expected token store
		expectedTransformationTokenStore.put(producerId, null, windowStart);

		aggregator = DigestOp.add(aggregator, transformationToken);

		LOG.debug(m, "{} - transformation token from producer={} (token = {})",
				() -> WindowUtil.f(window), () -> producerId, () -> transformationToken);

		// if the expected aggregation key store is empty for this window, then all
		// expected aggregation keys have been processed and we can output the local
		// result and change the status from Committed to Closed
		KeyValueIterator<Windowed<Long>, Integer> iter = expectedTransformationTokenStore.fetchAll(windowStart,
				windowStart);
		boolean isComplete = !iter.hasNext();
		iter.close();

		if (isComplete) {
			// delete aggregator for this window
			transformingSumStore.delete(windowStart);

			LOG.info(m, "{} - closed", WindowUtil.f(window));
			LOG.debug(m, "{} - local task result = {}", WindowUtil.f(window), aggregator);

			// construct update
			return Collections.singleton(
					new KeyValue<>(universeId,
							new UniversePartitionUpdate(taskId, window, statusClosed, null, aggregator)));

		} else {
			transformingSumStore.put(windowStart, aggregator);
		}
		return null;
	}

}
