package ch.ethz.infk.pps.zeph.client.facade;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.RecordMetadata;

import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public interface IPrivacyControllerFacade {

	public void init(Set<Long> universeIds);

	public CompletableFuture<RecordMetadata> sendCommit(long producerId, Window window, long universeId);

	public CompletableFuture<RecordMetadata> sendTransformationToken(long producerId, Window window,
			Digest transformationToken, long universeId);

	public ConsumerRecords<WindowedUniverseId, DeltaUniverseState> pollInfos();

	public void close();

}
