package ch.ethz.infk.pps.zeph.benchmark.macro.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.benchmark.macro.controller.config.PrivacyControllerBenchmarkConfig;
import ch.ethz.infk.pps.zeph.benchmark.macro.controller.util.PrivacyControllerTestDataGenerator;
import ch.ethz.infk.pps.zeph.benchmark.macro.controller.util.PrivacyControllerThroughputCalculator;
import ch.ethz.infk.pps.zeph.client.PrivacyController;
import ch.ethz.infk.pps.zeph.client.facade.PrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.pojo.ProducerIdentity;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleDeserializer;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.Window;

public class PrivacyControllerBenchmarkRunner {

	private static Logger LOG = LogManager.getLogger();

	public static void main(String[] args) {

		// 1. read arguments (including kafka config)
		PrivacyControllerBenchmarkConfig config;
		LOG.info("parsing arguments...");
		CommandLineParser parser = new DefaultParser();
		Options options = buildOptions();
		try {
			CommandLine commandLine = parser.parse(options, args, true);
			config = parseCommandLine(commandLine);
		} catch (ParseException | NumberFormatException e) {
			LOG.error("failed to parse arguments", e);
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("benchmark", options);
			System.exit(0);
			throw new IllegalStateException("");
		}

		LOG.info("creating topics...");
		// 2. create kafka topics (for each universe a info and a token topic)
		Map<Long, Boolean> universeIds = createTopics(config);

		long start = System.currentTimeMillis();

		// 3. init info topics with data
		LOG.info("generating test data...");
		Multimap<Long, Long> universes = buildUniverses(config, universeIds.keySet());

		LOG.info("universes={}", universes);

		Map<Long, Universe> universeConfigs = universeIds.keySet().stream()
				.collect(Collectors.toMap(universeId -> universeId,
						universeId -> new Universe(universeId, new Window(0l, Duration.ofSeconds(30).toMillis()), null,
								config.getUniverseMinimalSize(), config.getAlpha(), config.getDelta())));

		PrivacyControllerTestDataGenerator testDataGenerator = new PrivacyControllerTestDataGenerator(universeConfigs,
				universes, config);

		testDataGenerator.generateRecords(universeIds);

		long time = System.currentTimeMillis() - start;

		LOG.info("secs={}", time / 1000);

		// 4. setup shared keys etc.
		LOG.info("Setup shared keys...");
		Map<Long, ProducerIdentity> identities = DataLoader.createAndLoadProducers(universes.values(), null,
				config.getDataFolder(), false);

		List<ClientConfig> clientConfigs = new ArrayList<>();

		universes.asMap().forEach((universeId, members) -> {
			List<Long> clients = members.stream().limit(config.getClients()).collect(Collectors.toList());

			clients.forEach(client -> {

				ProducerIdentity identity = identities.get(client);

				ClientConfig clientConfig = new ClientConfig();
				clientConfig.setProducerId(client);
				clientConfig.setHeacKey(identity.getHeacKey());
				clientConfig.setPrivateKey(identity.getPrivateKey());
				clientConfig.setUniverseId(universeId);
				clientConfig.setSharedKeys(DataLoader.createAndLoadSharedKeys(Collections.singleton(client),
						universes.get(universeId), config.getDataFolder(), null, true));

				clientConfigs.add(clientConfig);

			});

		});

		// 5. loop over repeat experiment
		Duration pollTimeout = Duration.ofSeconds(1);
		long transformationId = new Random().nextLong();

		LOG.info("Start Experiment ");

		KafkaProducer<Long, Token> tokenProducer = PrivacyControllerFacade.buildTokenProducer(
				"producer-transformation-" + transformationId, config.getBootstrapServer(), 1 << 15);
		PrivacyControllerFacade facade = new PrivacyControllerFacade(transformationId, config.getBootstrapServer(),
				pollTimeout, tokenProducer);

		PrivacyController privacyController = new PrivacyController(clientConfigs, universeConfigs, facade);

		ExecutorService executorService = Executors.newFixedThreadPool(1);
		Future<?> f = executorService.submit(privacyController);

		try {
			f.get(config.getTestTimeSec(), TimeUnit.SECONDS);
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException("failed to execute iteration", e);
		} catch (TimeoutException e) {
			try {
				privacyController.requestShutdown();
				executorService.shutdown();
				executorService.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				throw new IllegalStateException("failed to stop experiment", e1);
			}

		}

		LOG.info("calculating throughput...");

		// 6. Calculate TP based on result topics
		PrivacyControllerThroughputCalculator calculator = buildThroughputCalculator(config, universes.keySet());

		Table<Window, String, Long> countTable = calculator.calculateThroughput();

		PrivacyControllerThroughputCalculator.print(countTable, config, config.getOutputFile());

		// delete the created topics
		if (config.deleteTokenTopics()) {
			// deleteTokenTopics(config, universes.keySet());
		}

		if (config.deleteAllInfoTopics()) {
			deleteAllInfosTopics(config);
		}

	}

