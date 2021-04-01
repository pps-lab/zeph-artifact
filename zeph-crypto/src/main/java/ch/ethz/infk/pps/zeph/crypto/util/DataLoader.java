package ch.ethz.infk.pps.zeph.crypto.util;

import java.io.File;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.crypto.DiffieHellmanKeyExchange;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.zeph.shared.pojo.ProducerIdentity;
import ch.ethz.infk.pps.shared.avro.util.CertificateFileStore;
import ch.ethz.infk.pps.shared.avro.util.ProducerKeyFileStore;
import ch.ethz.infk.pps.shared.avro.util.SharedKeyFileStore;

public class DataLoader {

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	public static void main(String[] args) {

		int size = 10000;

		Set<Long> identitiesIds = LongStream.rangeClosed(1, size).boxed().collect(Collectors.toSet());

		// String path = "C:/Development/mthesis-kuechler/data/benchmark";
		String path = "/home/kuenico/dev/zeph/newdata";

		File f = new File(path);

		Pattern r = Pattern.compile("shared-keys(\\d+).avro");

		File[] files = f.listFiles();
		System.out.println(files.length);

		for (File file : files) {
			Matcher m = r.matcher(file.getName());
			if (m.find()) {
				identitiesIds.remove(Long.parseLong(m.group(1)));
			}

		}
		System.out.println(identitiesIds.size());

		Set<Long> nodeIds = LongStream.rangeClosed(1, size).boxed().collect(Collectors.toSet());
		Map<Long, ProducerIdentity> identities = createAndLoadProducers(identitiesIds, null, path, true);
		System.out.println("Created Identities");
		Map<Long, Certificate> certificates = createAndLoadCertificates(nodeIds, identities, path, true);
		System.out.println("Created Certificates");

		ConcurrentHashMap<Long, ProducerIdentity> data = new ConcurrentHashMap<>();
		data.putAll(identities);

		System.out.print("<");
		data.forEach(1, (pId, identity) -> {
			createAndLoadSharedKeys(Collections.singletonMap(pId, identity), certificates, path, null, true);
			if (pId % 50 == 0) {
				System.out.print("=");
			}
		});
		System.out.println(">");
	}

	public static Map<ImmutableUnorderedPair<Long>, SecretKey> createAndLoadSharedKeys(Collection<Long> myNodeIds,
			Collection<Long> nodeIds, String path, SecretKey fileKey, boolean useSeed) {

		Map<Long, ProducerIdentity> producers = createAndLoadProducers(myNodeIds, fileKey, path, useSeed);
		Map<Long, Certificate> certificates = createAndLoadCertificates(nodeIds, producers, path, useSeed);

		return createAndLoadSharedKeys(producers, certificates, path, fileKey, useSeed);
	}

	public static Map<ImmutableUnorderedPair<Long>, SecretKey> createAndLoadSharedKeys(
			Map<Long, ProducerIdentity> identities, Map<Long, Certificate> otherIdentities, String path,
			SecretKey fileKey, boolean useSeed) {

		Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = new HashMap<>();

		identities.forEach((nodeId, identity) -> {

			// load shared keys and generate missing
			File sharedKeysFile = new File(path + "/shared-keys" + nodeId + ".avro");

			String keyId = "sk" + nodeId;
			Map<Long, SecretKey> producerSharedKeys = new HashMap<>();
			if (sharedKeysFile.exists()) {
				producerSharedKeys.putAll(SharedKeyFileStore.load(sharedKeysFile, keyId, fileKey));
			}

			final DiffieHellmanKeyExchange keyExchange = new DiffieHellmanKeyExchange();
			final PrivateKey privateKey = identity.getPrivateKey();

			otherIdentities.entrySet().stream()
					.filter(e -> !e.getKey().equals(nodeId) && !producerSharedKeys.containsKey(e.getKey()))
					.forEach(e -> {
						SecretKey sharedKey = keyExchange.generateSharedKey(privateKey, e.getValue());
						producerSharedKeys.put(e.getKey(), sharedKey);
					});
			SharedKeyFileStore.store(sharedKeysFile, keyId, fileKey, producerSharedKeys);

			sharedKeys.putAll(otherIdentities.entrySet().stream()
					.filter(e -> !e.getKey().equals(nodeId))
					.collect(Collectors.toMap(e -> ImmutableUnorderedPair.of(e.getKey(), nodeId),
							e -> producerSharedKeys.get(e.getKey()))));
		});

		return sharedKeys;

	}

