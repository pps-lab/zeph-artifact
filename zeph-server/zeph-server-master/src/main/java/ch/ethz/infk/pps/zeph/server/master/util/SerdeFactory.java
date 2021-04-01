package ch.ethz.infk.pps.zeph.server.master.util;

import java.io.Closeable;
import java.io.IOException;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerde;
import ch.ethz.infk.pps.zeph.shared.serde.WindowedUniverseIdSerde;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Membership;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.UniversePartitionStatus;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;

public class SerdeFactory implements Closeable {

	private Serde<Long> longSerde = Serdes.Long();
	private Serde<Integer> intSerde = Serdes.Integer();

	private WindowedUniverseIdSerde windowedUniverseIdSerde = new WindowedUniverseIdSerde();

	private SpecificAvroSimpleSerde<Token> tokenSerde = new SpecificAvroSimpleSerde<>(
			Token.getClassSchema());

	private SpecificAvroSimpleSerde<UniversePartitionUpdate> updateSerde = new SpecificAvroSimpleSerde<>(
			UniversePartitionUpdate.getClassSchema());

	private SpecificAvroSimpleSerde<TransformationResult> resultSerde = new SpecificAvroSimpleSerde<>(
			TransformationResult.getClassSchema());

	private SpecificAvroSimpleSerde<Membership> membershipSerde = new SpecificAvroSimpleSerde<>(
			Membership.getClassSchema());

	private SpecificAvroSimpleSerde<UniversePartitionStatus> universePartitionStatusSerde = new SpecificAvroSimpleSerde<>(
			UniversePartitionStatus.getClassSchema());

	private SpecificAvroSimpleSerde<Digest> digestSerde = new SpecificAvroSimpleSerde<>(
			Digest.getClassSchema());

	private SpecificAvroSimpleSerde<DeltaUniverseState> universeWindowStateSerde = new SpecificAvroSimpleSerde<>(
			DeltaUniverseState.getClassSchema());

	public Serde<Long> longSerde() {
		return longSerde;
	}

	public Serde<Integer> intSerde() {
		return intSerde;
	}

	public WindowedUniverseIdSerde windowedUniverseIdSerde() {
		return windowedUniverseIdSerde;
	}

	public SpecificAvroSimpleSerde<Token> tokenSerde() {
		return tokenSerde;
	}

	public SpecificAvroSimpleSerde<UniversePartitionUpdate> updateSerde() {
		return updateSerde;
	}

	public SpecificAvroSimpleSerde<TransformationResult> resultSerde() {
		return resultSerde;
	}

	public SpecificAvroSimpleSerde<Membership> membershipSerde() {
		return membershipSerde;
	}

	public SpecificAvroSimpleSerde<UniversePartitionStatus> universePartitionStatusSerde() {
		return universePartitionStatusSerde;
	}

	public SpecificAvroSimpleSerde<Digest> digestSerde() {
		return digestSerde;
	}

	public SpecificAvroSimpleSerde<DeltaUniverseState> universeWindowStateSerde() {
		return universeWindowStateSerde;
	}

	@Override
	public void close() throws IOException {
		longSerde.close();
		intSerde.close();
		windowedUniverseIdSerde.close();
		tokenSerde.close();
		updateSerde.close();
		resultSerde.close();
		membershipSerde.close();
		universePartitionStatusSerde.close();
		digestSerde.close();
		universeWindowStateSerde.close();

	}

}
