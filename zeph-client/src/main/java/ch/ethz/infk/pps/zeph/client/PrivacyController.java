package ch.ethz.infk.pps.zeph.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;

import javax.crypto.SecretKey;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import ch.ethz.infk.pps.zeph.client.facade.IPrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.client.util.ClientConfig;
import ch.ethz.infk.pps.zeph.client.util.PrivacyState;
import ch.ethz.infk.pps.zeph.client.util.PrivacyState.PrivacyConfig;
import ch.ethz.infk.pps.zeph.client.util.UniverseState;
import ch.ethz.infk.pps.zeph.client.util.UniverseState.UniverseConfig;
import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationNative;
import ch.ethz.infk.pps.zeph.crypto.util.ErdosRenyiUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.Window;

public class PrivacyController implements Runnable {

	private static final Logger LOG = LogManager.getLogger();

	private final IPrivacyControllerFacade facade;
	private final Map<Long, SecureAggregationNative> secureAggregation = new HashMap<>(); // <universeId,...>

	private Map<Long, CompletableFuture<UniverseState>> universeChains = new HashMap<>();
	private Map<Long, CompletableFuture<PrivacyState>> producerChains = new HashMap<>();
	private Multimap<Long, Long> assignedProducers = HashMultimap.create();

	private Map<Long, Long> stagedUniverseBoundary = new HashMap<>();
	private Map<Long, Long> mergedUniverseBoundary = new HashMap<>();

	private boolean isShutdownRequested = false;

	public PrivacyController(IPrivacyControllerFacade facade) {
		this.facade = facade;
	}

	public PrivacyController(List<ClientConfig> clientConfigs, Map<Long, Universe> universes,
			IPrivacyControllerFacade facade) {
		this(facade);
		universes.forEach((universeId, universe) -> initUniverse(universeId, universe));
		clientConfigs.forEach(config -> addAssignment(config.getUniverseId(), config));
	}

	public void addSharedKeys(long universeId, Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys) {
		this.secureAggregation.get(universeId).addSharedKeys(sharedKeys);
	}

	public Multimap<Long, Long> getAssignedProducers() {
		return assignedProducers;
	}

	public void initUniverse(long universeId, Universe universe) {
		UniverseState universeState;

		Window firstWindow = universe.getFirstWindow();
		long windowSize = firstWindow.getEnd() - firstWindow.getStart();

		Integer k = ErdosRenyiUtil.getK(universe.getMinimalSize(), universe.getAlpha(), universe.getDelta());
		if (k != null) {
			// perform ER secure aggregation
			int W = ErdosRenyiUtil.getNumberOfGraphs(k);
			long epochSize = W * windowSize;
			UniverseConfig config = new UniverseConfig(windowSize, k, epochSize);
			universeState = new UniverseState(config);
			universeState.setMembers(Collections.emptySet());
			universeState.setEpochs(new ConcurrentHashMap<>());
			LOG.info("Using ER Secure Aggregation for universe {} with k={}", universeId, k);

		} else {
			// perform standard secure aggregation
			UniverseConfig config = new UniverseConfig(windowSize);
			universeState = new UniverseState(config);
			universeState.setMembers(Collections.emptySet());

			LOG.info("Using Standard Secure Aggregation for universe {}", universeId);
		}

		universeChains.put(universeId, CompletableFuture.completedFuture(universeState));

		this.stagedUniverseBoundary.put(universeId, firstWindow.getStart());
		this.mergedUniverseBoundary.put(universeId, firstWindow.getStart());

		this.secureAggregation.put(universeId, new SecureAggregationNative());

	}

	public void addAssignment(long universeId, ClientConfig clientConfig) {

		long producerId = clientConfig.getProducerId();
		Heac heac = new Heac(clientConfig.getHeacKey());

		PrivacyConfig privacyConfig = new PrivacyConfig(heac);
		PrivacyState privacyState = new PrivacyState(privacyConfig);
		producerChains.put(producerId, CompletableFuture.completedFuture(privacyState));

		assignedProducers.put(universeId, producerId);

		if (clientConfig.getSharedKeys() != null) {
			addSharedKeys(universeId, clientConfig.getSharedKeys());
		}
	}

	@Override
	public void run() {

		this.facade.init(universeChains.keySet());

		try {
			while (true) {

				ConsumerRecords<WindowedUniverseId, DeltaUniverseState> records = facade.pollInfos();

				if (isShutdownRequested) {
					break;
				}

				records.forEach(record -> {
					WindowedUniverseId windowedUniverseId = record.key();
					DeltaUniverseState windowState = record.value();

					int statusCode = windowState.getStatus();
					WindowStatus status = WindowStatus.of(statusCode);

					switch (status) {
					case STAGED:
						onStatusStaged(windowedUniverseId, windowState);
						break;
					case MERGED:
						onStatusMerged(windowedUniverseId, windowState);
						break;
					default:
						// do nothing for other status
						break;
					}
				});
			}

		} catch (WakeupException e) {
			// ignore for shutdown
		} finally {
			facade.close();
		}
	}

	public void requestShutdown() {
		isShutdownRequested = true;
	}

