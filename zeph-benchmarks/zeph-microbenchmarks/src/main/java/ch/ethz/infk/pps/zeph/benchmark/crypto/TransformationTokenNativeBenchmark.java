package ch.ethz.infk.pps.zeph.benchmark.crypto;

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.crypto.IHeac;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationNative;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class TransformationTokenNativeBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testStrawmanNative(BenchmarkExecutionPlan benchPlan, OpCounter counter, Blackhole blackhole) {

		long start = 50000;
		long end = start + benchPlan.windowSizeMillis;

		final int numWindows = benchPlan.numWindows;
		final long producerId = benchPlan.producerId;

		counter.count += numWindows;

		for (int w = 0; w < numWindows; w++) {
			Digest dummyKeySum = benchPlan.secureAggregation.getDummyKeySum(start,
					producerId, benchPlan.producerIds);
			Digest heacKey = benchPlan.heac.getKey(start - 1, end - 1);
			Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
			blackhole.consume(transformationToken);

			start += benchPlan.windowSizeMillis;
			end += benchPlan.windowSizeMillis;
		}

	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testErdosRenyiNative(BenchmarkExecutionPlan benchPlan, OpCounter counter, Blackhole blackhole) {

		long start = 50000;
		long end = start + benchPlan.windowSizeMillis;
		long epoch = 1;

		Set<Long> dropped = Collections.emptySet();

		final int numWindows = benchPlan.numWindows;
		final int epochSize = benchPlan.epochSize;

		final long producerId = benchPlan.producerId;
		final List<Long> producerIdList = Collections.singletonList(producerId);

		counter.count += numWindows;

		int w = 0;
		while (w < numWindows) {

			benchPlan.secureAggregation.buildEpochNeighbourhoodsER(epoch, producerIdList, benchPlan.producerIds,
					benchPlan.k);

			for (short t = 0; t < epochSize; t++) {

				Digest dummyKeySum = benchPlan.secureAggregation.getDummyKeySumER(start, epoch, t, producerId,
						dropped);

				Digest heacKey = benchPlan.heac.getKey(start - 1, end - 1);
				Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
				blackhole.consume(transformationToken);

				start += benchPlan.windowSizeMillis;
				end += benchPlan.windowSizeMillis;
				w++;

				if (w >= numWindows) {
					break;
				}

			}
			benchPlan.secureAggregation.clearEpochNeighbourhoodsER(epoch, producerIdList);
			epoch += 1;
		}

	}

	@State(Scope.Thread)
	@AuxCounters
	public static class OpCounter {

		public int count;

	}

	@State(Scope.Benchmark)
	public static class BenchmarkExecutionPlan {

		// NOTE: https://stackoverflow.com/a/19562164 to be able to use bouncycastle

		@Param({ "1000_1", "1000_256", "1000_512" })
		public String size_win;

		@Param({ "/data" })
		public String dataDir;

		public int universeSize;
		public int numWindows;

		public SecureAggregationNative secureAggregation;
		public IHeac heac;

		public final long producerId = 1l;
		public Set<Long> producerIds;

		public int k;
		public int epochSize;

		public final long windowSizeMillis = 1000;

		@Setup(Level.Trial)
		public void setupBenchmark() throws NoSuchAlgorithmException {

			String[] parts = size_win.split("_");
			universeSize = Integer.parseInt(parts[0]);
			numWindows = Integer.parseInt(parts[1]);

			k = ErdosRenyiUtil.getK(universeSize, 0.5, 1.0e-5);
			epochSize = ErdosRenyiUtil.getNumberOfGraphs(k);

			producerIds = LongStream.rangeClosed(1, universeSize).boxed().collect(Collectors.toSet());
			Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = DataLoader.createAndLoadSharedKeys(
					Collections.singleton(producerId),
					producerIds, dataDir, null, true);

			// setup secure aggregation instance
			this.secureAggregation = new SecureAggregationNative();
			this.secureAggregation.addSharedKeys(sharedKeys);

			SecretKey heacKey = CryptoUtil.generateKey();
			this.heac = new Heac(heacKey);
		}

	}

}
