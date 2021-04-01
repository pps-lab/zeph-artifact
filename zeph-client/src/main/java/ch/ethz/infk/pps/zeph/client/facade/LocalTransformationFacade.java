package ch.ethz.infk.pps.zeph.client.facade;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.LongSerializer;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerializer;
import ch.ethz.infk.pps.shared.avro.Digest;

public class LocalTransformationFacade implements ILocalTransformationFacade {

	private long producerId;
	private String topic;
	private KafkaProducer<Long, Digest> ciphertextProducer;
	private String kafkaBootstrapServers;

	public LocalTransformationFacade(String kafkaBootstrapServers) {
		this.kafkaBootstrapServers = kafkaBootstrapServers;
	}

	public LocalTransformationFacade(KafkaProducer<Long, Digest> ciphertextProducer) {
		this.ciphertextProducer = ciphertextProducer;
	}

	@Override
	public void init(long producerId, long universeId) {
		this.producerId = producerId;
		Names n = new Names(universeId);
		this.topic = n.CIPHERTEXTS_TOPIC;

		if (this.ciphertextProducer == null) {
			this.ciphertextProducer = buildCiphertextProducer(this.kafkaBootstrapServers, producerId);
		}

	}

	@Override
	public CompletableFuture<RecordMetadata> sendCiphertext(long timestamp, Digest ciphertext, long heacStart) {
		byte[] bytes = Longs.toByteArray(heacStart);
		Iterable<Header> headers = Collections.singleton(new RecordHeader("heac", bytes));
		ProducerRecord<Long, Digest> record = new ProducerRecord<>(topic, null, timestamp, producerId, ciphertext,
				headers);
		return produceKafkaRecord(ciphertextProducer, record);
	}

	public static KafkaProducer<Long, Digest> buildCiphertextProducer(String kafkaBootstrapServers, long id) {
		String clientId = String.format("producer%d-ciphertext", id);

		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
		props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
		// props.put(ProducerConfig.BATCH_SIZE_CONFIG, 0);
		LongSerializer keySerializer = new LongSerializer();
		SpecificAvroSimpleSerializer<Digest> valueSerializer = new SpecificAvroSimpleSerializer<>(
				Digest.getClassSchema());
		return new KafkaProducer<Long, Digest>(props, keySerializer, valueSerializer);
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
		ciphertextProducer.close();
	}

}
