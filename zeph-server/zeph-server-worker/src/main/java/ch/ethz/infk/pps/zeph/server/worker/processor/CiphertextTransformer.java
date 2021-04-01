package ch.ethz.infk.pps.zeph.server.worker.processor;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;

public class CiphertextTransformer implements ValueTransformerWithKey<Long, Digest, Digest> {

	private static final Logger LOG = LogManager.getLogger();
	private Marker m;

	private ProcessorContext ctx;

	@Override
	public void init(ProcessorContext context) {
		this.ctx = context;
		this.m = MarkerManager.getMarker("taskId" + context.taskId().toString());
	}

	@Override
	public Digest transform(Long producerId, Digest value) {

		if (value == null) {
			return null;
		}

		long timestamp = this.ctx.timestamp();

		LOG.trace(m, "T={} P={}  - Record={}", timestamp, producerId, value);

		// extract info from header how this record was encrypted
		Header header = this.ctx.headers().lastHeader("heac");
		byte[] bytes = header.value();
		long prevTimestamp = Longs.fromByteArray(bytes);

		// embed the header in the record
		value.setHeader(new HeacHeader(prevTimestamp, timestamp));
		return value;
	}

	@Override
	public void close() {
		// do nothing
	}

	public static class ValueTransformerSupplier implements ValueTransformerWithKeySupplier<Long, Digest, Digest> {

		@Override
		public ValueTransformerWithKey<Long, Digest, Digest> get() {
			return new CiphertextTransformer();
		}
	}

}
