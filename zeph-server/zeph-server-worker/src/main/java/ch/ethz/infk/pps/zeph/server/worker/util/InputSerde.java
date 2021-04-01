package ch.ethz.infk.pps.zeph.server.worker.util;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerde;
import ch.ethz.infk.pps.shared.avro.Input;

public class InputSerde implements Serde<Input> {

	private SpecificAvroSimpleSerde<Input> inputSerde = new SpecificAvroSimpleSerde<>(Input.getClassSchema());

	@Override
	public Serializer<Input> serializer() {
		return inputSerde.serializer();
	}

	@Override
	public Deserializer<Input> deserializer() {
		return inputSerde.deserializer();
	}

}
