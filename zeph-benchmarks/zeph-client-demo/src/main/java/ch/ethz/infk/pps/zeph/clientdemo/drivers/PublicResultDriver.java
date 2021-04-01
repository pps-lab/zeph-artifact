package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.Window;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleDeserializer;

public class PublicResultDriver extends Driver {

	private final static Logger LOG = LogManager.getLogger();
	private final static Marker M = MarkerManager.getMarker("benchmark-results");

	private String kafkaBootstrapServers;

	public PublicResultDriver(String kafkaBootstrapServers) {
		this.kafkaBootstrapServers = kafkaBootstrapServers;
	}

	@Override
	protected void drive() {

		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
		props.put(ConsumerConfig.CLIENT_ID_CONFIG, "PUBLICRESULTDRIVER");
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "PUBLICRESULTDRIVERGROUP");

		final KafkaConsumer<Long, TransformationResult> consumer = new KafkaConsumer<>(props,
				new LongDeserializer(),
				new SpecificAvroSimpleDeserializer<>(TransformationResult.getClassSchema()));

		consumer.subscribe(Collections.singletonList(Names.UNIVERSE_RESULTS_TOPIC));

		Duration duration = Duration.ofMinutes(1);
		try {

			while (true) {

				final ConsumerRecords<Long, TransformationResult> records = consumer.poll(duration);

				if (isShutdownRequested()) {
					break;
				}

				records.forEach(record -> {
					Long universeId = record.key();
					TransformationResult transformationResult = record.value();
					Window window = transformationResult.getWindow();
					Long timestamp = record.timestamp();
					LOG.info(M, "PUBLIC RESULT: w={} u={} result=[count={} sum={}]  t={} delay={}", window, universeId, transformationResult.getCount(), transformationResult.getSum(),
							timestamp, Duration.ofMillis(System.currentTimeMillis() - window.getEnd()));
				});

			}

		} finally {
			consumer.close();
		}
	}

}