	private void onStatusStaged(WindowedUniverseId windowedUniverseId, DeltaUniverseState state) {
		final long universeId = windowedUniverseId.getUniverseId();
		final Window window = windowedUniverseId.getWindow();

		if (window.getEnd() < stagedUniverseBoundary.get(universeId)) {
			// do not send commit for old window
			LOG.debug("{} - u={} skipped sending commits (boundary)", () -> WindowUtil.f(window), () -> universeId);
			return;
		}

		Collection<Long> producerIds = assignedProducers.get(universeId);

		CompletableFuture<Void> allCf = CompletableFuture.allOf(producerIds.stream()
				.map(pId -> facade.sendCommit(pId, window, universeId)).toArray(CompletableFuture[]::new));

		allCf.thenAcceptAsync(
				x -> LOG.debug("{} - u={} sent all commits", () -> WindowUtil.f(window), () -> universeId));

		stagedUniverseBoundary.put(universeId, window.getEnd());
	}

	private void onStatusMerged(WindowedUniverseId windowedUniverseId, DeltaUniverseState deltaState) {

		final long universeId = windowedUniverseId.getUniverseId();
		final Window window = windowedUniverseId.getWindow();

		// update universe state (in order)
		CompletableFuture<UniverseState> universeChain = universeChains.get(universeId);
		universeChain = universeChain.thenApplyAsync(oldState -> {
			UniverseState state;
			if (oldState.isER()) {
				state = updateUniverseStateER(universeId, window, oldState, deltaState);
			} else {
				state = updateUniverseState(universeId, window, oldState, deltaState);
			}
			return state;
		}).exceptionally(e -> {
			throw new IllegalStateException("failed to update universe state", e);
		});
		universeChains.put(universeId, universeChain);

		if (window.getEnd() < mergedUniverseBoundary.get(universeId)) {
			// do not send tokens for old window
			LOG.debug("{} - u={} skipped sending token (boundary)", WindowUtil.f(window), universeId);
			return;
		}

		// for each assigned user: update privacy state (in order) and and build token
		Collection<Long> producerIds = assignedProducers.get(universeId);

		universeChain.thenApplyAsync(universeState -> {

			if (universeState.isER()) {

				if (universeState.isEpochChange()) {

					Long clearEpoch = universeState.getClearEpoch();
					if (clearEpoch != null && clearEpoch != -1) {
						// mark that the move to next epoch arrived
						universeState.getEpochs().get(clearEpoch).arrive();
					}

					// init new epoch
					long newEpoch = universeState.getEpoch();
					Phaser phaser = new Phaser() {

						private long epoch = newEpoch;
						private Collection<Long> nodeIds = assignedProducers.get(universeId);

						@Override
						protected boolean onAdvance(int phase, int registeredParties) {
							secureAggregation.get(universeId).clearEpochNeighbourhoodsER(epoch, nodeIds);
							universeState.getEpochs().remove(epoch);
							return true;
						}

					};
					phaser.register();
					phaser.register();
					universeState.getEpochs().put(newEpoch, phaser);

					secureAggregation.get(universeId).buildEpochNeighbourhoodsER(universeState.getEpoch(),
							assignedProducers.get(universeId),
							universeState.getMembers(), universeState.getK());
				} else {
					universeState.getEpochs().get(universeState.getEpoch()).register();
				}
			}

			// adjust existing epoch with new members (of this window)
			universeState.getNewMembers().ifPresent(newMembers -> {
				if (!newMembers.isEmpty()) {
					secureAggregation.get(universeId).buildEpochNeighbourhoodsER(universeState.getEpoch(), producerIds,
							newMembers, universeState.getK());
				}
			});

			return universeState;
		}

		).exceptionally(e -> {
			throw new IllegalStateException("failed ", e);
		}).thenComposeAsync(universeState -> CompletableFuture.allOf(producerIds.stream().map(pId -> {

			CompletableFuture<PrivacyState> privacyState = producerChains.get(pId);
			privacyState = privacyState.thenApplyAsync(oldState -> {
				PrivacyState state = updatePrivacyState(pId, oldState, universeState);
				return state;
			});
			producerChains.put(pId, privacyState);

			return privacyState.thenAcceptAsync(state -> {
				Digest token = getTransformationToken(pId, universeId, window, universeState, state);
				facade.sendTransformationToken(pId, window, token, universeId);
				// return;
			});

		}).toArray(CompletableFuture[]::new)).thenApply(v -> universeState)

		).exceptionally(e -> {
			throw new IllegalStateException("failed to update and build tokens", e);
		}).thenAcceptAsync(universeState -> {
			// mark that all tokens were built
			universeState.getEpochs().get(universeState.getEpoch()).arrive();
			LOG.debug("{} - u={} built all tokens", () -> WindowUtil.f(window), () -> universeId);
		});

	}

