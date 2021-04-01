package ch.ethz.infk.pps.zeph.benchmark.crypto;

import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

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

import com.google.common.collect.Multimap;

import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.crypto.IHeac;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregation;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationDream;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.crypto.util.SecureAggregationPartitioner;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class TransformationTokenBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testStrawman(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {

		long start = 50000;
		long end = start + benchPlan.windowSizeMillis;

		final int numWindows = benchPlan.numWindows;
		final long producerId = benchPlan.producerId;

		for (int w = 0; w < numWindows; w++) {
			Window window = new Window(start, end);
			Digest dummyKeySum = benchPlan.secureAggregation.getDummyKeySum(window, producerId, benchPlan.producerIds);
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
	public void testErdosRenyi(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {

		long start = 50000;
		long end = start + benchPlan.windowSizeMillis;
		long epoch = 1;

		final int numWindows = benchPlan.numWindows;
		final int epochSize = benchPlan.epochSize;
		final long producerId = benchPlan.producerId;

		int w = 0;
		while (true) {

			Multimap<Integer, Long> partitions = benchPlan.partitioner.getPartitions(epoch, producerId,
					benchPlan.producerIds);

			for (short t = 0; t < epochSize; t++) {

				if (w >= numWindows) {
					return;
				}

				Window window = new Window(start, end);
				Collection<Long> members = partitions.removeAll(w);
				Digest dummyKeySum = benchPlan.secureAggregation.getDummyKeySum(window, producerId, members);

				Digest heacKey = benchPlan.heac.getKey(start - 1, end - 1);
				Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
				blackhole.consume(transformationToken);

				start += benchPlan.windowSizeMillis;
				end += benchPlan.windowSizeMillis;
				w++;
			}

			epoch += 1;
		}
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testDream(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {

		long start = 50000;
		long end = start + benchPlan.windowSizeMillis;

		final int numWindows = benchPlan.numWindows;
		final long producerId = benchPlan.producerId;

		for (int w = 0; w < numWindows; w++) {
			Window window = new Window(start, end);
			Digest dummyKeySum = benchPlan.secureAggregationDream.getDummyKeySum(window, producerId,
					benchPlan.producerIds);
			Digest heacKey = benchPlan.heac.getKey(start - 1, end - 1);
			Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
			blackhole.consume(transformationToken);

			start += benchPlan.windowSizeMillis;
			end += benchPlan.windowSizeMillis;
		}

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

		public SecureAggregation secureAggregation;
		public SecureAggregationDream secureAggregationDream;
		private SecureAggregationPartitioner partitioner;
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
			this.secureAggregation = new SecureAggregation();
			this.secureAggregation.addSharedKeys(sharedKeys);

			this.secureAggregationDream = new SecureAggregationDream();
			this.secureAggregationDream.addSharedKeys(sharedKeys);

			this.partitioner = new SecureAggregationPartitioner(universeSize, producerId, 1.0e-5);
			this.partitioner.addSharedKeys(sharedKeys);

			SecretKey heacKey = CryptoUtil.generateKey();
			this.heac = new Heac(heacKey);
		}

	}

}
