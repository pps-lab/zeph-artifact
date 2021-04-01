package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.RandomGeneratorFactory;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.zeph.client.LocalTransformation;
import ch.ethz.infk.pps.zeph.client.facade.ILocalTransformationFacade;
import ch.ethz.infk.pps.zeph.client.facade.LocalTransformationFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.UniverseDefinition;

public class RandomProducerDriver extends Driver {

	private CountDownLatch completionLatch;

	private final LocalTransformation transformation;

	private final UniverseDefinition uDef;
	private final ProducerDefinition pDef;
	private final ExponentialDistribution exponentialDistribution;
	private final UniformRealDistribution uniformDistribution;

	public RandomProducerDriver(String profileId, long runId, UniverseDefinition uDef,
			ProducerDefinition pDef, CountDownLatch completionLatch, String kafkaBootstrapServers) {

		this.completionLatch = completionLatch;

		long producerId = pDef.getProducerId();
		long universeId = uDef.getUniverseId();

		this.uDef = uDef;
		this.pDef = pDef;

		RandomGenerator rng = RandomGeneratorFactory.createRandomGenerator(new Random());
		final double mean = 1.0 / pDef.getRandomDataExponentialDelayLambda();
		this.exponentialDistribution = new ExponentialDistribution(rng, mean);
		this.uniformDistribution = new UniformRealDistribution(rng, 0.0, 1.0);

		Universe universe = new Universe(universeId, uDef.getFirstWindow(), new ArrayList<>(uDef.getUnrolledMembers()),
				uDef.getMemberThreshold(), uDef.getAlpha(), uDef.getDelta());

		ClientConfig config = new ClientConfig();
		config.setProducerId(producerId);
		config.setUniverseId(universeId);
		config.setHeacKey(pDef.getProducerInfo().getHeacKey());

		ILocalTransformationFacade facade = new LocalTransformationFacade(kafkaBootstrapServers);
		this.transformation = new LocalTransformation(config, universe, facade);
		this.transformation.init();
	}

	@Override
	protected void drive() {

		boolean hasSendDelay = true;
		boolean hasSilentTime = true;

		final double silentProb = pDef.getSilentProb();
		final long silentTime = pDef.getSilentTimeSec() * 1000;


		final long end = uDef.getFirstWindow().getStart() + pDef.getTestTimeSec() * 1000;

		try {
			long timestamp = System.currentTimeMillis();
			Thread.sleep(Math.max(0, uDef.getFirstWindow().getStart() - timestamp));

			while (timestamp < end) {

				if (isShutdownRequested()) {
					break;
				}

				if (hasSendDelay) {
					long sendDelayMillis = (long) (exponentialDistribution.sample() * 1000);
					Thread.sleep(sendDelayMillis);
				}

				Input input = ApplicationAdapter.random();
				long prevTimestamp = timestamp;
				timestamp = System.currentTimeMillis();
				if (timestamp <= prevTimestamp) {
					timestamp = prevTimestamp + 1;
				}
				transformation.submit(input, timestamp);

				if (hasSilentTime && uniformDistribution.sample() < silentProb) {
					Thread.sleep(silentTime);
					timestamp = System.currentTimeMillis();
				}
			}

			transformation.submitHeartbeat(timestamp + 10);
			transformation.close();

		} catch (InterruptedException e) {
			log.error(M, "Producer interrupted.");
		} finally {
			log.debug(M, "Producer closed");
			completionLatch.countDown();
		}
	}
}
