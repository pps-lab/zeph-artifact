package ch.ethz.infk.pps.zeph.benchmark.macro.e2e;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfig;
import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfig.BenchmarkType;
import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.util.PlaintextTransformation;
import ch.ethz.infk.pps.zeph.client.IProducer;
import ch.ethz.infk.pps.zeph.client.LocalTransformation;
import ch.ethz.infk.pps.zeph.client.PrivacyController;
import ch.ethz.infk.pps.zeph.client.facade.LocalTransformationFacade;
import ch.ethz.infk.pps.zeph.client.facade.PrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.Window;

public class E2EBenchmark {

	private static Logger LOG = LogManager.getLogger();

	private ScheduledExecutorService executorService;
	private E2EConfig config;

	private List<IProducerDriver> producerDrivers = new ArrayList<>();
	private List<PrivacyController> privacyControllers = new ArrayList<>();

	public E2EBenchmark(E2EConfig config) {
		this.config = config;
	}

	public void setup() {

		BenchmarkType benchmarkType = config.getBenchmarkType();
		boolean isE2EProducer = config.isE2eProducer();
		boolean isE2EController = config.isE2eController();

		int numberOfProducers = isE2EProducer ? config.getTotalNumberOfProducers() : 0;
		int numberOfControllers = isE2EController ? config.getTotalNumberOfControllers() : 0;

		LOG.info("init executor service with {} threads (producer threads = {}  controller threads = {})",
				numberOfProducers + numberOfControllers, numberOfProducers, numberOfControllers);
		this.executorService = Executors.newScheduledThreadPool(numberOfProducers + numberOfControllers);

		if (isE2EProducer) {
			LOG.info("building producer drivers...");
			this.producerDrivers = buildProducerDriver(config, benchmarkType, executorService);
		}

		if (isE2EController) {
			LOG.info("building privacy controllers...");
			this.privacyControllers = buildPrivacyControllers(config);

			this.privacyControllers.forEach(controller -> {
				LOG.info("privacy controller assignment={}", controller.getAssignedProducers().toString());
			});
		}

	}

	public void start() {

		privacyControllers.forEach(controller -> executorService.submit(controller));

		producerDrivers.forEach(driver -> {
			driver.init(); // connect to kafka
			executorService.submit(driver);
		});
	}

	public boolean stop() throws InterruptedException {
		producerDrivers.forEach(driver -> driver.requestShutdown());
		privacyControllers.forEach(privacyController -> privacyController.requestShutdown());

		executorService.shutdown();
		boolean hasTerminated = executorService.awaitTermination(10, TimeUnit.SECONDS);
		return hasTerminated;
	}

	private static List<PrivacyController> buildPrivacyControllers(E2EConfig config) {
		List<PrivacyController> privacyControllers = new ArrayList<>();

		int numControllers = config.getTotalNumberOfControllers();
		int numUniversesInController = config.getUniversesInController();
		int numProducersInController = config.getProducersInController();

		int numUniverses = config.getNumUniverses();
		int universeSize = config.getUniverseSize();

		long partitionOffset = config.getControllerPartition() * config.getControllerPartitionSize();

		// <universeId, producerIds> -> contains all universe members
		Multimap<Long, Long> universeMembers = HashMultimap.create();

		// <universeId, producerIds> -> contains all universe members which are handled
		// by this partition
		Multimap<Long, Long> universePartitions = HashMultimap.create();

		for (int uidx = 0; uidx < numUniverses; uidx++) {
			long universeId = config.getUniverseIds().get(uidx);
			long ustart = uidx * universeSize + 1;

			for (int i = 0; i < universeSize; i++) {
				long producerId = ustart + i;
				universeMembers.put(universeId, producerId);

				long pstart = ustart + partitionOffset;
				if (producerId >= pstart && producerId < pstart + config.getControllerPartitionSize()) {
					universePartitions.put(universeId, producerId);
				}
			}
		}

		// load the required encryption keys
		Map<Long, SecretKey> heacs = new HashMap<>();
		DataLoader.loadProducers(universePartitions.values(), null, config.getDataFolder())
				.forEach((producerId, identity) -> heacs.put(producerId, identity.getHeacKey()));

		// build the universes
		Map<Long, Universe> universeMap = buildUniverses(config.getWindowSizeMillis(), config.getUniverseIds(),
				config.getUniverseMinSize(), config.getAlpha(), config.getDelta());

		// create controllers
		for (int controllerId = 0; controllerId < numControllers; controllerId++) {

			PrivacyControllerFacade facade = new PrivacyControllerFacade(UUID.randomUUID().getMostSignificantBits(),
					config.getKafkaBootstrapServers(), config.getConsumerPollTimeout());
			PrivacyController controller = new PrivacyController(facade);

			// assign universes to the controller
			List<Long> assignedUniverseIds = new ArrayList<>();
			for (int j = 0; j < numUniversesInController; j++) {
				int uidx = (controllerId + j) % numUniverses;
				long universeId = config.getUniverseIds().get(uidx);
				controller.initUniverse(universeId, universeMap.get(universeId));
				assignedUniverseIds.add(universeId);
			}

			// within assigned universes, select assigned producers
			for (long universeId : assignedUniverseIds) {
				Collection<Long> remainingProducerIds = universePartitions.get(universeId);

				// determine which producers of the universe are assigned to this controller
				Iterator<Long> iter = remainingProducerIds.iterator();
				Set<Long> assignedProducerIds = new HashSet<>();
				for (int i = 0; i < numProducersInController; i++) {
					long producerId = iter.next();
					assignedProducerIds.add(producerId);
				}

				// remove all producers of this universe which are assigned to this controller
				// and register the assignment
				assignedProducerIds.forEach(producerId -> {
					universePartitions.remove(universeId, producerId);
					ClientConfig clientConfig = new ClientConfig(producerId, universeId, heacs.get(producerId));
					controller.addAssignment(universeId, clientConfig);
				});

				// add the shared keys for all the assigned producers which are required for
				// this universe
				controller.addSharedKeys(universeId, DataLoader.loadSharedKeys(assignedProducerIds,
						universeMembers.get(universeId), config.getDataFolder(), null));

			}

			// add the controller to the list of existing controllers
			privacyControllers.add(controller);
		}

		if (!universePartitions.isEmpty()) {
			throw new IllegalStateException(
					"not all producers were assigned to a controller: remaining" + universePartitions);
		}

		return privacyControllers;
	}

