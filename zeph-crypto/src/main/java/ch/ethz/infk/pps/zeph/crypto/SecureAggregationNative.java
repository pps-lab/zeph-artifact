package ch.ethz.infk.pps.zeph.crypto;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scijava.nativelib.DefaultJniExtractor;
import org.scijava.nativelib.JniExtractor;
import org.scijava.nativelib.NativeLibraryUtil;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class SecureAggregationNative implements Closeable {

	private static Logger LOG = LogManager.getLogger();

	private static native long jniInit(long nfields);

	private static native void jniAddSharedKeys(long pointer, Map<String, byte[]> sharedKeys);

	private static native void jniGetDummyKeySum(long pointer, long timestamp, long nodeId, long[] nodeIds,
			DummyKeySumCallback callback);

	private static native void jniBuildEpochNeighbourhoodsER(long pointer, long epoch, long nodeId, long[] nodeIds,
			int k);

	private static native void jniClearEpochNeighbourhoodsER(long pointer, long epoch, long nodeId);

	private static native void jniGetDummyKeySumER(long pointer, long timestamp, long epoch, short t, long nodeId,
			long[] droppedNodeIds, DummyKeySumCallback callback);

	private static native void jniClose(long pointer);

	static {
		try {

			JniExtractor extractor = new DefaultJniExtractor(SecureAggregationNative.class);
			boolean success = NativeLibraryUtil.loadNativeLibrary(extractor, "secure_aggregation");

			if (!success) {
				throw new IllegalStateException("failed to load native library secure_aggregation");
			}

		} catch (IOException e) {
			throw new IllegalStateException("failed to load native library secure_aggregation", e);
		}

	}

	private Long pointer;

	public SecureAggregationNative() {
		long nFields = DigestOp.getTotalNumberOfElements();
		this.pointer = SecureAggregationNative.jniInit(nFields);
	}

	public void addSharedKeys(Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys) {
		Map<String, byte[]> map = new HashMap<>();

		sharedKeys.forEach((pair, sharedKey) -> {
			map.put(pair.toString(), sharedKey.getEncoded());
		});

		SecureAggregationNative.jniAddSharedKeys(pointer, map);
	}

	public void buildEpochNeighbourhoodsER(long epoch, Collection<Long> nodeIds, Set<Long> neighbourIds, int k) {
		long[] nodeIdArr = Longs.toArray(neighbourIds);
		nodeIds.forEach(
				nodeId -> SecureAggregationNative.jniBuildEpochNeighbourhoodsER(pointer, epoch, nodeId, nodeIdArr, k));
	}

	public void clearEpochNeighbourhoodsER(long epoch, Collection<Long> nodeIds) {
		nodeIds.forEach(nodeId -> SecureAggregationNative.jniClearEpochNeighbourhoodsER(pointer, epoch, nodeId));
	}

	public Digest getDummyKeySumER(long timestamp, long epoch, short t, long nodeId, Set<Long> droppedNodeIds) {
		DummyKeySumCallback callback = new DummyKeySumCallback();

		long[] droppedNodeArr = Longs.toArray(droppedNodeIds);
		SecureAggregationNative.jniGetDummyKeySumER(pointer, timestamp, epoch, t, nodeId, droppedNodeArr, callback);

		try {
			return callback.cf.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException("failed", e);
		}
	}

	public Digest getDummyKeySum(long timestamp, long nodeId, Set<Long> nodeIds) {
		DummyKeySumCallback callback = new DummyKeySumCallback();
		SecureAggregationNative.jniGetDummyKeySum(pointer, timestamp, nodeId, Longs.toArray(nodeIds), callback);

		try {
			return callback.cf.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException("failed", e);
		}
	}

	public static class DummyKeySumCallback {

		CompletableFuture<Digest> cf = new CompletableFuture<>();
		
		public void dummyKeySumCallback(long[] values) {			
			cf.complete(DigestOp.of(values));
		}

		public CompletableFuture<Digest> getCf() {
			return cf;
		}

	}

	@Override
	public void close() {
		SecureAggregationNative.jniClose(pointer);
	}

}
