package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.clientdemo.ClientDemo;

/**
 * @author jkorab
 * @see <a href=
 *      "kafka-perf-tool">https://github.com/jkorab/ameliant-tools/blob/master/kafka/kafka-perf-tool</a>
 */
public abstract class Driver implements Runnable {

	public static final Marker M = ClientDemo.MARKER;
	protected final Logger log = LogManager.getLogger();

	/**
	 * Flag marking whether shutdown has been requested.
	 */
	private boolean shutdownRequested = false;

	public void requestShutdown() {
		shutdownRequested = true;
	}

	protected boolean isShutdownRequested() {
		return shutdownRequested;
	}

	@Override
	public void run() {
		try {
			drive();
		} catch (Exception ex) {
			log.error("Caught exception: {}", ex);
			throw ex;
		}
	}

	protected abstract void drive();
}
