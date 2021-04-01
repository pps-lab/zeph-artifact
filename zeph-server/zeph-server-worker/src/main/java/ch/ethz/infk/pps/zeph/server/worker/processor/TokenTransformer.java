package ch.ethz.infk.pps.zeph.server.worker.processor;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.ProcessorContext;

import ch.ethz.infk.pps.zeph.server.worker.token.CommitHandler;
import ch.ethz.infk.pps.zeph.server.worker.token.StatusHandler;
import ch.ethz.infk.pps.zeph.server.worker.token.TransformationTokenHandler;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.Window;

public class TokenTransformer implements Transformer<Long, Token, Iterable<KeyValue<Long, UniversePartitionUpdate>>> {

	private ProcessorContext ctx;
	private Names n;

	private CommitHandler commitHandler;
	private StatusHandler statusHandler;
	private TransformationTokenHandler transformationHandler;

	public TokenTransformer(Names n) {
		this.n = n;
	}

	@Override
	public void init(ProcessorContext context) {
		this.ctx = context;
		this.commitHandler = new CommitHandler(this.ctx, this.n);
		this.statusHandler = new StatusHandler(this.ctx, this.n);
		this.transformationHandler = new TransformationTokenHandler(this.ctx, this.n);
	}

	@Override
	public Iterable<KeyValue<Long, UniversePartitionUpdate>> transform(Long producerId, Token token) {

		Window window = token.getWindow();

		// verify that the token is valid (only one of the possible types is used)
		int count = 0;
		HeacHeader commitInfo = token.getCommit();
		count += commitInfo == null ? 0 : 1;

		Integer status = token.getStatus();
		count += status == null ? 0 : 1;

		Digest transformationToken = token.getTransformation();
		count += transformationToken == null ? 0 : 1;

		if (count != 1) {
			// ignore invalid token
			return null;
		}

		// handle the different types of tokens
		if (commitInfo != null) {
			return commitHandler.handleCommit(producerId, window);
		} else if (transformationToken != null) {
			return transformationHandler.handleTransformationToken(producerId, window, transformationToken);
		} else {
			return statusHandler.handleWindowCommitted(window);
		}

	}

	@Override
	public void close() {
		// do nothing
	}

	public static class TokenTransformerSupplier
			implements TransformerSupplier<Long, Token, Iterable<KeyValue<Long, UniversePartitionUpdate>>> {

		private Names names;

		public TokenTransformerSupplier(Names names) {
			this.names = names;
		}

		@Override
		public Transformer<Long, Token, Iterable<KeyValue<Long, UniversePartitionUpdate>>> get() {
			return new TokenTransformer(names);
		}

	}

}
