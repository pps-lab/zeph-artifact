package ch.ethz.infk.pps.zeph.shared.serde;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class SpecificAvroSimpleSerde<T extends SpecificRecordBase> implements Serde<T> {

	private SpecificAvroSimpleSerializer<T> serializer;
	private SpecificAvroSimpleDeserializer<T> deserializer;

	public SpecificAvroSimpleSerde(Schema schema) {
		serializer = new SpecificAvroSimpleSerializer<>(schema);
		deserializer = new SpecificAvroSimpleDeserializer<>(schema);
	}

	@Override
	public Serializer<T> serializer() {
		return serializer;
	}

	@Override
	public Deserializer<T> deserializer() {
		return deserializer;
	}

	@Override
	public void close() {
		Serde.super.close();
		serializer.close();
		deserializer.close();
	}

}
