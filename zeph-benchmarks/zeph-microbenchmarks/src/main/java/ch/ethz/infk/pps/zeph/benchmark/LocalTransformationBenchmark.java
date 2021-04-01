package ch.ethz.infk.pps.zeph.benchmark;

import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.apache.kafka.clients.producer.RecordMetadata;
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

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.Window;
import ch.ethz.infk.pps.zeph.client.LocalTransformation;
import ch.ethz.infk.pps.zeph.client.facade.ILocalTransformationFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;

public class LocalTransformationBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.Throughput)
	public void testLocalTransformation(BenchmarkExecutionPlan benchPlan, OpCounter counter) {

		benchPlan.data.forEach((timestamp, input) -> {
			benchPlan.localTransformation.submit(input, timestamp);
			counter.count += 1;
		});

	}

	@State(Scope.Thread)
	@AuxCounters
	public static class OpCounter {
		public int count;
	}

	@State(Scope.Benchmark)
	public static class BenchmarkExecutionPlan {

		@Param({ "10", "100", "1000", "10000", "30000", "60000" })
		public long window_size_ms;

		@Param({ "59000" })
		public long bench_time_ms;

		public TreeMap<Long, Input> data;
		public ClientConfig clientConfig;
		public Universe universe;
		public ILocalTransformationFacade facade;

		public LocalTransformation localTransformation;

		@Setup(Level.Invocation)
		public void setup() {
			this.localTransformation = new LocalTransformation(clientConfig, universe, facade);
			this.localTransformation.init();
		}

		@Setup(Level.Trial)
		public void setupBenchmark() throws NoSuchAlgorithmException {

			clientConfig = new ClientConfig();
			clientConfig.setUniverseId(5l);
			clientConfig.setProducerId(1l);
			clientConfig.setHeacKey(CryptoUtil.generateKey());

			long start = WindowUtil.getWindowStart(System.currentTimeMillis(), window_size_ms);

			Window window = new Window(start, start + window_size_ms);
			this.universe = new Universe(5l, window, null, 0, 0.5, 1.0e-5);

			this.facade = new DummyFacade();

			this.data = new TreeMap<>();

			Random random = new Random(1);
			double mean = 0.3;
			RandomGenerator rng = RandomGeneratorFactory.createRandomGenerator(random);
			ExponentialDistribution expo = new ExponentialDistribution(rng, mean);

			long timestamp = start;

			long end = start + bench_time_ms;
			while (timestamp < end) {
				timestamp += (long) (expo.sample() * 1000) + 1;
				data.put(timestamp, ApplicationAdapter.random());
			}
		}

	}

	public static class DummyFacade implements ILocalTransformationFacade {

		@Override
		public void init(long producerId, long universeId) {

		}

		@Override
		public CompletableFuture<RecordMetadata> sendCiphertext(long timestamp, Digest ciphertext, long heacStart) {
			RecordMetadata metadata = new RecordMetadata(null, 0, 0, 0, null, 0, 0);
			return CompletableFuture.completedFuture(metadata);
		}

		@Override
		public void close() {

		}

	}

}
