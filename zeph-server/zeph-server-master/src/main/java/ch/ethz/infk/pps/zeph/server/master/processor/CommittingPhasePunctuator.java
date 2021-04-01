package ch.ethz.infk.pps.zeph.server.master.processor;

import java.util.Map;

import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.shared.avro.Window;

public class CommittingPhasePunctuator implements Punctuator {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;

	private final Map<Window, Cancellable> cancellableMap;
	private final StatusChangeHandler handler;
	private final Window window;
	private final long universeId;

	public CommittingPhasePunctuator(StatusChangeHandler handler, Map<Window, Cancellable> cancellableMap,
			Window window, long universeId, Marker marker) {
		this.handler = handler;
		this.cancellableMap = cancellableMap;
		this.window = new Window(window.getStart(), window.getEnd());
		this.universeId = universeId;
		this.m = marker;
	}

	@Override
	public void punctuate(long timestamp) {
		LOG.debug(m, "{} - universe={} punctuate", () -> WindowUtil.f(window), () -> universeId);
		Cancellable c = cancellableMap.remove(window);

		if (c != null) {
			c.cancel();
			handler.handleUniverseStatusCommitted(universeId, window);
		} else {
			LOG.warn(m, "{} - universe={} cancellable for window not found (cancellables={})", WindowUtil.f(window),
					universeId, cancellableMap);
		}

	}

}
