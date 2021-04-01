package ch.ethz.infk.pps.zeph.benchmark;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public class HashingBenchmark {

	public static void main(String[] args) {
		int x = -100000 % 300;

		System.out.println(x);

		System.out.println(Math.floorMod(-100000, 300));
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testMurmur3(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {
		int[] partitions = testHashFunction(benchPlan.murmur3, benchPlan.numPartitions, benchPlan.memberIds);
		blackhole.consume(partitions);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.All)
	public void testSha256(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {
//		int[] partitions = testHashFunction(benchPlan.sha256, benchPlan.numPartitions, benchPlan.memberIds);
//		blackhole.consume(partitions);

		HashCode code = benchPlan.sha256.hashObject(benchPlan.memberIds,
				Funnels.sequentialFunnel(Funnels.longFunnel()));
		blackhole.consume(code);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.All)
	public void testSha512(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {

		HashCode code = benchPlan.sha512.hashObject(benchPlan.memberIds,
				Funnels.sequentialFunnel(Funnels.longFunnel()));

		// int[] partitions = testHashFunction(benchPlan.sha512,
		// benchPlan.numPartitions, benchPlan.memberIds);
		blackhole.consume(code);
	}

	private int[] testHashFunction(HashFunction hasher, int numPartitions, Set<Long> members) {
		int[] partitions = new int[numPartitions];
		Iterator<Long> iter = members.iterator();
		int universeSize = members.size();
		for (int i = 0; i < universeSize; i++) {
			int partition = Math.floorMod(hasher.hashLong(iter.next()).asInt(), numPartitions);
			partitions[partition] += 1;
		}

		return partitions;
	}

	@State(Scope.Benchmark)
	public static class BenchmarkExecutionPlan {

		public final int numPartitions = 300;

		@Param({ "10000" })
		public long universeSize;

		public HashFunction murmur3;
		public HashFunction sha256;
		public HashFunction sha512;

		public Set<Long> memberIds;

		@Setup(Level.Trial)
		public void setupBenchmark() {
			this.murmur3 = Hashing.murmur3_32();
			this.sha256 = Hashing.sha256();
			this.sha512 = Hashing.sha512();
			this.memberIds = LongStream.range(0l, universeSize).boxed().collect(Collectors.toSet());

		}
	}

}
