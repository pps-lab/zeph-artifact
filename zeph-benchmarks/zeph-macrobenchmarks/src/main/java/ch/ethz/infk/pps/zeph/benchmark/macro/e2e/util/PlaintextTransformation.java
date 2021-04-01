package ch.ethz.infk.pps.zeph.benchmark.macro.e2e.util;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.zeph.client.IProducer;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerializer;
import ch.ethz.infk.pps.shared.avro.Input;

public class PlaintextTransformation implements IProducer {

	private static final Logger LOG = LogManager.getLogger();
	private final Marker m;

	private final long producerId;
	private String kafkaBootstrapServers;
	private final String topic;

	private KafkaProducer<Long, Input> plaintextProducer;

	private long prevTimestamp = -1;

	public PlaintextTransformation(long producerId, long universeId, String kafkaBootstrapServers) {
		this.producerId = producerId;
		this.kafkaBootstrapServers = kafkaBootstrapServers;
		this.topic = "u" + universeId + "-plaintexts";

		this.m = MarkerManager.getMarker("m");
	}

	public PlaintextTransformation(long producerId, long universeId, KafkaProducer<Long, Input> plaintextProducer) {
		this.producerId = producerId;
		this.topic = "u" + universeId + "-plaintexts";
		this.m = MarkerManager.getMarker("m");
		this.plaintextProducer = plaintextProducer;
	}

	@Override
	public void init() {
		if (this.plaintextProducer == null) {
			this.plaintextProducer = buildPlaintextProducer(kafkaBootstrapServers, producerId);
		}
	}

	@Override
	public void submit(Input value, long timestamp) {
		if (timestamp <= prevTimestamp) {
			LOG.error(m, "OoO: prev={} cur={}", prevTimestamp, timestamp);
			throw new IllegalArgumentException(
					"cannot submit OoO-records or multiple records with the same timestamp prev=" + prevTimestamp
							+ "  timestamp=" + timestamp);
		}
		prevTimestamp = timestamp;

		ProducerRecord<Long, Input> record = new ProducerRecord<>(topic, null, timestamp, this.producerId, value);
		this.plaintextProducer.send(record);
	}

	@Override
	public void submitHeartbeat(long timestamp) {
		// do nothing

	}

	public void close() {
		this.plaintextProducer.close();
	}

	public static KafkaProducer<Long, Input> buildPlaintextProducer(String kafkaBootstrapServers, long id) {
		String clientId = String.format("producer%d-plaintext", id);
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
		props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
		// props.put(ProducerConfig.BATCH_SIZE_CONFIG, 0);
		LongSerializer longSerializer = new LongSerializer();
		SpecificAvroSimpleSerializer<Input> inputSerializer = new SpecificAvroSimpleSerializer<>(
				Input.getClassSchema());
		return new KafkaProducer<>(props, longSerializer, inputSerializer);
	}

}
