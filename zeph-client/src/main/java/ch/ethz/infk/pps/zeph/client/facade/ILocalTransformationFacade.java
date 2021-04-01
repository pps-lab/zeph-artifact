package ch.ethz.infk.pps.zeph.client.facade;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;

import ch.ethz.infk.pps.shared.avro.Digest;

public interface ILocalTransformationFacade {
	
	public void init(long producerId, long universeId);

	public CompletableFuture<RecordMetadata> sendCiphertext(long timestamp, Digest ciphertext, long heacStart);
	
	public void close();

}
