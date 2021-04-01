package ch.ethz.infk.pps.zeph.server.worker.util;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerde;
import ch.ethz.infk.pps.shared.avro.Digest;

public class DigestSerde implements Serde<Digest> {

	private SpecificAvroSimpleSerde<Digest> digestSerde = new SpecificAvroSimpleSerde<>(Digest.getClassSchema());

	@Override
	public Serializer<Digest> serializer() {
		return digestSerde.serializer();
	}

	@Override
	public Deserializer<Digest> deserializer() {
		return digestSerde.deserializer();
	}

}