	private static List<IProducerDriver> buildProducerDriver(E2EConfig config, BenchmarkType benchmarkType,
			ScheduledExecutorService executorService) {

		List<IProducerDriver> drivers = new ArrayList<>();

		Map<Long, SecretKey> heacKeys = new HashMap<>();
		Map<Long, Universe> universeMap = new HashMap<>();
		if (benchmarkType == BenchmarkType.zeph) {
			// load data encryption keys
			Set<Long> allProducerIds = LongStream.rangeClosed(1, config.getUniverseSize() * config.getNumUniverses())
					.boxed().collect(Collectors.toSet());

			DataLoader.loadProducers(allProducerIds, null, config.getDataFolder())
					.forEach((producerId, identity) -> heacKeys.put(producerId, identity.getHeacKey()));

			universeMap = buildUniverses(config.getWindowSizeMillis(), config.getUniverseIds(),
					config.getUniverseMinSize(), config.getAlpha(), config.getDelta());
		}
		int producerPartitionSize = config.getProducerPartitionSize();
		long partitionOffset = producerPartitionSize * config.getProducerPartition();

		int numUniverses = config.getNumUniverses();
		for (int uidx = 0; uidx < numUniverses; uidx++) {
			long ustart = uidx * config.getUniverseSize() + 1;

			long universeId = config.getUniverseIds().get(uidx);
			Universe universe = universeMap.get(universeId);

			for (int i = 0; i < producerPartitionSize; i++) {
				long producerId = ustart + partitionOffset + i;
				IProducer producer = null;
				if (benchmarkType == BenchmarkType.zeph) {
					LocalTransformationFacade facade = new LocalTransformationFacade(config.getKafkaBootstrapServers());
					ClientConfig clientConfig = new ClientConfig(producerId, universeId, heacKeys.get(producerId));
					producer = new LocalTransformation(clientConfig, universe, facade);

				} else if (benchmarkType == BenchmarkType.plaintext) {
					producer = new PlaintextTransformation(producerId, universeId, config.getKafkaBootstrapServers());
				} else {
					throw new IllegalStateException("unknown benchmark type: " + benchmarkType);
				}

				IProducerDriver driver;
				if (StringUtils.isEmpty(config.getApplication())) {
					driver = new ProducerDriver(producer, executorService, producerId, config.getExpoDelayMean());

				} else {
					Path dataFilePath = Paths.get(config.getDataFolder(), config.getApplication(),
							"stream" + producerId + ".csv");
					driver = new FileProducerDriver(producer, executorService, dataFilePath, config.getTestTimeSec());

				}
				drivers.add(driver);
			}
		}

		return drivers;
	}

	private static Map<Long, Universe> buildUniverses(long windowSizeMillis, List<Long> universeIds,
			int universeMinSize, double alpha, double delta) {
		long start = WindowUtil.getWindowStart(System.currentTimeMillis(), windowSizeMillis);
		Window firstWindow = new Window(start, start + windowSizeMillis);

		Map<Long, Universe> universes = universeIds.stream().collect(Collectors.toMap(uId -> uId,
				uId -> new Universe(uId, firstWindow, null, universeMinSize, alpha, delta)));
		return universes;
	}

}
