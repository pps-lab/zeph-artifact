package ch.ethz.infk.pps.zeph.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.Window;
import ch.ethz.infk.pps.zeph.client.facade.ILocalTransformationFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;

public class LocalTransformation implements IProducer {

	private static final Logger LOG = LogManager.getLogger();
	private final Marker m;

	private Heac heac;
	private final long windowSize;
	private final long producerId;
	private final long universeId;
	private final ILocalTransformationFacade facade;

	private long prevTimestamp;
	private CompletableFuture<RecordMetadata> prevSubmission = null;
	private Window currentWindow;

	public LocalTransformation(ClientConfig config, Universe universe, ILocalTransformationFacade facade) {

		this.heac = new Heac(config.getHeacKey());
		this.currentWindow = universe.getFirstWindow();

		this.producerId = config.getProducerId();
		this.universeId = config.getUniverseId();

		this.windowSize = this.currentWindow.getEnd() - this.currentWindow.getStart();

		this.facade = facade;

		this.prevTimestamp = this.currentWindow.getStart() - 1;

		this.m = MarkerManager.getMarker("m");

	}

	@Override
	public void init() {
		this.facade.init(producerId, universeId);
	}

	@Override
	public void submitHeartbeat(long timestamp) {
		submitDigest(null, timestamp);
	}

	@Override
	public void submit(Input value, long timestamp) {
		submitDigest(ApplicationAdapter.toDigest(value), timestamp);
	}

	private void submitDigest(Digest digest, long timestamp) {

		if (timestamp <= prevTimestamp) {
			LOG.error(m, "OoO: prev={} cur={}", prevTimestamp, timestamp);
			throw new IllegalArgumentException(
					"cannot submit OoO-records or multiple records with the same timestamp prev=" + prevTimestamp
							+ "  timestamp=" + timestamp);
		}

		if (timestamp >= currentWindow.getStart() && timestamp < currentWindow.getEnd()) {
			if (digest != null) { // if it is not only heartbeat
				submitValueInCurrentWindow(digest, timestamp);
			}
		} else { // new window

			if (prevTimestamp >= currentWindow.getStart() && prevTimestamp < currentWindow.getEnd()) {
				// there is a record in currentWindow -> need to close it
				boolean submitEmptyBoundaryValue = true;
				closeCurrentWindowAndMoveToNext(submitEmptyBoundaryValue);
			}

			long start = WindowUtil.getWindowStart(timestamp, windowSize);
			currentWindow = new Window(start, start + windowSize);
			prevTimestamp = start - 1;

			if (digest != null) { // if it is not only heartbeat
				submitValueInCurrentWindow(digest, timestamp);
			}
		}
	}

	public void close() {
		try {
			this.prevSubmission.get(1, TimeUnit.MINUTES);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new IllegalStateException("failed to close local transformation");
		}
	}

	private void submitValueInCurrentWindow(Digest digest, long timestamp) {

		Digest encDigest = heac.encrypt(timestamp, digest, prevTimestamp);

		CompletableFuture<RecordMetadata> cf = facade.sendCiphertext(timestamp, encDigest, prevTimestamp);
		prevSubmission = cf;
		prevTimestamp = timestamp;

		if (timestamp == currentWindow.getEnd() - 1) {
			boolean submitEmptyBoundaryValue = false;
			closeCurrentWindowAndMoveToNext(submitEmptyBoundaryValue);
		}
	}

	private void closeCurrentWindowAndMoveToNext(boolean submitEmptyBoundaryValue) {

		if (submitEmptyBoundaryValue) {
			LOG.debug(m, "Window={} - Submit Empty Boundary Timestamp (End-1)", currentWindow);
			long emptyBoundryTimestamp = currentWindow.getEnd() - 1;
			Digest emptyBoundaryRecord = heac.getKey(prevTimestamp, emptyBoundryTimestamp);

			CompletableFuture<RecordMetadata> cf = facade.sendCiphertext(emptyBoundryTimestamp, emptyBoundaryRecord,
					prevTimestamp);
			prevSubmission = cf;
		}

		// shift to next window
		currentWindow = new Window(currentWindow.getStart() + windowSize, currentWindow.getEnd() + windowSize);
		prevTimestamp = currentWindow.getStart() - 1;
	}

}
