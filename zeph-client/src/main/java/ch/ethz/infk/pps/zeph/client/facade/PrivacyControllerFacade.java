package ch.ethz.infk.pps.zeph.client.facade;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongSerializer;

import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleDeserializer;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerializer;
import ch.ethz.infk.pps.zeph.shared.serde.WindowedUniverseIdSerde.WindowedUniverseIdDeserializer;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.Window;

public class PrivacyControllerFacade implements IPrivacyControllerFacade {

	private KafkaProducer<Long, Token> tokenProducer;
	private KafkaConsumer<WindowedUniverseId, DeltaUniverseState> infoConsumer;
	private final Duration pollTimeout;

	private Map<Long, String> tokenTopics;

	private long transformationId;
	private String kafkaBootstrapServers;

	public PrivacyControllerFacade(long transformationId, String kafkaBootstrapServers, Duration pollTimeout) {
		this.pollTimeout = pollTimeout;
		this.transformationId = transformationId;
		this.kafkaBootstrapServers = kafkaBootstrapServers;
	}

	public PrivacyControllerFacade(long transformationId, String kafkaBootstrapServers, Duration pollTimeout,
			KafkaProducer<Long, Token> tokenProducer) {
		this.pollTimeout = pollTimeout;
		this.transformationId = transformationId;
		this.kafkaBootstrapServers = kafkaBootstrapServers;
		this.tokenProducer = tokenProducer;
	}

	@Override
	public void init(Set<Long> universeIds) {

		if (this.tokenProducer == null) {
			String producerClientId = "producer-transformation-" + transformationId;
			this.tokenProducer = buildTokenProducer(producerClientId, kafkaBootstrapServers, 0);
		}
		if (this.infoConsumer == null) {
			String consumerClientId = "consumer-transformation-" + transformationId;
			this.infoConsumer = buildInfoConsumer(consumerClientId, kafkaBootstrapServers);
		}

		List<String> infoTopics = new ArrayList<>();
		tokenTopics = new HashMap<>();

		universeIds.forEach(universeId -> {
			tokenTopics.put(universeId, Names.getTokensTopic(universeId));
			infoTopics.add(Names.getInfosTopic(universeId));
		});

		this.infoConsumer.unsubscribe();
		this.infoConsumer.subscribe(infoTopics);
	}

	@Override
	public CompletableFuture<RecordMetadata> sendCommit(long producerId, Window window, long universeId) {
		HeacHeader commitInfo = new HeacHeader(window.getStart() - 1, window.getEnd() - 1);
		Token commitToken = new Token(window, "a", null, commitInfo, null);

		ProducerRecord<Long, Token> record = new ProducerRecord<Long, Token>(tokenTopics.get(universeId), producerId,
				commitToken);
		return produceKafkaRecord(tokenProducer, record);
	}

	@Override
	public CompletableFuture<RecordMetadata> sendTransformationToken(long producerId, Window window,
			Digest transformationToken, long universeId) {

		Token token = new Token(window, "b", transformationToken, null, null);
		ProducerRecord<Long, Token> record = new ProducerRecord<Long, Token>(tokenTopics.get(universeId), producerId,
				token);
		return produceKafkaRecord(tokenProducer, record);
	}

	@Override
	public ConsumerRecords<WindowedUniverseId, DeltaUniverseState> pollInfos() {
		ConsumerRecords<WindowedUniverseId, DeltaUniverseState> records = this.infoConsumer.poll(pollTimeout);
		return records;
	}

	public static KafkaProducer<Long, Token> buildTokenProducer(String clientId, String kafkaBootstrapServers,
			int producerBatchSize) {
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
		props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);

		// batch tokens together for better throughput (however im too long, with not
		// enough participants -> lot higher latency)
		// props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768); // always send tokens
		// props.put(ProducerConfig.LINGER_MS_CONFIG, 10);

		// send tokens immediately
		props.put(ProducerConfig.BATCH_SIZE_CONFIG, producerBatchSize);

		LongSerializer keySerializer = new LongSerializer();
		SpecificAvroSimpleSerializer<Token> valueSerializer = new SpecificAvroSimpleSerializer<>(
				Token.getClassSchema());
		return new KafkaProducer<>(props, keySerializer, valueSerializer);
	}

	private KafkaConsumer<WindowedUniverseId, DeltaUniverseState> buildInfoConsumer(String clientId,
			String kafkaBootstrapServers) {

		Properties props = new Properties();
		props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, clientId);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 16);

		WindowedUniverseIdDeserializer keyDeserializer = new WindowedUniverseIdDeserializer();
		SpecificAvroSimpleDeserializer<DeltaUniverseState> valueDeserializer = new SpecificAvroSimpleDeserializer<>(
				DeltaUniverseState.getClassSchema());
		return new KafkaConsumer<>(props, keyDeserializer, valueDeserializer);
	}

	private <K, V> CompletableFuture<RecordMetadata> produceKafkaRecord(KafkaProducer<K, V> producer,
			ProducerRecord<K, V> record) {
		CompletableFuture<RecordMetadata> cf = new CompletableFuture<>();
		producer.send(record, new Callback() {
			@Override
			public void onCompletion(RecordMetadata metadata, Exception exception) {
				if (exception != null) {
					cf.completeExceptionally(exception);
				} else {
					cf.complete(metadata);
				}
			}
		});
		return cf;
	}

	@Override
	public void close() {
		tokenProducer.close(Duration.ofMillis(1000));
		infoConsumer.close(Duration.ofMillis(1000));
	}

}
