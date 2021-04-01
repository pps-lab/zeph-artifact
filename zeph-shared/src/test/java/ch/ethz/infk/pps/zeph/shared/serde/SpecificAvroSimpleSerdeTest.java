package ch.ethz.infk.pps.zeph.shared.serde;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.Test;

import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.Window;

public class SpecificAvroSimpleSerdeTest {

	@Test
	public void testDigest() {

		SpecificAvroSimpleSerde<Digest> serde = new SpecificAvroSimpleSerde<>(Digest.getClassSchema());
		Serializer<Digest> serializer = serde.serializer();
		Deserializer<Digest> deserializer = serde.deserializer();

		Digest digest = DigestOp.random();

		byte[] byteArray = serializer.serialize(null, digest);

		Digest result = deserializer.deserialize(null, byteArray);

		assertEquals(digest, result);

		serde.close();
	}

	@Test
	public void testHeacHeader() {

		SpecificAvroSimpleSerde<HeacHeader> serde = new SpecificAvroSimpleSerde<>(
				HeacHeader.getClassSchema());
		Serializer<HeacHeader> serializer = serde.serializer();
		Deserializer<HeacHeader> deserializer = serde.deserializer();

		HeacHeader data = new HeacHeader(100000l, 150000l);

		byte[] byteArray = serializer.serialize(null, data);

		HeacHeader result = deserializer.deserialize(null, byteArray);

		assertEquals(data, result);

		serde.close();
	}

	@Test
	public void testToken() {
		SpecificAvroSimpleSerde<Token> serde = new SpecificAvroSimpleSerde<>(
				Token.getClassSchema());
		Serializer<Token> serializer = serde.serializer();
		Deserializer<Token> deserializer = serde.deserializer();

		long start = WindowUtil.getWindowStart(System.currentTimeMillis(), 30000l);
		long end = start + 30000;

		Window window = new Window(start, end);
		HeacHeader commit = new HeacHeader(100000l, 150000l);

		Token commitData = new Token(window, "", null, commit, null);
		byte[] commitByteArray = serializer.serialize(null, commitData);
		Token commitResult = deserializer.deserialize(null, commitByteArray);
		assertEquals(commitData, commitResult);

		Token transformationData = new Token(window, "", DigestOp.random(), null, null);
		byte[] transByteArray = serializer.serialize(null, transformationData);
		Token tokenResult = deserializer.deserialize(null, transByteArray);
		assertEquals(transformationData, tokenResult);

		Token statusData = new Token(window, "", null, null, WindowStatus.COMMITTED.code());
		byte[] statusByteArray = serializer.serialize(null, statusData);
		Token statusResult = deserializer.deserialize(null, statusByteArray);
		assertEquals(statusData, statusResult);

		serde.close();
	}

}
