package ch.ethz.infk.pps.zeph.benchmark.macro.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfig;
import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfig.BenchmarkType;
import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfigParser;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleDeserializer;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.Window;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

public class E2EBenchmarkRunner {

	private static Logger LOG = LogManager.getLogger();

	public static void main(String[] args) {

		E2EConfig config = null;

		Options options = E2EConfigParser.buildOptions();
		try {
			LOG.info("parsing arguments...");
			CommandLineParser parser = new DefaultParser();
			CommandLine commandLine = parser.parse(options, args, true);
			config = E2EConfigParser.parseConfig(commandLine);

		} catch (ParseException e) {
			LOG.error("failed to parse arguments", e);
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("benchmark", options);
			System.exit(0);
		}

		E2EBenchmark benchmark = new E2EBenchmark(config);
		LOG.info("experiment={} config={}", config.getExperimentId(), config);

		LOG.info("setting up benchmark...");
		benchmark.setup();

		// wait until all producer partitions are ready
		LOG.info("syncing producer partitions...");
		long timestampSync = syncProducerPartitions(config);
		config.setTimestampSync(timestampSync);

		try {
			LOG.info("starting privacy controllers and producer drivers...");
			benchmark.start();

			TimeUnit.SECONDS.sleep(config.getTestTimeSec());

			LOG.info("stopping privacy controllers and producer drivers...");
			boolean hasTerminated = benchmark.stop();
			if (!hasTerminated) {
				LOG.warn("executor service did not terminate properly");
				if (!config.isLeader()) {
					System.exit(0);
				}

			}
		} catch (InterruptedException e) {

		}

		if (config.isLeader()) {
			LOG.info("calculating performance metrics...");
			calculateMetrics(config);
			deleteSyncTopic(config);
		}

	}

	private static void calculateMetrics(E2EConfig config) {
		Set<Long> universes = new HashSet<>(config.getUniverseIds());

		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
		String uuid = UUID.randomUUID().toString();
		props.put(ConsumerConfig.CLIENT_ID_CONFIG, uuid);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, uuid);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		final KafkaConsumer<Long, TransformationResult> resultConsumer = new KafkaConsumer<>(props,
				new LongDeserializer(),
				new SpecificAvroSimpleDeserializer<>(TransformationResult.getClassSchema()));

		resultConsumer.subscribe(Collections.singletonList(Names.UNIVERSE_RESULTS_TOPIC));

		Table<Long, Window, JSONObject> resultTable = HashBasedTable.create();

		Duration duration = Duration.ofSeconds(5);
		try {

			while (true) {
				final ConsumerRecords<Long, TransformationResult> records = resultConsumer.poll(duration);

				if (records.isEmpty()) {
					break;
				}

				records.forEach(record -> {

					Long universeId = record.key();

					if (universes.contains(universeId)) {
						TransformationResult transformationResult = record.value();
						Window window = transformationResult.getWindow();
						

						Long timestamp = record.timestamp();
						long latency = timestamp - window.getEnd();

						JSONObject jsonObj = new JSONObject();
						jsonObj.put("uid", universeId);
						jsonObj.put("window", window.getEnd());
						jsonObj.put("latency", latency);

						JSONObject res = new JSONObject();
						res.put("sum", transformationResult.getSum());
						res.put("count", transformationResult.getCount());
						jsonObj.put("result", res);

						resultTable.put(universeId, window, jsonObj);
						LOG.info("uid={} window={} latency={} sum={}  count={}", universeId, WindowUtil.f(window), latency,
								transformationResult.getSum(), transformationResult.getCount());
					}
				});

			}

		} finally {
			resultConsumer.close();
		}

		JSONObject result = new JSONObject();

		JSONObject configObject = new JSONObject(config);
		configObject.remove("kafkaBootstrapServers");
		result.put("config", configObject);

		System.out.println("uid\twindow-end\tlatency\tcount");
		JSONArray jsonArr = new JSONArray();
		resultTable.cellSet().forEach(cell -> {
			System.out.println(
					cell.getRowKey() + "\t" + cell.getColumnKey().getEnd() + "\t" + cell.getValue().getLong("latency") +
							"\t" + cell.getValue().getJSONObject("result").getLong("count"));
			jsonArr.put(cell.getValue());
		});
		result.put("data", jsonArr);

