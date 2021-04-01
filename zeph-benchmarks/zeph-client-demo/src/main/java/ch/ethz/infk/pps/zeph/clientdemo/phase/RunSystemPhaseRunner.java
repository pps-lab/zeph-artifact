package ch.ethz.infk.pps.zeph.clientdemo.phase;

import java.security.cert.Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.clientdemo.ClientDemo;
import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.TestProfileDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.UniverseDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.Driver;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.PrivacyControllerDriver;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.PublicResultDriver;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.RandomProducerDriver;
import ch.ethz.infk.pps.zeph.clientdemo.drivers.SmashingDashboardDriver;

public class RunSystemPhaseRunner extends AbstractBenchmarkPhaseRunner {

	private static final Logger LOG = LogManager.getLogger();
	private static final Marker M = ClientDemo.MARKER;
	private final ConcurrentHashMap<Long, Certificate> certificates;

	public RunSystemPhaseRunner(TestProfileDefinition testProfileDefinition,
			ConcurrentHashMap<Long, Certificate> certificates) {
		super(testProfileDefinition);
		this.certificates = certificates;

	}

	@Override
	public void run() {
		LOG.info(M, "Running Producers Phase...");

		String profileId = tpDef.getProfileId();
		long runId = tpDef.getRunId();
		long phaseTimeout = tpDef.getClientDemo().getDriverTimeoutSec();
		String kafkaBootstrapServers = tpDef.getKafka().getKafkaBootstrapServersString();

		List<Driver> drivers = new ArrayList<>();

		Map<Long, ProducerDefinition> pDefs = tpDef.getProducers().stream()
				.collect(Collectors.toMap(pDef -> pDef.getProducerId(), pDef -> pDef));

		int driverCount = tpDef.getProducers().size();
		CountDownLatch latch = new CountDownLatch(driverCount);

		// producer drivers
		tpDef.getUniverses().forEach(uDef -> {
			uDef.getUnrolledMembers().forEach(pId -> {
				if (pDefs.containsKey(pId)) {
					ProducerDefinition pDef = pDefs.get(pId);
					drivers.add(new RandomProducerDriver(profileId, runId, uDef, pDef, latch, kafkaBootstrapServers));
				}
			});
		});

		Map<Long, UniverseDefinition> uDefs = tpDef.getUniverses().stream()
				.collect(Collectors.toMap(uDef -> uDef.getUniverseId(), uDef -> uDef));

		// privacy controller drivers
		tpDef.getPrivacyControllers().forEach(cDef -> {

			Map<Long, ProducerDefinition> controllerProducerDefs = tpDef.getProducers().stream()
					.filter(pDef -> cDef.getUnrolledMembers().contains(pDef.getProducerId()))
					.collect(Collectors.toMap(pDef -> pDef.getProducerId(), pDef -> pDef));

			drivers.add(
					new PrivacyControllerDriver(profileId, runId, cDef, uDefs, controllerProducerDefs, certificates,
							kafkaBootstrapServers));
		});

		// result consumer
		drivers.add(new PublicResultDriver(kafkaBootstrapServers));

		drivers.add(new SmashingDashboardDriver(tpDef.getDashboard(), uDefs.get(tpDef.getDashboard().getUniverseId()),
				kafkaBootstrapServers, Duration.ofSeconds(15)));

		run(drivers, latch, phaseTimeout);
	}

}
