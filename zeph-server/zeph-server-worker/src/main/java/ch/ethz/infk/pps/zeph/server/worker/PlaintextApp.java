package ch.ethz.infk.pps.zeph.server.worker;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes.LongSerde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.Window;
import ch.ethz.infk.pps.zeph.server.worker.config.ConfigLoader;
import ch.ethz.infk.pps.zeph.server.worker.config.WorkerConfig;
import ch.ethz.infk.pps.zeph.server.worker.util.InputSerde;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.TransformationResultOp;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerde;
import ch.ethz.infk.pps.zeph.shared.serde.WindowedUniverseIdSerde;

public class PlaintextApp {

	public static String getPlaintextTopic(long universeId) {
		return "u" + universeId + "-plaintexts";
	}

	public static String getPlaintextSumStore(long universeId) {
		return "u" + universeId + "-plaintext-sum-store";
	}

	public static String getPlaintextProducerSumTopic(long universeId) {
		return "u" + universeId + "-plaintext-psums";
	}

	public static void main(String[] args) {

		WorkerConfig config = ConfigLoader.loadConfig(args);
		String instanceId = "plaintext-worker-u" + config.getUniverseId() + "-i" + System.currentTimeMillis();
		config.setInstanceId(instanceId);
		String instanceStateDir = Paths.get(config.getStateDir(), instanceId).toString();
		config.setStateDir(instanceStateDir);

		createTopics(config);

		final KafkaStreams streams = createStreams(config);
		streams.start();

		// Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				streams.close();
				if (config.deleteTopics()) {
					deleteTopics(config);
				}
			} catch (final Exception e) {
				// ignored
			}
		}));

	}

	private static KafkaStreams createStreams(WorkerConfig config) {

		long universeId = config.getUniverseId();
		final long universeSize = config.getUniverseSize();

		if (universeSize < 0) {
			throw new IllegalArgumentException("plaintext app requires that universe size is set");
		}

		Properties streamsConfig = buildStreamConfiguration(config);

		StreamsBuilder builder = new StreamsBuilder();

		LongSerde longSerde = new LongSerde();
		SpecificAvroSimpleSerde<Input> inputSerde = new SpecificAvroSimpleSerde<>(
			Input.getClassSchema());
		SpecificAvroSimpleSerde<TransformationResult> resultSerde = new SpecificAvroSimpleSerde<>(
				TransformationResult.getClassSchema());

		WindowedUniverseIdSerde windowedUniverseIdSerde = new WindowedUniverseIdSerde();

		String dataStoreName = getPlaintextSumStore(universeId);
		String producerIntermediateSumTopic = getPlaintextProducerSumTopic(universeId);

		TimeWindows windows = TimeWindows.of(Duration.ofMillis(config.getWindowSizeMillis()))
				.grace(Duration.ofMillis(config.getGraceSizeMillis()));

		builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(dataStoreName),
				windowedUniverseIdSerde, inputSerde));

		WindowBytesStoreSupplier store = Stores.inMemoryWindowStore("u" + universeId + "-xy",
				Duration.ofHours(config.getRetentionPeriodHours()), Duration.ofMillis(config.getWindowSizeMillis()),
				false);

		builder.stream(getPlaintextTopic(universeId), Consumed.with(longSerde, inputSerde))
				.groupByKey(Grouped.with(longSerde, inputSerde))
				.windowedBy(windows)
				.reduce((a, b) -> ApplicationAdapter.add(a, b),
						Materialized.as(store))
				.suppress(Suppressed.untilWindowCloses(BufferConfig.unbounded())).toStream()
				.selectKey(
						(k, v) -> new WindowedUniverseId(universeId, new Window(k.window().start(), k.window().end())))
				.through(producerIntermediateSumTopic, Produced.with(windowedUniverseIdSerde, inputSerde))
				.flatTransform(
						new TransformerSupplier<WindowedUniverseId, Input, Iterable<KeyValue<Long, TransformationResult>>>() {

							@Override
							public Transformer<WindowedUniverseId, Input, Iterable<KeyValue<Long, TransformationResult>>> get() {
								return new Transformer<WindowedUniverseId, Input, Iterable<KeyValue<Long, TransformationResult>>>() {

									private ProcessorContext ctx;
									private KeyValueStore<WindowedUniverseId, Input> dataStore;

									@SuppressWarnings("unchecked")
									@Override
									public void init(ProcessorContext context) {
										this.ctx = context;
										this.dataStore = (KeyValueStore<WindowedUniverseId, Input>) this.ctx
												.getStateStore(dataStoreName);
									}

									@Override
									public Iterable<KeyValue<Long, TransformationResult>> transform(
											WindowedUniverseId key, Input value) {

										Input aggregator = dataStore.get(key);

										if (aggregator == null) {
											aggregator = ApplicationAdapter.empty();
										}

										value.setCount(1l);

										Input updatedAggregator = ApplicationAdapter.add(aggregator, value);

										// use the sum of squares field for a counter
										long counter = updatedAggregator.getCount();

										if (counter < universeSize) {
											dataStore.put(key, updatedAggregator);
											// System.out.println("Not Ready: " + key + "agg= " + updatedAggregator);
											return Collections.emptySet();
										} else {
											dataStore.delete(key);
											updatedAggregator.setCount(0l);
											TransformationResult result = TransformationResultOp.of(updatedAggregator, key.getWindow());
											// System.out.println("RESULT: " + result);
											return Collections.singleton(new KeyValue<>(key.getUniverseId(), result));
										}

									}

									@Override
									public void close() {

									}

								};
							}
						}, dataStoreName)
				.transform(new TransformerSupplier<Long, TransformationResult, KeyValue<Long, TransformationResult>>() {

					@Override
					public Transformer<Long, TransformationResult, KeyValue<Long, TransformationResult>> get() {
						return new Transformer<Long, TransformationResult, KeyValue<Long, TransformationResult>>() {

							private ProcessorContext ctx;

							@Override
							public void init(ProcessorContext context) {
								this.ctx = context;

							}

							@Override
							public KeyValue<Long, TransformationResult> transform(Long key,
									TransformationResult value) {
								long timestamp = System.currentTimeMillis();
								ctx.forward(key, value, To.all().withTimestamp(timestamp));
								return null;
							}

							@Override
							public void close() {

							}
						};
					}

				}).to(Names.UNIVERSE_RESULTS_TOPIC, Produced.with(longSerde, resultSerde));

		Topology topology = builder.build();
		System.out.println("TOPOLOGY: ");
		System.out.println(topology.describe());

		return new KafkaStreams(topology, streamsConfig);
	}

	private static Properties buildStreamConfiguration(WorkerConfig config) {

		Properties streamsConfig = new Properties();

		String identifier = "plaintext-transformation-u" + config.getUniverseId();
		streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, identifier);
		streamsConfig.put(StreamsConfig.CLIENT_ID_CONFIG, identifier);

		streamsConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		streamsConfig.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG,
				Math.min(config.getNumPartitions(), config.getStreamThreads()));

		// streamsConfig.put(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG, 0);
		streamsConfig.put(StreamsConfig.producerPrefix(ProducerConfig.BATCH_SIZE_CONFIG), 0);
		streamsConfig.put(StreamsConfig.producerPrefix(ProducerConfig.LINGER_MS_CONFIG), 0);
		streamsConfig.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 0);

		streamsConfig.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, LongSerde.class.getName());
		streamsConfig.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, InputSerde.class.getName());

		final File file = new File(config.getStateDir());
		streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, file.getPath());

		streamsConfig.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
				LogAndContinueExceptionHandler.class.getName());
		streamsConfig.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
				DefaultProductionExceptionHandler.class.getName());

		return streamsConfig;
	}

	private static void createTopics(WorkerConfig config) {

		Properties props = new Properties();

		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		AdminClient admin = KafkaAdminClient.create(props);

		Set<String> topics = Sets.newHashSet(getPlaintextProducerSumTopic(config.getUniverseId()),
				getPlaintextTopic(config.getUniverseId()), Names.UNIVERSE_RESULTS_TOPIC);
		try {
			Set<String> existingTopics = admin.listTopics().listings().get().stream().map(x -> x.name())
					.collect(Collectors.toSet());

			int numPartitions = config.getNumPartitions();
			int replicationFactor = config.getReplication();
			List<NewTopic> createTopics = topics.stream().filter(topic -> !existingTopics.contains(topic))
					.map(topic -> new NewTopic(topic, numPartitions, (short) replicationFactor))
					.collect(Collectors.toList());

			admin.createTopics(createTopics).all().get();
			admin.close();

		} catch (ExecutionException | InterruptedException e) {
			throw new IllegalStateException("failed to create topics", e);
		}
	}

	private static void deleteTopics(WorkerConfig config) {

		Properties props = new Properties();

		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		AdminClient admin = KafkaAdminClient.create(props);

		Set<String> topics = Sets.newHashSet(getPlaintextProducerSumTopic(config.getUniverseId()),
				getPlaintextTopic(config.getUniverseId()), Names.UNIVERSE_RESULTS_TOPIC);

		try {
			admin.deleteTopics(topics).all().get();
			admin.close();

		} catch (ExecutionException | InterruptedException e) {
			throw new IllegalStateException("failed to delete topics", e);
		}
	}

}