	private static PrivacyControllerThroughputCalculator buildThroughputCalculator(
			PrivacyControllerBenchmarkConfig config, Set<Long> universeIds) {
		Properties props = new Properties();
		String tp = "tp-calculator" + new Random().nextLong();
		props.put(ConsumerConfig.CLIENT_ID_CONFIG, tp);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, tp);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServer());

		KafkaConsumer<Long, Token> kafkaConsumer = new KafkaConsumer<>(props, new LongDeserializer(),
				new SpecificAvroSimpleDeserializer<Token>(Token.getClassSchema()));
		PrivacyControllerThroughputCalculator calculator = new PrivacyControllerThroughputCalculator(universeIds,
				kafkaConsumer, config);
		return calculator;
	}

	private static Multimap<Long, Long> buildUniverses(PrivacyControllerBenchmarkConfig config, Set<Long> universeIds) {
		Multimap<Long, Long> universes = HashMultimap.create();

		long numParticipants = config.getUniverses() * config.getUniverseSize();
		List<Long> participants = LongStream.rangeClosed(1, numParticipants).boxed().collect(Collectors.toList());
		Iterator<Long> iter = universeIds.iterator();
		for (int i = 0; i < universeIds.size(); i++) {
			long universeId = iter.next();
			int start = i * config.getUniverseSize();
			universes.putAll(universeId, participants.subList(start, start + config.getUniverseSize()));
		}
		return universes;
	}

	private static void deleteAllInfosTopics(PrivacyControllerBenchmarkConfig config) {
		Properties props = new Properties();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServer());
		AdminClient admin = KafkaAdminClient.create(props);

		try {
			Set<String> topicsToDelete = new HashSet<>();

			Pattern pattern = Pattern.compile("^u([0-9]+)-infos");

			admin.listTopics().names().get().forEach(topic -> {
				Matcher matcher = pattern.matcher(topic);
				if (matcher.find()) {
					topicsToDelete.add(topic);
				}
			});

			admin.deleteTopics(topicsToDelete);

		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException("failed to delete topics", e);
		} finally {
			admin.close();
		}

	}

	private static void deleteTokenTopics(PrivacyControllerBenchmarkConfig config, Set<Long> universes) {

		Properties props = new Properties();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServer());
		AdminClient admin = KafkaAdminClient.create(props);
		try {
			List<String> topics = new ArrayList<>();
			universes.forEach(universeId -> {
				topics.add(Names.getTokensTopic(universeId));
			});

			DeleteTopicsResult result = admin.deleteTopics(topics);
			result.all().get();

		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException("failed to delete topics", e);
		} finally {
			admin.close();
		}
	}

	private static long getUniverseId(long universeSize, long universeIdx) {

		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(Longs.toByteArray(universeSize));
			outputStream.write(Longs.toByteArray(universeIdx));
			byte c[] = outputStream.toByteArray();
			int universeId = Hashing.sha256().hashBytes(c).asInt();

			return Utils.toPositive(universeId);
		} catch (IOException e) {
			throw new IllegalStateException("failed to convert universe id");
		}

	}

	private static Map<Long, Boolean> createTopics(PrivacyControllerBenchmarkConfig config) {
		Properties props = new Properties();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServer());
		AdminClient admin = KafkaAdminClient.create(props);

		Map<Long, Boolean> universes;
		try {

			Pattern pattern = Pattern.compile("^u([0-9]+)-infos");
			Pattern patternTokens = Pattern.compile("^u([0-9]+)-tokens");

			universes = LongStream.rangeClosed(1, config.getUniverses()).boxed()
					.map(uIdx -> getUniverseId(config.getUniverseSize(), uIdx))
					.collect(Collectors.toMap(uId -> uId, uId -> true));

			Map<Long, NewTopic> infoTopics = new HashMap<>();
			Map<Long, NewTopic> tokenTopics = new HashMap<>();

			universes.forEach((universeId, init) -> {
				NewTopic infoTopic = new NewTopic(Names.getInfosTopic(universeId), 1, (short) 1);
				infoTopics.put(universeId, infoTopic);
				NewTopic tokenTopic = new NewTopic(Names.getTokensTopic(universeId), config.getPartitions(), (short) 1);
				tokenTopics.put(universeId, tokenTopic);
			});

			admin.listTopics().listings().get().forEach(topic -> {

				Matcher matcher = pattern.matcher(topic.name());
				Matcher matcherTokens = patternTokens.matcher(topic.name());
				if (matcher.find()) {
					long mUId = Long.parseLong(matcher.group(1));
					if (universes.containsKey(mUId)) {
						universes.put(mUId, false);
						infoTopics.remove(mUId);
					}
				} else if (matcherTokens.find()) {
//					try {
//						long mUId = Long.parseLong(matcherTokens.group(1));
//						if (universes.containsKey(mUId)) {
//							admin.deleteTopics(Collections.singleton(topic.name())).all().get();
//						}
//					} catch (InterruptedException | ExecutionException e) {
//
//					}
				}
			});

			TimeUnit.SECONDS.sleep(3);
			admin.createTopics(infoTopics.values()).all().get();
			admin.createTopics(tokenTopics.values()).all().get();
			admin.close();

		} catch (ExecutionException | InterruptedException e) {
			throw new IllegalStateException("failed to create topics", e);
		}
		return universes;
	}

	private static PrivacyControllerBenchmarkConfig parseCommandLine(CommandLine commandLine) {
		PrivacyControllerBenchmarkConfig config = new PrivacyControllerBenchmarkConfig();

		config.setBootstrapServer(commandLine.getOptionValue("bootstrap-server"));
		config.setPartitions(Integer.parseInt(commandLine.getOptionValue("partitions")));
		config.setUniverses(Integer.parseInt(commandLine.getOptionValue("universes")));
		config.setUniverseSize(Integer.parseInt(commandLine.getOptionValue("universe-size")));
		config.setUniverseMinimalSize(Integer.parseInt(commandLine.getOptionValue("universe-min-size")));
		config.setAlpha(Double.parseDouble(commandLine.getOptionValue("alpha", "0.5")));
		config.setDelta(Double.valueOf(commandLine.getOptionValue("delta", "1.0E-5")));
		config.setClients(Integer.parseInt(commandLine.getOptionValue("clients")));
		config.setClientDropoutProb(Double.parseDouble(commandLine.getOptionValue("client-dropout-prob")));
		config.setClientComebackProb(Double.parseDouble(commandLine.getOptionValue("client-comeback-prob")));
		config.setTestTimeSec(Integer.parseInt(commandLine.getOptionValue("test-time")));

		config.setDataFolder(commandLine.getOptionValue("data-dir"));
		config.setOutputFile(commandLine.getOptionValue("out-file"));
		config.setDeleteTokenTopics(true);
		config.setDeleteAllInfoTopics(commandLine.hasOption("delete-all-info-topics"));

		int windows = config.getTestTimeSec() * Integer.parseInt(commandLine.getOptionValue("max-tp"));
		config.setWindows(windows);

		return config;
	}

	private static Options buildOptions() {
		Options options = new Options();

		options.addOption(Option.builder("pc").longOpt("privacy-controller").hasArg(false)
				.desc("run privacy controller macrobenchmark").build());

		options.addOption(Option.builder().longOpt("out-file").argName("FILE")
				.desc("name of output file").hasArg().required().build());

		options.addOption(Option.builder().longOpt("delete-topics")
				.desc("delete the created topics").hasArg(false).build());

		options.addOption(Option.builder().longOpt("bootstrap-server").argName("HOST:PORT")
				.desc("kafka bootstrap server host:port").hasArg().required().build());

		options.addOption(Option.builder().longOpt("partitions").argName("NUMBER")
				.desc("kafka topic partitions for token topic").hasArg().required().build());

		options.addOption(Option.builder().longOpt("universes").argName("NUMBER").desc("number of universes").hasArg()
				.required().build());

		options.addOption(Option.builder().longOpt("universe-size").argName("NUMBER")
				.desc("number of participants in a universe").hasArg().required().build());

		options.addOption(Option.builder().longOpt("universe-min-size").argName("NUMBER")
				.desc("minimum number of participants required in a universe").hasArg().required().build());

		options.addOption(Option.builder().longOpt("alpha").argName("PROB")
				.desc("probability that a member is non-malicious").hasArg().build());

		options.addOption(Option.builder().longOpt("delta").argName("PROB")
				.desc("failure error bound of erdos renyi secure aggregation").hasArg().build());

		options.addOption(Option.builder().longOpt("clients").argName("NUMBER")
				.desc("number of clients assigned to privacy controller per universe").hasArg().required().build());

		options.addOption(Option.builder().longOpt("client-dropout-prob").argName("PROB")
				.desc("probability that a client drops out in a window").hasArg().required().build());

		options.addOption(Option.builder().longOpt("client-comeback-prob").argName("PROB")
				.desc("probability that a dropped client comes back in a window").hasArg().required().build());

		options.addOption(Option.builder().longOpt("test-time").argName("SECS")
				.desc("number of seconds to run the test").hasArg().required().build());

		options.addOption(Option.builder().longOpt("max-tp").argName("NUMBER")
				.desc("max throughput estimate (used to generate test data)").hasArg().required().build());

		options.addOption(Option.builder().longOpt("data-dir").argName("PATH")
				.desc("base folder for producer info and shared keys").hasArg().required().build());

		return options;
	}

}
