package ch.ethz.infk.pps.zeph.shared.serde;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Serializer;

public class SpecificAvroSimpleSerializer<T extends SpecificRecordBase> implements Serializer<T> {

	private DatumWriter<T> writer;

	private int encoderBufferSize;

	private EncoderFactory encoderFactory = new EncoderFactory();

	private int byteArrayBaseSize;

	public SpecificAvroSimpleSerializer(Schema schema) {
		this.writer = new SpecificDatumWriter<>(schema);
		this.encoderBufferSize = 2048;
		this.byteArrayBaseSize = 2048;
		this.encoderFactory.configureBufferSize(this.encoderBufferSize);
	}

	@Override
	public byte[] serialize(String topic, T data) {
		if (data == null) {
			return null;
		}
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream(this.byteArrayBaseSize);
			BinaryEncoder binaryEncoder = this.encoderFactory.binaryEncoder(os, null);
			this.writer.write(data, binaryEncoder);
			binaryEncoder.flush();
			os.flush();
			os.close();
			return os.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

}
