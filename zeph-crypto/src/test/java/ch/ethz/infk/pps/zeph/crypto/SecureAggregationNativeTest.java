package ch.ethz.infk.pps.zeph.crypto;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.junit.BeforeClass;
import org.junit.Test;

import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class SecureAggregationNativeTest {

	private static int size = 100;
	private static int k;
	private static Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys;
	private static Set<Long> nodeIds = new HashSet<>();

	@BeforeClass
	public static void setup() {

		sharedKeys = new HashMap<>();
		for (int i = 1; i <= size; i++) {
			for (int j = i + 1; j <= size; j++) {

				SecretKey sharedKey = CryptoUtil.generateKey(); // skip ecdh
				sharedKeys.put(ImmutableUnorderedPair.of((long) i, (long) j), sharedKey);
			}
		}

		LongStream.rangeClosed(1, size).forEach(pId -> nodeIds.add(pId));

		k = ErdosRenyiUtil.getK(size, 0.5, 1.0e-5);
	}

	@Test
	public void testSecureAggregationNative() {

		SecureAggregationNative aggregation1 = new SecureAggregationNative();
		SecureAggregationNative aggregation2 = new SecureAggregationNative();

		Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys1 = new HashMap<>();
		Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys2 = new HashMap<>();

		sharedKeys.forEach((k, v) -> {
			if (k.hashCode() % 2 == 0) {
				sharedKeys1.put(k, v);
			} else {
				sharedKeys2.put(k, v);
			}
		});

		aggregation1.addSharedKeys(sharedKeys1);
		aggregation1.addSharedKeys(sharedKeys2);
		aggregation1.addSharedKeys(sharedKeys1);
		aggregation2.addSharedKeys(sharedKeys);

		for (short t = 0; t < 2; t++) {
			Digest aggregator = DigestOp.empty();

			for (int i = 1; i <= size; i++) {
				long start = t * 2000;

				Digest dummyKey = null;
				if (i % 4 == 0) {
					dummyKey = aggregation1.getDummyKeySum(start, i, nodeIds);
				} else {
					dummyKey = aggregation2.getDummyKeySum(start, i, nodeIds);
				}
				aggregator = DigestOp.add(aggregator, dummyKey);
			}
			assertEquals(DigestOp.empty(), aggregator);
		}

		aggregation1.close();
		aggregation2.close();
	}

	@Test
	public void testConcurrentSecureAggregationERNative() throws InterruptedException, ExecutionException {
		long epoch = 1l;

		List<Long> producerIds = LongStream.rangeClosed(1, size).boxed().collect(Collectors.toList());

		SecureAggregationNative aggregation = new SecureAggregationNative();
		aggregation.addSharedKeys(sharedKeys);

		aggregation.buildEpochNeighbourhoodsER(epoch, producerIds, nodeIds, k);

		CompletableFuture<Void> cf = CompletableFuture.allOf(LongStream.rangeClosed(1, size).boxed().map(pId -> {

			return CompletableFuture.runAsync(() -> {
				aggregation.getDummyKeySumER(1000l, epoch, (short) 6, pId, Collections.emptySet());
			});

		}).toArray(CompletableFuture[]::new));

		cf.get();

		aggregation.clearEpochNeighbourhoodsER(epoch, producerIds);

		aggregation.close();

	}

	@Test
	public void testSecureAggregationERNative() {

		long epoch = 1l;

		SecureAggregationNative aggregation1 = new SecureAggregationNative();
		SecureAggregationNative aggregation2 = new SecureAggregationNative();

		aggregation1.addSharedKeys(sharedKeys);
		aggregation2.addSharedKeys(sharedKeys);

		// build neighbourhoods
		List<Long> producerIds1 = LongStream.rangeClosed(1, size).filter(pId -> pId % 4 == 0).boxed()
				.collect(Collectors.toList());
		aggregation1.buildEpochNeighbourhoodsER(epoch, producerIds1, nodeIds, k);

		List<Long> producerIds2 = LongStream.rangeClosed(1, size).filter(pId -> pId % 4 != 0).boxed()
				.collect(Collectors.toList());
		aggregation2.buildEpochNeighbourhoodsER(epoch, producerIds2, nodeIds, k);

		for (short t = 0; t < 2; t++) {
			Digest aggregator = DigestOp.empty();

			Set<Long> dropped = new HashSet<>();
			LongStream.range(50, 60).forEach(pId -> dropped.add(pId));

			for (int i = 1; i <= size; i++) {
				if (i >= 50 && i < 60) {
					continue;
				}

				long start = t * 2000;

				Digest dummyKey = null;
				if (i % 4 == 0) {
					dummyKey = aggregation1.getDummyKeySumER(start, epoch, t, i, dropped);
				} else {
					dummyKey = aggregation2.getDummyKeySumER(start, epoch, t, i, dropped);
				}
				aggregator = DigestOp.add(aggregator, dummyKey);
			}
			assertEquals(DigestOp.empty(), aggregator);
		}

		aggregation1.clearEpochNeighbourhoodsER(epoch, producerIds1);
		aggregation2.clearEpochNeighbourhoodsER(epoch, producerIds2);

		aggregation1.close();
		aggregation2.close();

	}
}
