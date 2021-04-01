package ch.ethz.infk.pps.zeph.crypto;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.junit.BeforeClass;
import org.junit.Test;

import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class SecureAggregationDreamNativeTest {

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

		System.out.println(k);
	}

	@Test
	public void testSecureAggregationNative() {

		SecureAggregationDreamNative aggregation1 = new SecureAggregationDreamNative();
		SecureAggregationDreamNative aggregation2 = new SecureAggregationDreamNative();

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
					dummyKey = aggregation1.getDummyKeySum(start + 1, start, i, nodeIds, k);
				} else {
					dummyKey = aggregation2.getDummyKeySum(start + 1, start, i, nodeIds, k);
				}
				aggregator = DigestOp.add(aggregator, dummyKey);
			}
			assertEquals(DigestOp.empty(), aggregator);
		}

		aggregation1.close();
		aggregation2.close();
	}

}
