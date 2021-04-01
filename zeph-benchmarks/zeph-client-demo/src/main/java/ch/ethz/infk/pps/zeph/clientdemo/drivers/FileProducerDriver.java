package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import java.util.concurrent.CountDownLatch;

import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.UniverseDefinition;

public class FileProducerDriver extends Driver {

	public FileProducerDriver(String profileId, long runId, UniverseDefinition uDef, ProducerDefinition pDef,
			CountDownLatch completionLatch, String kafkaBootstrapServers) {
		pDef.getDataImport();
	}

	@Override
	protected void drive() {
		throw new IllegalStateException("not implemented yet");
	}

}
