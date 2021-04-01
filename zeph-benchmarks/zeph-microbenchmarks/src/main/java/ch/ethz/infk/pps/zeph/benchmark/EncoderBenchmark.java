package ch.ethz.infk.pps.zeph.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import org.openjdk.jmh.infra.Blackhole;

import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.shared.AggregationBasedEncoding;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;

public class EncoderBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testEncode(Data data, Blackhole blackhole) {
		blackhole.consume(encode(data));
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testEncodeEncrypt(Data data, Blackhole blackhole) {
		List<Long> encoding = encode(data);
		List<Long> ciphertext = data.heac.encrypt(data.msgTimestamp, encoding, data.prevTimestamp);
		blackhole.consume(ciphertext);
	}

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testEncryptVectorElement(EncryptionBenchPlan plan, Blackhole blackhole) {
		List<Long> ciphertext = plan.heac.encrypt(plan.msgTimestamp, plan.preEncodedSumVector, plan.prevTimestamp);
		blackhole.consume(ciphertext);
	}

	private List<Long> encode(Data data) {
		List<Long> encoding;
		switch (data.encoding) {
		case AVG:
			encoding = AggregationBasedEncoding.encodeAvg(data.x);
			break;
		case COUNT:
			encoding = AggregationBasedEncoding.encodeCount(data.x);
			break;
		case HIST:
			encoding = AggregationBasedEncoding.encodeHist(data.x, data.bucketBoundaries);
			break;
		case MAX:
			encoding = AggregationBasedEncoding.encodeMax(data.x, data.bucketBoundaries);
			break;
		case MIN:
			encoding = AggregationBasedEncoding.encodeMin(data.x, data.bucketBoundaries);
			break;
		case REG:
			encoding = AggregationBasedEncoding.encodeRegression(data.x, data.y);
			break;
		case STDDEV:
			encoding = AggregationBasedEncoding.encodeStandardDeviation(data.x);
			break;
		case SUM:
			encoding = AggregationBasedEncoding.encodeSum(data.x);
			break;
		case VAR:
			encoding = AggregationBasedEncoding.encodeVariance(data.x);
			break;
		default:
			throw new IllegalStateException("unknown encoding:" + data.encoding);
		}

		return encoding;
	}

	public enum Encoding {
		SUM, COUNT, AVG, VAR, STDDEV, HIST, MIN, MAX, REG
	}

	@State(Scope.Benchmark)
	public static class EncryptionBenchPlan {

		public List<Long> preEncodedSumVector;
		public Heac heac;

		public long msgTimestamp;
		public long prevTimestamp;

		@Setup(Level.Trial)
		public void setupBenchmark() {
			this.msgTimestamp = System.currentTimeMillis();
			this.prevTimestamp = this.msgTimestamp - 1234;
			this.heac = new Heac(CryptoUtil.generateKey());
			this.preEncodedSumVector = AggregationBasedEncoding.encodeSum(511);
		}
	}

	@State(Scope.Benchmark)
	public static class Data {

		@Param({ "sum", "count", "avg", "var", "stddev", "hist_10", "min_10", "max_10", "reg" })
		public String encoderConfig;

		public Encoding encoding;
		public List<Long> bucketBoundaries;

		public Heac heac;

		public long msgTimestamp;
		public long prevTimestamp;

		public long minValue = 0;
		public long maxValue = 1000;
		public long x = 511;
		public long y = 4;

		@Setup(Level.Trial)
		public void setupBenchmark() {
			String[] parts = encoderConfig.split("_");

			if (parts.length > 1) {
				this.encoding = Encoding.valueOf(parts[0].toUpperCase());
			} else {
				this.encoding = Encoding.valueOf(encoderConfig.toUpperCase());
			}

			// setup bucket boundaries for min / max and hist
			if (parts.length > 1) {
				int numBuckets = Integer.parseInt(parts[1]);
				bucketBoundaries = new ArrayList<>(numBuckets - 1);
				long bucketSize = (maxValue - minValue) / numBuckets;
				for (long b = bucketSize; b < maxValue; b += bucketSize) {
					bucketBoundaries.add(b);
				}
			}

			this.msgTimestamp = System.currentTimeMillis();

			this.prevTimestamp = this.msgTimestamp - 1234;

			this.heac = new Heac(CryptoUtil.generateKey());

		}

	}

}
