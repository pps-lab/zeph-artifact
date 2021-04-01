package ch.ethz.infk.pps.zeph.server.master;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
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
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.processor.RecordContext;
import org.apache.kafka.streams.processor.StreamPartitioner;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.apache.kafka.streams.state.Stores;

import com.google.common.collect.Sets;

import ch.ethz.infk.pps.zeph.server.master.config.ConfigLoader;
import ch.ethz.infk.pps.zeph.server.master.config.MasterConfig;
import ch.ethz.infk.pps.zeph.server.master.processor.UniversePartitionUpdateProcessor.UniversePartitionUpdateProcessorSupplier;
import ch.ethz.infk.pps.zeph.server.master.util.SerdeFactory;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;

public class MasterApp {

	public static void main(String[] args) {

		MasterConfig config = ConfigLoader.getConfig(args);
		String instanceId = "master-i" + System.currentTimeMillis();
		config.setInstanceId(instanceId);
		String instanceStateDir = Paths.get(config.getStateDir(), instanceId).toString();
		config.setStateDir(instanceStateDir);

		createTopics(config);

		final SerdeFactory serde = new SerdeFactory();
		final KafkaStreams streams = createStreams(config, serde);

		streams.start();

		// Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				System.out.println("SHUTDOWN HOOK");
				streams.close();
				serde.close();

				if (config.deleteTopics()) {
					deleteTopics(config);
				}
				// restService.stop();
			} catch (final Exception e) {
				// ignored
			}
		}));
	}

	private static KafkaStreams createStreams(MasterConfig config, SerdeFactory serde) {
		Properties streamsConfig = buildStreamConfiguration(config);

		// could add deserialization error handler

		Duration timeForCommit = Duration.ofMillis(config.getTimeToCommitMillis());

		StreamsBuilder builder = new StreamsBuilder();

		// add state stores
		builder.addStateStore(
				Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(Names.UNIVERSE_TASK_STATUS_STORE),
						serde.windowedUniverseIdSerde(), serde.universePartitionStatusSerde()));

		builder.addStateStore(
				Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(Names.UNIVERSE_STATUS_STORE),
						serde.windowedUniverseIdSerde(), serde.intSerde()));

		builder.addStateStore(
				Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(Names.UNIVERSE_MEMBERSHIP_STORE),
						serde.windowedUniverseIdSerde(), serde.membershipSerde()));

		builder.addStateStore(
				Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(Names.UNIVERSE_RESULT_STORE),
						serde.windowedUniverseIdSerde(), serde.digestSerde()));

		// create input streams
		KStream<Long, UniversePartitionUpdate> updateInputStream = builder.stream(
				Names.UNIVERSE_PARTITION_UPDATES_TOPIC,
				Consumed.with(serde.longSerde(), serde.updateSerde()).withName("SOURCE.universe-partition-updates"));

		// build topology
		updateInputStream
				.process(new UniversePartitionUpdateProcessorSupplier(timeForCommit),
						Named.as("TRANSFORM.universe-partition-update"),
						Names.UNIVERSE_STATUS_STORE, Names.UNIVERSE_TASK_STATUS_STORE, Names.UNIVERSE_MEMBERSHIP_STORE,
						Names.UNIVERSE_RESULT_STORE);

		Topology topology = builder.build();

		topology.addSink("SINK.results", Names.UNIVERSE_RESULTS_TOPIC, serde.longSerde().serializer(),
				serde.resultSerde().serializer(),
				"TRANSFORM.universe-partition-update");

		topology.addSink("SINK.u-tokens", new UniverseTopicNameExtractorFromToken(), serde.longSerde().serializer(),
				serde.tokenSerde().serializer(), new DirectPartitioner<Token>(), "TRANSFORM.universe-partition-update");

		topology.addSink("SINK.u-info", new UniverseTopicNameExtractor(), serde.windowedUniverseIdSerde().serializer(),
				serde.universeWindowStateSerde().serializer(), "TRANSFORM.universe-partition-update");

		System.out.println("TOPOLOGY: ");
		System.out.println(topology.describe());

		return new KafkaStreams(topology, streamsConfig);
	}

	private static class UniverseTopicNameExtractor
			implements TopicNameExtractor<WindowedUniverseId, DeltaUniverseState> {

		@Override
		public String extract(WindowedUniverseId key, DeltaUniverseState value, RecordContext recordContext) {
			String topic = Names.getInfosTopic(key.getUniverseId());
			return topic;
		}
	}

	private static class UniverseTopicNameExtractorFromToken implements TopicNameExtractor<Long, Token> {
		@Override
		public String extract(Long key, Token token, RecordContext ctx) {

			long universeId = Long.parseLong(token.getMac());
			String topic = Names.getTokensTopic(universeId);
			return topic;
		}
	}

	private static class DirectPartitioner<V> implements StreamPartitioner<Long, V> {

		@Override
		public Integer partition(String topic, Long key, V value, int numPartitions) {
			return (int) (key % numPartitions);
		}

	}

	private static Properties buildStreamConfiguration(MasterConfig config) {

		Properties streamsConfig = new Properties();

		String identifier = "zeph-server";
		streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, identifier);
		streamsConfig.put(StreamsConfig.CLIENT_ID_CONFIG, identifier);

		streamsConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		streamsConfig.put(StreamsConfig.BUFFERED_RECORDS_PER_PARTITION_CONFIG, 0);
		streamsConfig.put(StreamsConfig.producerPrefix(ProducerConfig.BATCH_SIZE_CONFIG), 0);
		streamsConfig.put(StreamsConfig.producerPrefix(ProducerConfig.LINGER_MS_CONFIG), 0);
		streamsConfig.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 0);

		final File file = new File(config.getStateDir());
		streamsConfig.put(StreamsConfig.STATE_DIR_CONFIG, file.getPath());

		streamsConfig.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
				LogAndContinueExceptionHandler.class.getName());
		streamsConfig.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG,
				DefaultProductionExceptionHandler.class.getName());

		return streamsConfig;
	}

	private static void createTopics(MasterConfig config) {

		Properties props = new Properties();

		String bootstrapServers = config.getKafkaBootstrapServers();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

		AdminClient admin = KafkaAdminClient.create(props);

		Set<String> topics = Sets.newHashSet(Names.UNIVERSE_PARTITION_UPDATES_TOPIC, Names.UNIVERSE_RESULTS_TOPIC);
		try {
			Set<String> existingTopics = admin.listTopics().listings().get()
					.stream().map(x -> x.name()).collect(Collectors.toSet());

			int numPartitions = config.getNumPartitions();
			int replicationFactor = config.getReplication();
			List<NewTopic> createTopics = topics.stream()
					.filter(topic -> !existingTopics.contains(topic))
					.map(topic -> new NewTopic(topic, numPartitions, (short) replicationFactor))
					.collect(Collectors.toList());

			admin.createTopics(createTopics).all().get();
			admin.close();

		} catch (ExecutionException | InterruptedException e) {
			throw new IllegalStateException("failed to create topics", e);
		}
	}

	private static void deleteTopics(MasterConfig config) {

		Properties props = new Properties();

		String bootstrapServers = config.getKafkaBootstrapServers();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

		AdminClient admin = KafkaAdminClient.create(props);

		Set<String> topics = Sets.newHashSet(Names.UNIVERSE_PARTITION_UPDATES_TOPIC, Names.UNIVERSE_RESULTS_TOPIC);
		try {
			admin.deleteTopics(topics).all().get();
			admin.close();
		} catch (ExecutionException | InterruptedException e) {
			throw new IllegalStateException("failed to delete topics", e);
		}
	}

}
