package ch.ethz.infk.pps.zeph.server.worker.util;

import java.io.Closeable;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerde;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;

public class SerdeFactory implements Closeable {

	private Serde<Long> longSerde = Serdes.Long();
	private DigestSerde digestSerde = new DigestSerde();
	private Serde<Integer> intSerde = Serdes.Integer();
	private SpecificAvroSimpleSerde<Token> tokenSerde = new SpecificAvroSimpleSerde<>(
			Token.getClassSchema());
	private SpecificAvroSimpleSerde<UniversePartitionUpdate> updateSerde = new SpecificAvroSimpleSerde<>(
			UniversePartitionUpdate.getClassSchema());

	public Serde<Long> longSerde() {
		return longSerde;
	}

	public DigestSerde digestSerde() {
		return digestSerde;
	}

	public Serde<Integer> intSerde() {
		return intSerde;
	}

	public SpecificAvroSimpleSerde<Token> tokenSerde() {
		return tokenSerde;
	}

	public SpecificAvroSimpleSerde<UniversePartitionUpdate> updateSerde() {
		return updateSerde;
	}

	@Override
	public void close() {
		longSerde.close();
		digestSerde.close();
		intSerde.close();
		tokenSerde.close();
		updateSerde.close();
	}

}