		try {
			Files.write(Paths.get(config.getOutputFile()), result.toString(2).getBytes());
		} catch (JSONException | IOException e) {
			System.out.println(result.toString(2));
			throw new IllegalStateException("failed to write json output file", e);
		}

	}

	private static void deleteSyncTopic(E2EConfig config) {
		Properties props = new Properties();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
		AdminClient admin = KafkaAdminClient.create(props);
		admin.deleteTopics(Collections.singleton(config.getExperimentId()));
		LOG.info("deleted topic");
		admin.close();
	}

	private static long syncProducerPartitions(E2EConfig config) {

		Properties props = new Properties();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
		AdminClient admin = KafkaAdminClient.create(props);

		RetryPolicy<Boolean> policy = new RetryPolicy<Boolean>().handle(ExecutionException.class)
				.withDelay(Duration.ofSeconds(1)).withMaxRetries(500)
				.onFailure(e -> LOG.info("topic not ready")).onRetriesExceeded(e -> {
					throw new IllegalStateException("retries exceeded: failed to wait for leader to create topic");
				});

		if (config.isLeader()) {
			// the leader creates the topic
			CreateTopicsResult res = admin
					.createTopics(Collections.singleton(new NewTopic(config.getExperimentId(), 1, (short) 1)));
			try {
				res.all().get();
			} catch (InterruptedException | ExecutionException e1) {
				throw new IllegalStateException("leader failed to create topic", e1);
			}
			LOG.info("Leader: topic is created");
		} else {
			// all others wait until the topic is created
			Failsafe.with(policy).get(() -> {
				DescribeTopicsResult res = admin.describeTopics(Collections.singleton(config.getExperimentId()));
				res.all().get();
				return true;
			});

			LOG.info("NonLeader: topic is created");

		}
		admin.close();

		Properties producerConfig = new Properties();
		producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
		producerConfig.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());
		producerConfig.put(ProducerConfig.BATCH_SIZE_CONFIG, 0);

		LongSerializer longSer = new LongSerializer();
		StringSerializer stringSer = new StringSerializer();
		KafkaProducer<String, Long> producer = new KafkaProducer<>(producerConfig, stringSer, longSer);
		try {

			if (config.isE2eProducer()) {
				Future<RecordMetadata> future = producer
						.send(new ProducerRecord<>(config.getExperimentId(), "p_" + config.getProducerPartition(),
								System.currentTimeMillis() + 5000));
				future.get();
			}

			if (config.isE2eController()) {
				Future<RecordMetadata> future = producer
						.send(new ProducerRecord<>(config.getExperimentId(), "c_" + config.getControllerPartition(),
								System.currentTimeMillis() + 5000));
				future.get();
			}

			LOG.info("sent heartbeat");
		} catch (InterruptedException | ExecutionException e1) {
			throw new IllegalStateException("failed to send heartbeat", e1);
		} finally {
			producer.close();
		}

		Properties consumerConfig = new Properties();

		String uuid = UUID.randomUUID().toString();
		consumerConfig.put(ConsumerConfig.CLIENT_ID_CONFIG, uuid);
		consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, uuid);
		consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());

		LongDeserializer longDe = new LongDeserializer();
		StringDeserializer stringDe = new StringDeserializer();
		KafkaConsumer<String, Long> consumer = new KafkaConsumer<>(consumerConfig, stringDe, longDe);

		consumer.subscribe(Collections.singleton(config.getExperimentId()));

		Set<String> expectedPartitions = IntStream.range(0, config.getProducerPartitionCount()).boxed()
				.map(x -> "p_" + x).collect(Collectors.toSet());

		if (config.getBenchmarkType() == BenchmarkType.zeph) {
			expectedPartitions.addAll(
					IntStream.range(0, config.getControllerPartitionCount()).boxed()
							.map(x -> "c_" + x).collect(Collectors.toSet()));
		}

		LOG.info("expected partitions: {}", expectedPartitions);
		int count = 0;

		long maxTimestamp = -1;
		try {
			while (true) {
				ConsumerRecords<String, Long> records = consumer.poll(Duration.ofSeconds(6));

				Iterator<ConsumerRecord<String, Long>> iter = records.iterator();
				while (iter.hasNext()) {
					ConsumerRecord<String, Long> record = iter.next();
					LOG.info("get record: {}", record);
					expectedPartitions.remove(record.key());
					maxTimestamp = Math.max(maxTimestamp, record.value());
				}

				if (records.isEmpty()) {
					count++;
				} else {
					count = 0;
				}

				if (expectedPartitions.isEmpty()) {
					// ready to run experiment
					break;
				}

				if (count > 20) {
					throw new IllegalStateException(
							"timeout: sync clients failed to wait! expectedPartitions=" + expectedPartitions);
				}
			}
		} finally {
			consumer.close();
		}

		LOG.info("done (have all expected)");

		return maxTimestamp;

	}

}
