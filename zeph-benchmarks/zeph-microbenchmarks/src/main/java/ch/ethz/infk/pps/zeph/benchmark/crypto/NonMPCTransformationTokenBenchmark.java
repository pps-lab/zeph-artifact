package ch.ethz.infk.pps.zeph.benchmark.crypto;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.crypto.IHeac;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.shared.avro.Digest;

public class NonMPCTransformationTokenBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testNonMPCTransformationToken(BenchmarkExecutionPlan benchPlan, Blackhole blackhole) {
		Digest transformationToken = benchPlan.heac.getKey(benchPlan.start - 1, benchPlan.end - 1);
		blackhole.consume(transformationToken);
	}

	@State(Scope.Benchmark)
	public static class BenchmarkExecutionPlan {

		public IHeac heac;

		public long windowSizeMillis = Duration.ofHours(4).toMillis();
		public long start;
		public long end;

		@Setup(Level.Trial)
		public void setupBenchmark() throws NoSuchAlgorithmException {

			this.heac = new Heac(CryptoUtil.generateKey());

			this.start = WindowUtil.getWindowStart(System.currentTimeMillis(), windowSizeMillis);
			this.end = this.start + this.windowSizeMillis;
		}

	}

}
