package ch.ethz.infk.pps.zeph.benchmark.crypto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationDreamNative;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationNative;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;

public class TransformationTokenNativeLatencyBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testStrawmanNativeLatency(BenchmarkState state, Blackhole blackhole) {
		Digest dummyKeySum = state.secureAggregation.getDummyKeySum(state.start, state.producerId, state.producerIds);
		Digest heacKey = state.heac.getKey(state.start - 1, state.end - 1);
		Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
		blackhole.consume(transformationToken);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testDreamNativeLatency(BenchmarkDreamState state, Blackhole blackhole) {
		Digest dummyKeySum = state.secureAggregation.getDummyKeySum(state.start, state.start + 1, state.producerId,
				state.producerIds, state.k);
		Digest heacKey = state.heac.getKey(state.start - 1, state.end - 1);
		Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
		blackhole.consume(transformationToken);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testErdosRenyiNativeLatency(ErRunState state, Blackhole blackhole) {
		Digest dummyKeySum = state.secureAggregation.getDummyKeySumER(state.start, state.epoch, state.t,
				state.producerId, Collections.emptySet());
		Digest heacKey = state.heac.getKey(state.start - 1, state.end - 1);
		Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
		blackhole.consume(transformationToken);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testErdosRenyiBuildNeighbourhoodNativeLatency(ErBuildNeighbourhoodState state) {
		state.secureAggregation.buildEpochNeighbourhoodsER(state.epoch, state.nodeIds, state.producerIds,
				state.k);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testErdosRenyiClearNeighbourhoodNativeLatency(ErClearNeighbourhoodState state) {
		state.secureAggregation.clearEpochNeighbourhoodsER(state.epoch, state.nodeIds);
	}

	@State(Scope.Thread)
	public static class BenchmarkConfigState {

		@Param({ "100", "200", "500", "1000", "10000" })
		public int universe_size;

		@Param({ "/data" })
		public String dataDir;

		public long start;
		public long end;
		public int k;
		public long producerId = 1l;
		public Set<Long> producerIds;
		public Heac heac;

		public final long windowSizeMillis = 1000;

		public Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys;

		@Setup(Level.Trial)
		public void setupBenchmark() {

			k = ErdosRenyiUtil.getK(universe_size, 0.5, 1.0e-5);

			producerIds = LongStream.rangeClosed(1, universe_size).boxed().collect(Collectors.toSet());
			sharedKeys = DataLoader.createAndLoadSharedKeys(Collections.singleton(producerId), producerIds, dataDir,
					null, true);

			SecretKey heacKey = CryptoUtil.generateKey();
			this.heac = new Heac(heacKey);

			start = WindowUtil.getWindowStart(System.currentTimeMillis(), windowSizeMillis);
			end = start + windowSizeMillis;
		}

	}

	@State(Scope.Thread)
	public static class BenchmarkDreamState extends BenchmarkConfigState {

		public SecureAggregationDreamNative secureAggregation;

		@Setup(Level.Trial)
		public void setupBenchmark() {
			super.setupBenchmark();
			this.secureAggregation = new SecureAggregationDreamNative();
			this.secureAggregation.addSharedKeys(sharedKeys);
		}

	}

	@State(Scope.Thread)
	public static class BenchmarkState extends BenchmarkConfigState {

		public SecureAggregationNative secureAggregation;
		public long epoch = 1;
		public short t = 16;

		@Setup(Level.Trial)
		public void setupBenchmark() {
			super.setupBenchmark();
			this.secureAggregation = new SecureAggregationNative();
			this.secureAggregation.addSharedKeys(sharedKeys);
		}

	}

	@State(Scope.Thread)
	public static class ErRunState extends BenchmarkState {

		public List<Long> nodeIds = Collections.singletonList(producerId);

		@Setup(Level.Iteration)
		public void build() {
			secureAggregation.buildEpochNeighbourhoodsER(epoch, nodeIds, producerIds, k);
		}

		@TearDown(Level.Iteration)
		public void clear() {
			secureAggregation.clearEpochNeighbourhoodsER(epoch, nodeIds);
		}
	}

	@State(Scope.Thread)
	public static class ErBuildNeighbourhoodState extends BenchmarkState {

		public List<Long> nodeIds = Collections.singletonList(producerId);

		@TearDown(Level.Invocation)
		public void clear() {
			secureAggregation.clearEpochNeighbourhoodsER(epoch, nodeIds);
		}
	}

	@State(Scope.Thread)
	public static class ErClearNeighbourhoodState extends BenchmarkState {

		public List<Long> nodeIds = Collections.singletonList(producerId);

		@Setup(Level.Invocation)
		public void build() {
			secureAggregation.buildEpochNeighbourhoodsER(epoch, nodeIds, producerIds, k);
		}
	}

}
