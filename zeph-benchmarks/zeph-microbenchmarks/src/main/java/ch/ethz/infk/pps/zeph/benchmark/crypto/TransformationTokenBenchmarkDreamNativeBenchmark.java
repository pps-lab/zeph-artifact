package ch.ethz.infk.pps.zeph.benchmark.crypto;

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
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
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationDreamNative;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class TransformationTokenBenchmarkDreamNativeBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testDreamNative(BenchmarkExecutionPlan benchPlan, OpCounter counter, Blackhole blackhole) {

		long start = 50000;
		long end = start + benchPlan.windowSizeMillis;

		final int numWindows = benchPlan.numWindows;
		final long producerId = benchPlan.producerId;

		counter.count += numWindows;

		for (int w = 0; w < numWindows; w++) {
			Digest dummyKeySum = benchPlan.secureAggregation.getDummyKeySum(start, start + 1, producerId,
					benchPlan.producerIds, benchPlan.k);
			Digest heacKey = benchPlan.heac.getKey(start - 1, end - 1);
			Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
			blackhole.consume(transformationToken);

			start += benchPlan.windowSizeMillis;
			end += benchPlan.windowSizeMillis;
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

		public SecureAggregationDreamNative secureAggregation;
		public IHeac heac;

		public final long producerId = 1l;
		public Set<Long> producerIds;

		public int k;

		public final long windowSizeMillis = 1000;

		@Setup(Level.Trial)
		public void setupBenchmark() throws NoSuchAlgorithmException {

			String[] parts = size_win.split("_");
			universeSize = Integer.parseInt(parts[0]);
			numWindows = Integer.parseInt(parts[1]);

			k = ErdosRenyiUtil.getK(universeSize, 0.5, 1.0e-5);

			producerIds = LongStream.rangeClosed(1, universeSize).boxed().collect(Collectors.toSet());
			Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = DataLoader.createAndLoadSharedKeys(
					Collections.singleton(producerId),
					producerIds, dataDir, null, true);

			// setup secure aggregation instance
			this.secureAggregation = new SecureAggregationDreamNative();
			this.secureAggregation.addSharedKeys(sharedKeys);

			SecretKey heacKey = CryptoUtil.generateKey();
			this.heac = new Heac(heacKey);
		}

	}

}
