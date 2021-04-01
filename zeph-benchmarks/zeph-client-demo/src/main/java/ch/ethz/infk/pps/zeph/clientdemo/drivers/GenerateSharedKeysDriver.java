package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.crypto.SecretKey;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.clientdemo.ClientDemo;
import ch.ethz.infk.pps.zeph.clientdemo.ClientDemoPath;
import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.crypto.DiffieHellmanKeyExchange;
import ch.ethz.infk.pps.shared.avro.util.SharedKeyFileStore;

public class GenerateSharedKeysDriver extends Driver {

	private final static Logger LOG = LogManager.getLogger();
	private static final Marker M = ClientDemo.MARKER;

	private final ProducerDefinition pDef;
	private final ConcurrentHashMap<Long, Certificate> certificates;
	private CountDownLatch completionLatch;

	public GenerateSharedKeysDriver(ProducerDefinition pDef, ConcurrentHashMap<Long, Certificate> certificates,
			CountDownLatch completionLatch) {
		this.pDef = pDef;
		this.certificates = certificates;
		this.completionLatch = completionLatch;
	}

	@Override
	protected void drive() {

		long producerId = pDef.getProducerId();
		PrivateKey privateKey = pDef.getProducerInfo().getPrivateKey();

		// load existing shared keys
		SecretKey sharedKeysFileKey = pDef.getProducerInfo().getSharedKeysFileKey();
		ConcurrentHashMap<Long, SecretKey> sharedKeys = new ConcurrentHashMap<>();
		File sharedKeysFile = ClientDemoPath.getSharedKeysFile(producerId);
		if (sharedKeysFile.exists()) {
			Map<Long, SecretKey> existingSharedKeys = SharedKeyFileStore.load(sharedKeysFile, "sk" + producerId,
					sharedKeysFileKey);
			sharedKeys.putAll(existingSharedKeys);
		}

		DiffieHellmanKeyExchange keyExchange = new DiffieHellmanKeyExchange();

		int initialSize = sharedKeys.size();

		LOG.debug(M, "Generating Missing Shared Keys for Producer{}...", producerId);

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		long parallelismThreshold = 128;
		certificates.forEach(parallelismThreshold, (otherProducerId, certificate) -> {
			if (!sharedKeys.containsKey(otherProducerId)) {
				SecretKey sharedKey = keyExchange.generateSharedKey(privateKey, certificate);
				sharedKeys.put(otherProducerId, sharedKey);
			}
		});
		stopWatch.stop();
		long runtime = stopWatch.getTime();
		int count = sharedKeys.size() - initialSize;
		double throughput = (1000d / runtime) * count;

		LOG.info(M, "Generated {} shared keys in {} ms  ({} keys/s)", count, runtime, throughput);

		LOG.debug(M, "Writing Shared Keys for Producer{} to File...", producerId);
		SharedKeyFileStore.store(sharedKeysFile, "sk" + producerId, sharedKeysFileKey, sharedKeys);

		this.completionLatch.countDown();
	}

}
