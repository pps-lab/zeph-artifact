package ch.ethz.infk.pps.zeph.clientdemo.phase;

import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.clientdemo.ClientDemo;
import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.TestProfileDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.Driver;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.GenerateSharedKeysDriver;

public class GenerateSharedKeysPhaseRunner extends AbstractBenchmarkPhaseRunner {

	private static final Logger LOG = LogManager.getLogger();
	private static final Marker M = ClientDemo.MARKER;

	private final ConcurrentHashMap<Long, Certificate> certificates;

	public GenerateSharedKeysPhaseRunner(TestProfileDefinition testProfileDefinition,
			ConcurrentHashMap<Long, Certificate> certificates) {
		super(testProfileDefinition);
		this.certificates = certificates;
	}

	@Override
	public void run() {
		LOG.info(M, "Running Generate Shared Keys Phase...");

		long phaseTimeout = tpDef.getClientDemo().getDriverTimeoutSec();

		List<ProducerDefinition> producerDefinitions = tpDef.getProducers();
		int driverCount = producerDefinitions.size();
		CountDownLatch latch = new CountDownLatch(driverCount);

		List<Driver> drivers = producerDefinitions.stream()
				.map(pDef -> new GenerateSharedKeysDriver(pDef, certificates, latch))
				.collect(Collectors.toList());

		run(drivers, latch, phaseTimeout);
	}

}
