package ch.ethz.infk.pps.zeph.shared.serde;

import java.io.IOException;
import java.util.Arrays;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;

public class SpecificAvroSimpleDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

	private DatumReader<T> reader;

	private int decoderBufferSize;

	private DecoderFactory decoderFactory = new DecoderFactory();

	public SpecificAvroSimpleDeserializer(Schema schema) {
		this.reader = new SpecificDatumReader<>(schema);
		this.decoderBufferSize = 8192;
		this.decoderFactory.configureDecoderBufferSize(this.decoderBufferSize);
	}

	@Override
	public T deserialize(String topic, byte[] data) {
		if (data == null) {
			return null;
		}

		BinaryDecoder binaryDecoder = this.decoderFactory.binaryDecoder(data, null);
		try {
			T r = this.reader.read(null, binaryDecoder);

			return r;
		} catch (IOException e) {
			throw new RuntimeException("Data=" + Arrays.toString(data), e);
		}
	}

}
