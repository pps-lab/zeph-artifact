package ch.ethz.infk.pps.zeph.clientdemo;

import java.io.File;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import ch.ethz.infk.pps.zeph.clientdemo.config.ClientDemoDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.ClientDemoDefinition.Phase;
import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.TestProfileDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.phase.CreateProducerIdentityPhaseRunner;
import ch.ethz.infk.pps.zeph.clientdemo.phase.GenerateSharedKeysPhaseRunner;
import ch.ethz.infk.pps.zeph.clientdemo.phase.RunSystemPhaseRunner;
import ch.ethz.infk.pps.zeph.clientdemo.util.KeyStoreUtil;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.ProducerIdentity;
import ch.ethz.infk.pps.shared.avro.Window;
import ch.ethz.infk.pps.shared.avro.util.CertificateFileStore;
import ch.ethz.infk.pps.shared.avro.util.ProducerKeyFileStore;

public class TestProfileRunner {

	private final static Logger LOG = LogManager.getLogger();
	private static final Marker M = ClientDemo.MARKER;

	private final TestProfileDefinition testProfileDefinition;

	public TestProfileRunner(TestProfileDefinition testProfileDefinition) {
		this.testProfileDefinition = testProfileDefinition;
		processTestProfileDefinition();
	}

	private void processTestProfileDefinition() {
		testProfileDefinition.getUniverses().forEach(uDef -> uDef.unroll());
		testProfileDefinition.getPrivacyControllers().forEach(pcDef -> pcDef.unroll());
		List<ProducerDefinition> pDefs = new ArrayList<>();
		testProfileDefinition.getProducers().forEach(pDef -> {

			if (pDef.getProducerId() != null && pDef.getProducerIdRange() == null) {
				pDefs.add(pDef);
			} else if (pDef.getProducerIdRange().size() == 2 && pDef.getProducerId() == null) {
				long firstProducerId = pDef.getProducerIdRange().get(0);
				long lastProducerId = pDef.getProducerIdRange().get(1);
				LongStream.rangeClosed(firstProducerId, lastProducerId).forEach(pId -> {
					ProducerDefinition copy = pDef.copy();
					copy.setProducerId(pId);
					copy.setProducerIdRange(null);
					pDefs.add(copy);
				});
			} else {
				throw new IllegalArgumentException("failed to unroll producerId range");
			}

		});

		testProfileDefinition.setProducers(pDefs);
	}

	public void run() {

		ClientDemoDefinition clientDemoDef = testProfileDefinition.getClientDemo();
		Set<Phase> phases = clientDemoDef.getPhases();
		ClientDemoPath.BASE = clientDemoDef.getBaseFolder();

		if (phases.contains(Phase.CREATE_PRODUCER_IDENTITY)) {
			CreateProducerIdentityPhaseRunner phaseRunner = new CreateProducerIdentityPhaseRunner(
					testProfileDefinition);
			phaseRunner.run();
		}

		LOG.info(M, "Loading Certificates...");
		File certFile = ClientDemoPath.getCertificatesFile();
		ConcurrentHashMap<Long, Certificate> certificates = new ConcurrentHashMap<>();
		certificates.putAll(CertificateFileStore.load(certFile));

		LOG.info(M, "Loading Producer Configuration (keys)...");
		try {
			char[] ksPwd = testProfileDefinition.getClientDemo().getKeystorePassword().toCharArray();
			String ksPath = ClientDemoPath.getGlobalKeystorePath();

			KeyStore ks = KeyStoreUtil.buildKeyStore(ksPath, ksPwd, "pkcs12");
			SecretKey fileKey = (SecretKey) ks.getKey("local-producers-key", ksPwd);

			File keyFile = ClientDemoPath.getKeysFile();
			Map<Long, ProducerIdentity> infoMap = ProducerKeyFileStore.load(keyFile, fileKey);
			testProfileDefinition.getProducers()
					.forEach(pDef -> pDef.setProducerInfo(infoMap.get(pDef.getProducerId())));

		} catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("failed to load file key from keystore", e);
		}

		if (phases.contains(Phase.GENERATE_SHARED_KEYS)) {
			GenerateSharedKeysPhaseRunner phaseRunner = new GenerateSharedKeysPhaseRunner(testProfileDefinition,
					certificates);
			phaseRunner.run();
		}

		long earliestPossibleStart = System.currentTimeMillis();

		testProfileDefinition.getUniverses().forEach(uDef -> {
			long start = WindowUtil.getWindowStart(earliestPossibleStart, uDef.getWindowSizeMillis());
			Window firstWindow = new Window(start + uDef.getWindowSizeMillis(), start + 2 * uDef.getWindowSizeMillis());
			LOG.info(M, "Universe {}  - First Window: {}", uDef.getUniverseId(), WindowUtil.f(firstWindow));
			uDef.setFirstWindow(firstWindow);
		});

		if (phases.contains(Phase.RUN_SYSTEM)) {
			RunSystemPhaseRunner phaseRunner = new RunSystemPhaseRunner(testProfileDefinition, certificates);
			phaseRunner.run();
		}

	}

}