	public static UniverseState updateUniverseStateER(long universeId, Window window, UniverseState old,
			DeltaUniverseState delta) {

		long windowStart = window.getStart();
		long epoch = WindowUtil.getWindowStart(windowStart, old.getEpochSize());
		short t = (short) ((windowStart - epoch) / (double) old.getWindowSize());

		UniverseState universeState = new UniverseState(epoch, t, old.getUniverseConfig());

		universeState.setEpochs(old.getEpochs());

		boolean newEpoch = epoch != old.getEpoch();

		int codeNew = 1;
		int codeDropped = -1;

		if (newEpoch) {
			// adjust members
			Set<Long> members = new HashSet<>(old.getMembers());

			// add members which arrived during last epoch to members
			old.getNewMembersCummulative().ifPresent(newMembers -> newMembers.forEach(pId -> members.add(pId)));

			// remove members which were not present at the end of prev epoch from members
			old.getDroppedMembersCummulative()
					.ifPresent(droppedMembers -> droppedMembers.forEach(pId -> members.remove(pId)));

			// process the last member diff
			delta.getMemberDiff().forEach((pIdStr, code) -> {
				long pId = Long.parseLong(pIdStr);
				if (code == codeNew) {
					members.add(pId);
				} else if (code == codeDropped) {
					members.remove(pId);
				} else {
					throw new IllegalArgumentException("unknown code: " + code);
				}
			});

			universeState.setMembers(members);
			universeState.markNewEpoch(old.getEpoch());

		} else {

			Set<Long> members = old.getMembers();

			boolean droppedHasChange = false;
			Set<Long> droppedMembersCummulative = old.getDroppedMembersCummulative().orElse(Collections.emptySet());

			boolean newHasChange = false;
			Set<Long> newMembersCummulative = old.getNewMembersCummulative().orElse(Collections.emptySet());

			Set<Long> newMembers = new HashSet<>();

			Iterator<Entry<String, Integer>> iter = delta.getMemberDiff().entrySet().iterator();
			int diffSize = delta.getMemberDiff().size();

			for (int i = 0; i < diffSize; i++) {
				Entry<String, Integer> e = iter.next();
				long pId = Long.parseLong(e.getKey());
				long code = e.getValue();

				if (code == codeNew && droppedMembersCummulative.contains(pId)) {
					if (!droppedHasChange) {
						// only copy immutable map if needed
						droppedMembersCummulative = new HashSet<>(droppedMembersCummulative);
						droppedHasChange = true;
					}
					droppedMembersCummulative.remove(pId);
				} else if (code == codeNew) {
					if (!newHasChange) {
						// only copy immutable map if needed
						newMembersCummulative = new HashSet<>(newMembersCummulative);
						newHasChange = true;
					}
					newMembers.add(pId);
					newMembersCummulative.add(pId);
				} else if (code == codeDropped && (members.contains(pId) || newMembersCummulative.contains(pId))) {
					if (!droppedHasChange) {
						// only copy immutable map if needed
						droppedMembersCummulative = new HashSet<>(droppedMembersCummulative);
						droppedHasChange = true;
					}
					droppedMembersCummulative.add(pId);
				} else {
					LOG.warn("{} - unhandled member diff p={} code={}", WindowUtil.f(window), pId, code);
				}
			}

			universeState.setMembers(members);
			universeState.setDroppedMembersCummulative(droppedMembersCummulative);
			universeState.setNewMembers(newMembers);
			universeState.setNewMembersCummulative(newMembersCummulative);
		}

		return universeState;
	}

	private UniverseState updateUniverseState(long universeId, Window window, UniverseState old,
			DeltaUniverseState delta) {

		UniverseState universeState = new UniverseState(old.getUniverseConfig());

		Set<Long> members = new HashSet<>(old.getMembers());

		int codeNew = 1;
		int codeDropped = -1;

		Map<String, Integer> memberDiff = delta.getMemberDiff();
		memberDiff.forEach((pIdStr, code) -> {
			long pId = Long.parseLong(pIdStr);

			if (code == codeNew) {
				members.add(pId);
			} else if (code == codeDropped) {
				members.remove(pId);
			} else {
				throw new IllegalArgumentException("unknown status code: " + code);
			}

		});

		universeState.setMembers(members);

		return universeState;
	}

	private PrivacyState updatePrivacyState(long producerId, PrivacyState old, UniverseState universeState) {
		// here would subtract privacy budget and check if compatible with privacy policy
		PrivacyState privacyState = new PrivacyState(old.getPrivacyConfig());

		return privacyState;
	}

	private Digest getTransformationToken(long producerId, long universeId, Window window, UniverseState universeState,
			PrivacyState privacyState) {

		Digest dummyKeySum;
		if (universeState.isER()) {
			Set<Long> dropped = universeState.getDroppedMembersCummulative().orElse(Collections.emptySet());
			dummyKeySum = secureAggregation.get(universeId).getDummyKeySumER(window.getStart(),
					universeState.getEpoch(), universeState.getT(), producerId, dropped);
		} else {
			Set<Long> producerIds = universeState.getMembers();
			dummyKeySum = secureAggregation.get(universeId).getDummyKeySum(window.getStart(), producerId, producerIds);
		}

		Digest heacKey = privacyState.getHeac().getKey(window.getStart() - 1, window.getEnd() - 1);
		Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);
		return transformationToken;
	}
}
