package ch.ethz.infk.pps.zeph.benchmark.macro.e2e;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.RandomGeneratorFactory;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.zeph.client.IProducer;

public class ProducerDriver implements IProducerDriver {

	public static void main(String[] args) {

		double expoDelayMean = 0.5;

		int seed = 0;
		Random random = new Random(seed);
		RandomGenerator rng = RandomGeneratorFactory.createRandomGenerator(random);
		ExponentialDistribution delayDistribution = new ExponentialDistribution(rng, expoDelayMean);
		long cum = 0;
		System.out.println("Start");
		for (double delay : delayDistribution.sample(1000)) {
			long d = (long) (delay * 1000);
			cum += d;
			System.out.println(d + ";" + cum);
		}
	}

	private IProducer producer;
	private ScheduledExecutorService executorService;

	private Random random;
	private ExponentialDistribution delayDistribution;
	private boolean isShutdownRequested = false;

	long timestamp = -1;

	public ProducerDriver(IProducer producer, ScheduledExecutorService executorService, long seed,
			double expoDelayMean) {
		this.producer = producer;
		this.executorService = executorService;

		this.random = new Random(seed);
		RandomGenerator rng = RandomGeneratorFactory.createRandomGenerator(random);
		this.delayDistribution = new ExponentialDistribution(rng, expoDelayMean);
	}

	@Override
	public Void call() throws Exception {
		try {
			Input value = ApplicationAdapter.random();

			timestamp = Math.max(timestamp + 1, System.currentTimeMillis());
			producer.submit(value, timestamp);
		} finally {
			if (!isShutdownRequested) {
				long delay = (long) (delayDistribution.sample() * 1000);
				executorService.schedule(this, delay, TimeUnit.MILLISECONDS);
			}
		}

		return null;
	}

	@Override
	public void init() {
		this.producer.init();
	}

	@Override
	public void requestShutdown() {
		isShutdownRequested = true;
	}

}