	public static Map<Long, Certificate> createAndLoadCertificates(Collection<Long> nodeIds,
			Map<Long, ProducerIdentity> identities, String path, boolean useSeed) {
		if (!useSeed) {
			throw new IllegalArgumentException("only supported with seed");
		}

		Map<Long, Certificate> certificates = new HashMap<>();

		File certFile = new File(path + "/certificates.avro");
		if (certFile.exists()) {
			certificates.putAll(CertificateFileStore.load(certFile));
		}

		identities.forEach((pId, identity) -> certificates.put(pId, identity.getCertificate()));

		nodeIds.stream()
				.filter(pId -> !certificates.containsKey(pId))
				.forEach(pId -> {
					byte[] seed = Longs.toByteArray(pId);
					Certificate cert = CryptoUtil.generateCertificate(CryptoUtil.generateKeyPair(seed), pId);
					certificates.put(pId, cert);
				});

		CertificateFileStore.store(certFile, certificates);

		return nodeIds.stream().collect(Collectors.toMap(pId -> pId, pId -> certificates.get(pId)));
	}

	public static Map<Long, ProducerIdentity> createAndLoadProducers(Collection<Long> nodeIds, SecretKey fileKey,
			String path, boolean useSeed) {
		// load producer identities and create missing
		Map<Long, ProducerIdentity> producers = new HashMap<>();
		File producerIdentityFile = new File(path + "/producers.avro");
		if (producerIdentityFile.exists()) {
			producers.putAll(ProducerKeyFileStore.load(producerIdentityFile, fileKey));
		} else {
			producerIdentityFile.getParentFile().mkdirs();
		}

		nodeIds.stream()
				.filter(id -> !producers.containsKey(id))
				.forEach(id -> {
					if (useSeed) {
						byte[] seed = Longs.toByteArray(id);
						ProducerIdentity identity = ProducerIdentity.generate(id, seed);
						producers.put(id, identity);
					} else {
						ProducerIdentity identity = ProducerIdentity.generate(id);
						producers.put(id, identity);
					}

				});

		ProducerKeyFileStore.store(producerIdentityFile, fileKey, producers);

		return nodeIds.stream().collect(Collectors.toMap(pId -> pId, pId -> producers.get(pId)));
	}

	public static Map<Long, ProducerIdentity> loadProducers(Collection<Long> nodeIds, SecretKey fileKey, String path) {
		File producerIdentityFile = new File(path + "/producers.avro");
		Map<Long, ProducerIdentity> producers = ProducerKeyFileStore.load(producerIdentityFile, fileKey);

		nodeIds.stream().filter(pId -> !producers.containsKey(pId)).findAny().ifPresent(x -> {
			throw new IllegalStateException(
					"not all producers exist: pId=" + x + "    nodeIds=" + nodeIds + "  path=" + path);
		});

		return nodeIds.stream().collect(Collectors.toMap(pId -> pId, pId -> producers.get(pId)));
	}

	public static Map<Long, Certificate> loadCertificates(Collection<Long> nodeIds,
			Map<Long, ProducerIdentity> identities, String path) {

		File certFile = new File(path + "/certificates.avro");
		Map<Long, Certificate> certificates = CertificateFileStore.load(certFile);

		nodeIds.stream().filter(pId -> !certificates.containsKey(pId)).findAny().ifPresent(x -> {
			throw new IllegalStateException("not all certificates exist");
		});

		return nodeIds.stream().collect(Collectors.toMap(pId -> pId, pId -> certificates.get(pId)));

	}

	public static Map<ImmutableUnorderedPair<Long>, SecretKey> loadSharedKeys(Collection<Long> myNodeIds,
			Collection<Long> nodeIds, String path, SecretKey fileKey) {

		Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = new HashMap<>();

		myNodeIds.forEach(nodeId -> {

			// load shared keys and generate missing
			File sharedKeysFile = new File(path + "/shared-keys" + nodeId + ".avro");

			String keyId = "sk" + nodeId;
			Map<Long, SecretKey> producerSharedKeys = SharedKeyFileStore.load(sharedKeysFile, keyId, fileKey);

			sharedKeys.putAll(nodeIds.stream()
					.filter(e -> !e.equals(nodeId))
					.collect(Collectors.toMap(e -> ImmutableUnorderedPair.of(e, nodeId),
							e -> producerSharedKeys.get(e))));

		});

		if (sharedKeys.containsValue(null)) {
			throw new IllegalStateException("not all shared keys exist");
		}

		return sharedKeys;

	}
}
