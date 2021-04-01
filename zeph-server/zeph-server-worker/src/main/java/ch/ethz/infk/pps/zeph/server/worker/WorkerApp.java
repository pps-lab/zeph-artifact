package ch.ethz.infk.pps.zeph.server.worker;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes.LongSerde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KafkaStreams.State;
import org.apache.kafka.streams.KafkaStreams.StateListener;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.collect.Sets;

import ch.ethz.infk.pps.zeph.server.worker.config.ConfigLoader;
import ch.ethz.infk.pps.zeph.server.worker.config.WorkerConfig;
import ch.ethz.infk.pps.zeph.server.worker.processor.CiphertextSumTransformer.CiphertextSumTransformerSupplier;
import ch.ethz.infk.pps.zeph.server.worker.processor.CiphertextTransformer.ValueTransformerSupplier;
import ch.ethz.infk.pps.zeph.server.worker.processor.TokenTransformer.TokenTransformerSupplier;
import ch.ethz.infk.pps.zeph.server.worker.util.DigestSerde;
import ch.ethz.infk.pps.zeph.server.worker.util.SerdeFactory;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Token;

public class WorkerApp {

	private static final Logger LOG = LogManager.getLogger();
	public static Marker GLOBAL_MARKER;

	public static void main(String[] args) {

		if (args[0].equals("plaintext")) {
			String[] plaintextArgs = Arrays.copyOfRange(args, 1, args.length);
			PlaintextApp.main(plaintextArgs);
			return;
		}

		WorkerConfig config = ConfigLoader.loadConfig(args);
		String instanceId = "worker-u" + config.getUniverseId() + "-i" + System.currentTimeMillis();
		config.setInstanceId(instanceId);
		String instanceStateDir = Paths.get(config.getStateDir(), instanceId).toString();
		config.setStateDir(instanceStateDir);

		GLOBAL_MARKER = MarkerManager.getMarker("global-" + config.getInstanceId());

		createTopics(config);

		final SerdeFactory serde = new SerdeFactory();
		final KafkaStreams streams = createStreams(config, serde);

		streams.setStateListener(new StateListener() {
			@Override
			public void onChange(State newState, State oldState) {
				LOG.info("kafka streams state change: {} -> {}", oldState, newState);
			}
		});

		streams.start();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				LOG.info("Caught Shutdown Hook -> Closing Kafka Streams");
				streams.close();
				serde.close();
				if (config.deleteTopics()) {
					deleteTopics(config);
				}
			} catch (final Exception e) {
				LOG.error("Failed to Close Kafka Streams", e);
			}
		}));

	}

	private static KafkaStreams createStreams(WorkerConfig config, SerdeFactory serde) {

		Properties streamsConfig = buildStreamConfiguration(config);

		Names n = new Names(config.getUniverseId());

		Duration windowSize = Duration.ofMillis(config.getWindowSizeMillis());
		Duration grace = Duration.ofMillis(config.getGraceSizeMillis());
		Duration retentionPeriod = Duration.ofHours(config.getRetentionPeriodHours());

		TimeWindows windows = TimeWindows.of(windowSize).grace(grace);

		StreamsBuilder builder = new StreamsBuilder();

		// add state stores

		WindowBytesStoreSupplier ciphertextSumStore = Stores.inMemoryWindowStore(n.CIPHERTEXT_SUM_STORE,
				retentionPeriod, windowSize, false);

		builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(n.COMMITTING_SUM_STORE),
				serde.longSerde(), serde.digestSerde()));

		builder.addStateStore(Stores.windowStoreBuilder(
				Stores.inMemoryWindowStore(n.COMMIT_BUFFER_STORE, retentionPeriod, windowSize, false),
				serde.longSerde(), serde.intSerde()));

		builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(n.TRANSFORMING_SUM_STORE),
				serde.longSerde(), serde.digestSerde()));

		builder.addStateStore(Stores.windowStoreBuilder(
				Stores.inMemoryWindowStore(n.EXPECTED_TRANSFORMATION_TOKEN_STORE, retentionPeriod, windowSize, false),
				serde.longSerde(), serde.intSerde()));

		builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(n.MEMBER_STORE),
				serde.longSerde(), serde.longSerde()));

		builder.addStateStore(Stores.windowStoreBuilder(
				Stores.inMemoryWindowStore(n.MEMBER_DELTA_STORE, retentionPeriod, windowSize, false), serde.longSerde(),
				serde.intSerde()));

		// create input streams
		KStream<Long, Digest> ciphertextInputStream = builder.stream(n.CIPHERTEXTS_TOPIC,
				Consumed.with(serde.longSerde(), serde.digestSerde()).withName("SOURCE.ciphertexts"));

		KStream<Long, Token> tokenInputStream = builder.stream(n.TOKEN_TOPIC,
				Consumed.with(serde.longSerde(), serde.tokenSerde()).withName("SOURCE.tokens"));

		// build topology
		ciphertextInputStream.transformValues(new ValueTransformerSupplier(), Named.as("TRANSFORM.heac-header"))
				.groupByKey().windowedBy(windows)
				.reduce((aggValue, newValue) -> DigestOp.add(aggValue, newValue), Named.as("REDUCE.ciphertexts"),
						Materialized.as(ciphertextSumStore))
				.toStream(Named.as("toStream"))
				.flatTransform(new CiphertextSumTransformerSupplier(windows, n), Named.as("TRANSFORM.ciphertext-sums"),
						n.CIPHERTEXT_SUM_STORE, n.COMMIT_BUFFER_STORE, n.COMMITTING_SUM_STORE, n.TRANSFORMING_SUM_STORE,
						n.EXPECTED_TRANSFORMATION_TOKEN_STORE, n.MEMBER_STORE, n.MEMBER_DELTA_STORE)
				.to(Names.UNIVERSE_PARTITION_UPDATES_TOPIC, Produced.with(serde.longSerde(), serde.updateSerde()));

		tokenInputStream
				.flatTransform(new TokenTransformerSupplier(n), Named.as("TRANSFORM.tokens"), n.CIPHERTEXT_SUM_STORE,
						n.COMMIT_BUFFER_STORE, n.COMMITTING_SUM_STORE, n.TRANSFORMING_SUM_STORE,
						n.EXPECTED_TRANSFORMATION_TOKEN_STORE, n.MEMBER_STORE, n.MEMBER_DELTA_STORE)
				.to(Names.UNIVERSE_PARTITION_UPDATES_TOPIC, Produced.with(serde.longSerde(), serde.updateSerde()));

		Topology topology = builder.build();

		System.out.println("TOPOLOGY: ");
		System.out.println(topology.describe());

		return new KafkaStreams(topology, streamsConfig);

	}

	private static Properties buildStreamConfiguration(WorkerConfig config) {

		long universeId = config.getUniverseId();

		Properties streamsConfig = new Properties();

		String identifier = "zeph-transformation-u" + universeId;
		streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, identifier);
		streamsConfig.put(StreamsConfig.CLIENT_ID_CONFIG, identifier);

		streamsConfig.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG,
				Math.min(config.getNumPartitions(), config.getStreamThreads()));

		streamsConfig.put(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG, 0);
		streamsConfig.put(StreamsConfig.producerPrefix(ProducerConfig.BATCH_SIZE_CONFIG), 0);
		streamsConfig.put(StreamsConfig.producerPrefix(ProducerConfig.LINGER_MS_CONFIG), 0);
		streamsConfig.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 0);

		streamsConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		streamsConfig.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, LongSerde.class.getName());
		streamsConfig.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, DigestSerde.class.getName());

		final File file = new File(config.getStateDir());
		streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, file.getPath());

		streamsConfig.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
				LogAndContinueExceptionHandler.class.getName());
		streamsConfig.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
				DefaultProductionExceptionHandler.class.getName());

		return streamsConfig;
	}

	private static void createTopics(WorkerConfig config) {

		Names names = new Names(config.getUniverseId());

		Properties props = new Properties();

		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		AdminClient admin = KafkaAdminClient.create(props);

		Set<String> topics = Sets.newHashSet(names.CIPHERTEXTS_TOPIC, names.TOKEN_TOPIC);
		try {
			Set<String> existingTopics = admin.listTopics().listings().get().stream().map(x -> x.name())
					.collect(Collectors.toSet());

			int numPartitions = config.getNumPartitions();
			int replicationFactor = config.getReplication();
			List<NewTopic> createTopics = topics.stream().filter(topic -> !existingTopics.contains(topic))
					.map(topic -> new NewTopic(topic, numPartitions, (short) replicationFactor))
					.collect(Collectors.toList());

			if (!existingTopics.contains(names.INFO_TOPIC)) {
				NewTopic infoTopic = new NewTopic(names.INFO_TOPIC, 1, (short) replicationFactor);
				createTopics.add(infoTopic);
			}

			admin.createTopics(createTopics).all().get();

		} catch (ExecutionException e) {
			LOG.warn(GLOBAL_MARKER, "execution exception while creating topics: ", e);

		} catch (InterruptedException e) {
			throw new IllegalStateException("failed to create topics", e);
		} finally {
			admin.close();
		}
	}

	private static void deleteTopics(WorkerConfig config) {

		Properties props = new Properties();

		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		AdminClient admin = KafkaAdminClient.create(props);

		Set<String> topics = Sets.newHashSet(Names.getInfosTopic(config.getUniverseId()),
				Names.getTokensTopic(config.getUniverseId()));

		try {
			admin.deleteTopics(topics).all().get();
			admin.close();

		} catch (ExecutionException | InterruptedException e) {
			throw new IllegalStateException("failed to delete topics", e);
		}
	}

}
