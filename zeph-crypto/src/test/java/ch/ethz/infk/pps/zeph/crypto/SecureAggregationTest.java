package ch.ethz.infk.pps.zeph.crypto;

import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Multimap;

import ch.ethz.infk.pps.zeph.crypto.util.SecureAggregationPartitioner;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class SecureAggregationTest {

	private static int size = 100;
	private static Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys;
	private static Set<Long> nodeIds;

	@BeforeClass
	public static void setup() {

		sharedKeys = new HashMap<>();

		int count = size * (size - 1) / 2;

		List<SecretKey> keys = CryptoUtil.generateKeys(count);// skip ecdh
		Iterator<SecretKey> iter = keys.iterator();

		for (int i = 1; i <= size; i++) {
			for (int j = i + 1; j <= size; j++) {
				sharedKeys.put(ImmutableUnorderedPair.of((long) i, (long) j), iter.next());
			}
		}

		nodeIds = LongStream.range(1, size + 1).boxed().collect(Collectors.toSet());
	}

	@Test
	public void testSecureAggregation() {

		SecureAggregation aggregation1 = new SecureAggregation();
		SecureAggregation aggregation2 = new SecureAggregation();

		aggregation1.addSharedKeys(sharedKeys);
		aggregation2.addSharedKeys(sharedKeys);

		for (short t = 0; t < 2; t++) {
			Digest aggregator = DigestOp.empty();

			for (int i = 1; i <= size; i++) {
				long start = t * 2000;
				Window window = new Window(start, start + 2000);

				Digest dummyKey = null;
				if (i % 4 == 0) {
					dummyKey = aggregation1.getDummyKeySum(window, i, nodeIds);
				} else {
					dummyKey = aggregation2.getDummyKeySum(window, i, nodeIds);
				}
				aggregator = DigestOp.add(aggregator, dummyKey);
			}
			assertEquals(DigestOp.empty(), aggregator);
		}
	}

	@Test
	public void testSecureAggregationER() {

		SecureAggregationPartitioner partitioner = new SecureAggregationPartitioner(size, 1, 1.0e-5);
		partitioner.addSharedKeys(sharedKeys);

		SecureAggregation secureAggregation = new SecureAggregation();
		secureAggregation.addSharedKeys(sharedKeys);

		long epoch = 1;

		// perform partitioning
		Map<Long, Multimap<Integer, Long>> map = new HashMap<>();
		for (long i = 1; i <= size; i++) {
			Multimap<Integer, Long> partitions = partitioner.getPartitions(epoch, i, nodeIds);
			map.put(i, partitions);
		}

		for (int w = 0; w < 2; w++) {
			final long start = w * 2000;
			final long end = start + 2000;
			Window window = new Window(start, end);

			Digest aggregator = DigestOp.empty();

			for (long i = 1; i <= size; i++) {
				Collection<Long> members = map.get(i).removeAll(w);
				Digest dummyKeySum = secureAggregation.getDummyKeySum(window, i, members);
				aggregator = DigestOp.add(aggregator, dummyKeySum);
			}

			assertEquals(DigestOp.empty(), aggregator);
		}

	}

}
