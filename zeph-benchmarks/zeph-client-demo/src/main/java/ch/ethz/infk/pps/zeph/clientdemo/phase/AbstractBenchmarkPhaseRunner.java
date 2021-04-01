package ch.ethz.infk.pps.zeph.clientdemo.phase;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.clientdemo.ClientDemo;
import ch.ethz.infk.pps.zeph.clientdemo.config.TestProfileDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.Driver;

public abstract class AbstractBenchmarkPhaseRunner {

	private static final Logger LOG = LogManager.getLogger();
	private static final Marker M = ClientDemo.MARKER;

	protected final TestProfileDefinition tpDef;

	public AbstractBenchmarkPhaseRunner(TestProfileDefinition testProfileDefinition) {
		this.tpDef = testProfileDefinition;
	}

	public abstract void run();

	protected void run(List<Driver> drivers, CountDownLatch latch, long latchTimeoutSec) {
		int driverCount = drivers.size();

		ExecutorService executorService = Executors.newFixedThreadPool(driverCount);

		drivers.forEach(driver -> {
			LOG.info(M, "submitting driver {}", driver);
			executorService.submit(driver);
		});

		try {
			if (!latch.await(latchTimeoutSec, TimeUnit.SECONDS)) {
				LOG.error(M, "drivers timed out");
				drivers.forEach(driver -> driver.requestShutdown());
				executorService.shutdown();
				throw new RuntimeException("drivers timed out");
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			executorService.shutdown();
		}
	}

}
