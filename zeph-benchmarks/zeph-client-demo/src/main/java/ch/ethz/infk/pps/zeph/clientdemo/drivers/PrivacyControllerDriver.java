package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import java.io.File;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import ch.ethz.infk.pps.zeph.client.PrivacyController;
import ch.ethz.infk.pps.zeph.client.facade.IPrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.client.facade.PrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.clientdemo.ClientDemoPath;
import ch.ethz.infk.pps.zeph.clientdemo.config.PrivacyControllerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.ProducerDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.UniverseDefinition;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutablePair;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.util.FileStoreNames;
import ch.ethz.infk.pps.shared.avro.util.SharedKeyFileStore;

public class PrivacyControllerDriver extends Driver {

	private PrivacyController privacyController;

	public PrivacyControllerDriver(String profileId, long runId, PrivacyControllerDefinition cDef,
			Map<Long, UniverseDefinition> uDefs, Map<Long, ProducerDefinition> pDefs,
			ConcurrentHashMap<Long, Certificate> certificates, String kafkaBootstrapServers) {

		Map<Long, Long> universeAssignment = uDefs.values().stream().flatMap(uDef -> {
			return uDef.getUnrolledMembers().stream().map(pId -> ImmutablePair.of(pId, uDef.getUniverseId()));
		}).collect(Collectors.toMap(pair -> pair.getLeft(), pair -> pair.getRight()));

		Map<Long, Universe> universes = uDefs.values().stream()
				.map(uDef -> new Universe(uDef.getUniverseId(), uDef.getFirstWindow(),
						new ArrayList<>(uDef.getUnrolledMembers()),
						uDef.getMemberThreshold(),
						uDef.getAlpha(),
						uDef.getDelta()))
				.collect(Collectors.toMap(u -> u.getUniverseId(), u -> u));

		List<ClientConfig> clientConfigs = new ArrayList<>();

		cDef.getUnrolledMembers().forEach(pId -> {
			ProducerDefinition pDef = pDefs.get(pId);

			long universeId = universeAssignment.get(pId);

			ClientConfig config = new ClientConfig();
			config.setProducerId(pId);
			config.setUniverseId(universeId);
			config.setHeacKey(pDef.getProducerInfo().getHeacKey());
			config.setPrivateKey(pDef.getProducerInfo().getPrivateKey());

			File sharedKeysFile = ClientDemoPath.getSharedKeysFile(pId);
			Map<Long, SecretKey> sharedKeys = SharedKeyFileStore.load(sharedKeysFile,
					FileStoreNames.getSharedKeyId(pId), pDef.getProducerInfo().getSharedKeysFileKey());

			sharedKeys.remove(pId);
			config.setSharedKeys(sharedKeys.entrySet().stream()
					.collect(Collectors.toMap(e -> ImmutableUnorderedPair.of(e.getKey(), pId), e -> e.getValue())));

			clientConfigs.add(config);
		});

		IPrivacyControllerFacade facade = new PrivacyControllerFacade(cDef.getControllerId(),
				kafkaBootstrapServers, Duration.ofMillis(cDef.getPollTimeoutMillis()));

		this.privacyController = new PrivacyController(clientConfigs, universes, facade);

	}

	@Override
	protected void drive() {
		this.privacyController.run();
	}

	@Override
	public void requestShutdown() {
		super.requestShutdown();
		this.privacyController.requestShutdown();
	}

}
