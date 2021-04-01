package ch.ethz.infk.pps.zeph.clientdemo.phase;

import java.io.File;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import ch.ethz.infk.pps.zeph.clientdemo.ClientDemo;
import ch.ethz.infk.pps.zeph.clientdemo.ClientDemoPath;
import ch.ethz.infk.pps.zeph.clientdemo.config.TestProfileDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.util.KeyStoreUtil;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.ProducerIdentity;
import ch.ethz.infk.pps.shared.avro.util.CertificateFileStore;
import ch.ethz.infk.pps.shared.avro.util.ProducerKeyFileStore;

public class CreateProducerIdentityPhaseRunner extends AbstractBenchmarkPhaseRunner {

	private static final Logger LOG = LogManager.getLogger();
	private static final Marker M = ClientDemo.MARKER;

	public CreateProducerIdentityPhaseRunner(TestProfileDefinition testProfileDefinition) {
		super(testProfileDefinition);
		Security.addProvider(new BouncyCastleProvider());
	}

	@Override
	public void run() {

		Set<Long> producerIds = tpDef.getProducers()
				.stream()
				.map(pDef -> pDef.getProducerId())
				.collect(Collectors.toSet());

		char[] ksPwd = tpDef.getClientDemo().getKeystorePassword().toCharArray();

		String ksPath = ClientDemoPath.getGlobalKeystorePath();
		KeyStore ks = KeyStoreUtil.buildKeyStore(ksPath, ksPwd, "pkcs12");

		ConcurrentHashMap<Long, Certificate> globalCertificates = new ConcurrentHashMap<>();
		Map<Long, ProducerIdentity> producers = new ConcurrentHashMap<>();

		File certFile = ClientDemoPath.getCertificatesFile();
		if (certFile.exists()) {
			globalCertificates.putAll(CertificateFileStore.load(certFile));
			LOG.info(M, "Loaded {} (global) certificates from: {}", globalCertificates.size(),
					certFile.getPath());
		}

		try {
			SecretKey fileKey = (SecretKey) ks.getKey("local-producers-key", ksPwd);
			File keyFile = ClientDemoPath.getKeysFile();
			if (keyFile.exists()) {
				producers.putAll(ProducerKeyFileStore.load(keyFile, fileKey));
				LOG.info(M, "Loaded {} producers from: {}", producers.size(), keyFile.getPath());

			} else if (fileKey == null) {
				fileKey = CryptoUtil.generateKey();

				ks.setEntry("local-producers-key", new KeyStore.SecretKeyEntry(fileKey),
						new KeyStore.PasswordProtection(ksPwd));

				KeyStoreUtil.flushKeyStore(ks, ksPath, ksPwd);
				LOG.info(M, "Generated new local-producers-key and updated KeyStore: " + ksPath);
			}

			int initialSize = producers.size();

			// generate missing keys
			producerIds.parallelStream()
					.filter(pId -> !producers.containsKey(pId)) // find the missing producers
					.forEach(pId -> {

						if (globalCertificates.containsKey(pId)) {
							throw new IllegalArgumentException(
									"certificate for this producer id exists in global certificates list");
						}

						ProducerIdentity identity = ProducerIdentity.generate(pId);
						globalCertificates.put(pId, identity.getCertificate());
						producers.put(pId, identity);
					});

			// store the local keys
			ProducerKeyFileStore.store(keyFile, fileKey, producers);

			// store the global certificates
			CertificateFileStore.store(certFile, globalCertificates);
			LOG.info(M, "Generated {} new producers and updated files: {} {}", producers.size() - initialSize,
					keyFile.getPath(), certFile.getPath());

		} catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Failed to update KeyStore", e);
		}

	}

}
