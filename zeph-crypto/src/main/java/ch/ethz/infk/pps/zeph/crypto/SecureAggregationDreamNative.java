package ch.ethz.infk.pps.zeph.crypto;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.crypto.SecretKey;

import org.scijava.nativelib.DefaultJniExtractor;
import org.scijava.nativelib.JniExtractor;
import org.scijava.nativelib.NativeLibraryUtil;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class SecureAggregationDreamNative implements Closeable {

	private static native long jniInit(long nfields);

	private static native void jniAddSharedKeys(long pointer, Map<String, byte[]> sharedKeys);

	private static native void jniGetDummyKeySumDream(long pointer, long r1, long r2, long nodeId,
			long[] clusterNodeIds, int k,
			DummyKeySumCallback callback);

	private static native void jniClose(long pointer);

	static {
		try {

			JniExtractor extractor = new DefaultJniExtractor(SecureAggregationDreamNative.class);
			boolean success = NativeLibraryUtil.loadNativeLibrary(extractor, "secure_aggregation_dream");

			if (!success) {
				throw new IllegalStateException("failed to load native library secure_aggregation_dream");
			}

		} catch (IOException e) {
			throw new IllegalStateException("failed to load native library secure_aggregation_dream", e);
		}

	}

	private long pointer;

	public SecureAggregationDreamNative() {
		long nFields = DigestOp.getTotalNumberOfElements();
		this.pointer = SecureAggregationDreamNative.jniInit(nFields);
	}

	public void addSharedKeys(Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys) {
		Map<String, byte[]> map = new HashMap<>();

		sharedKeys.forEach((pair, sharedKey) -> {
			map.put(pair.toString(), sharedKey.getEncoded());
		});

		SecureAggregationDreamNative.jniAddSharedKeys(pointer, map);
	}

	public Digest getDummyKeySum(long r1, long r2, long nodeId, Set<Long> nodeIds, int k) {
		DummyKeySumCallback callback = new DummyKeySumCallback();
		SecureAggregationDreamNative.jniGetDummyKeySumDream(pointer, r1, r2, nodeId, Longs.toArray(nodeIds), k,
				callback);

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
		SecureAggregationDreamNative.jniClose(pointer);
	}

}
