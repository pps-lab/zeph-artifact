package ch.ethz.infk.pps.zeph.shared.serde;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.Window;

public class WindowedUniverseIdSerde implements Serde<WindowedUniverseId> {

	private WindowedUniverseIdSerializer serializer = new WindowedUniverseIdSerializer();
	private WindowedUniverseIdDeserializer deserializer = new WindowedUniverseIdDeserializer();

	@Override
	public Serializer<WindowedUniverseId> serializer() {
		return serializer;
	}

	@Override
	public Deserializer<WindowedUniverseId> deserializer() {
		return deserializer;
	}

	@Override
	public void close() {
		serializer.close();
		deserializer.close();
	}

	public static class WindowedUniverseIdSerializer implements Serializer<WindowedUniverseId> {

		private LongSerializer inner = new LongSerializer();

		@Override
		public byte[] serialize(String topic, WindowedUniverseId data) {
			if (data == null) {
				return null;
			}

			try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
					final DataOutputStream out = new DataOutputStream(baos)) {
				final byte[] serialized1 = inner.serialize(topic, data.getUniverseId());
				final byte[] serialized2 = inner.serialize(topic, data.getWindow().getStart());
				final byte[] serialized3 = inner.serialize(topic, data.getWindow().getEnd());

				out.write(serialized1);
				out.write(serialized2);
				out.write(serialized3);

				return baos.toByteArray();
			} catch (final IOException e) {
				throw new RuntimeException("unable to serialize windowed universe id", e);
			}
		}

		@Override
		public void close() {
			inner.close();
		}

	}

	public static class WindowedUniverseIdDeserializer implements Deserializer<WindowedUniverseId> {

		private LongDeserializer inner = new LongDeserializer();

		@Override
		public WindowedUniverseId deserialize(String topic, byte[] bytes) {
			if (bytes == null || bytes.length == 0) {
				return null;
			}

			try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {

				byte[] array = new byte[8];
				in.readFully(array);
				long universeId = inner.deserialize(topic, array);

				in.readFully(array);
				long windowStart = inner.deserialize(topic, array);

				in.readFully(array);
				long windowEnd = inner.deserialize(topic, array);

				return new WindowedUniverseId(universeId, new Window(windowStart, windowEnd));

			} catch (final IOException e) {
				throw new RuntimeException("Unable to deserialize windowed universe id", e);
			}

		}

	}

}
