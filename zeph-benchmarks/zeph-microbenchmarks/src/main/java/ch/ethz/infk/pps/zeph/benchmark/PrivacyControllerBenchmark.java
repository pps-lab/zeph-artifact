package ch.ethz.infk.pps.zeph.benchmark;

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.crypto.SecretKey;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import ch.ethz.infk.pps.zeph.client.PrivacyController;
import ch.ethz.infk.pps.zeph.client.util.UniverseState;
import ch.ethz.infk.pps.zeph.client.util.UniverseState.UniverseConfig;
import ch.ethz.infk.pps.zeph.crypto.Heac;
import ch.ethz.infk.pps.zeph.crypto.IHeac;
import ch.ethz.infk.pps.zeph.crypto.SecureAggregationNative;
import ch.ethz.infk.pps.zeph.crypto.util.DataLoader;
import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class PrivacyControllerBenchmark {

	@Benchmark
	@Fork(value = 1, warmups = 1)
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	public void testUniverseDelta(Blackhole blackhole, BenchmarkExecutionPlan benchPlan) {

		// apply delta universe state to universe state
		UniverseState universeState = PrivacyController.updateUniverseStateER(benchPlan.universeId, benchPlan.window,
				benchPlan.baseUniverseState, benchPlan.deltaState);

		// add the new members
		universeState.getNewMembers().ifPresent(newMembers -> {
			if (!newMembers.isEmpty()) {
				benchPlan.secureAggregation.buildEpochNeighbourhoodsER(universeState.getEpoch(), benchPlan.nodeIds,
						newMembers, universeState.getK());
			}
		});

		// get dropped members
		Set<Long> dropped = universeState.getDroppedMembersCummulative().orElse(Collections.emptySet());

		// generate token
		Digest dummyKeySum = benchPlan.secureAggregation.getDummyKeySumER(benchPlan.window.getStart(),
				universeState.getEpoch(), universeState.getT(), benchPlan.producerId, dropped);

		Digest heacKey = benchPlan.heac.getKey(benchPlan.window.getStart() - 1, benchPlan.window.getEnd() - 1);
		Digest transformationToken = DigestOp.subtract(dummyKeySum, heacKey);

		blackhole.consume(transformationToken);

	}

	@State(Scope.Benchmark)
	public static class BenchmarkExecutionPlan {

		@Param({ "/data" })
		public String dataDir;

		@Param({ "1000" })
		public int universeSize;

		@Param({ "0_0_0_0" })
		public String addE_addNE_dropE_dropNE;

		public final long windowSizeMillis = 10000l;
		public final long windowStart = 15040000l;
		public final long epoch = 10240000l;
		public final short t = 480;
		public int k = 4;
		private long epochSize = 5120000;

		public final long producerId = 1;
		public final long universeId = 5l;

		// in epoch=10240000 t=480, in a universe of size of 1000, the producer with id
		// 1 has these edges
		private Set<Integer> edges = Set.of(11, 15, 32, 39, 58, 87, 95, 101, 106, 107, 108, 119, 138, 147, 163, 180,
				188, 229, 239, 242, 259, 280, 282, 296, 309, 316, 322, 329, 374, 417, 418, 438, 447, 517, 521, 531, 551,
				570, 598, 633, 634, 642, 658, 659, 683, 684, 729, 735, 766, 773, 780, 786, 787, 788, 810, 822, 830, 838,
				848, 861, 877, 878, 919, 921, 930, 935, 954, 966, 997);

		public Window window = new Window(windowStart, windowStart + windowSizeMillis);

		public Set<Long> nodeIds = Collections.singleton(producerId);

		private Set<Long> initial = new HashSet<>();
		private Set<Long> additions = new HashSet<>();
		private Set<Long> dropouts = new HashSet<>();

		public SecureAggregationNative secureAggregation;
		public IHeac heac;
		public DeltaUniverseState deltaState;
		public UniverseState baseUniverseState;

		@Setup(Level.Invocation)
		public void setupInvocation() {
			// ensure that only initial are here
			this.secureAggregation.clearEpochNeighbourhoodsER(epoch, nodeIds);
			this.secureAggregation.buildEpochNeighbourhoodsER(epoch, nodeIds, initial, k);
		}

		@Setup(Level.Trial)
		public void setupBenchmark() throws NoSuchAlgorithmException {

			String parts[] = addE_addNE_dropE_dropNE.split("_");
			int numAdditionsWithEdge = Integer.parseInt(parts[0]);
			int numAdditionsWithoutEdge = Integer.parseInt(parts[1]);
			int numDropoutsWithEdge = Integer.parseInt(parts[2]);
			int numDropoutsWithoutEdge = Integer.parseInt(parts[3]);

			Set<Long> producerIds = LongStream.rangeClosed(1, universeSize).boxed().collect(Collectors.toSet());

			Set<Long> neighbors = edges.stream().map(x -> (long) x).collect(Collectors.toSet());

			Set<Long> nonNeighbors = producerIds.stream().filter(x -> !neighbors.contains(x))
					.collect(Collectors.toSet());
			nonNeighbors.remove(1l);

			// assign neighbors
			Queue<Long> neighborQueue = new LinkedBlockingQueue<Long>(neighbors);

			for (int i = 0; i < numAdditionsWithEdge; i++) {
				additions.add(neighborQueue.poll());
			}
			for (int i = 0; i < numDropoutsWithEdge; i++) {
				dropouts.add(neighborQueue.poll());
			}
			neighborQueue.forEach(pId -> initial.add(pId));

			// assign non neighbors
			Queue<Long> nonNeighborQueue = new LinkedBlockingQueue<Long>(nonNeighbors);

			for (int i = 0; i < numAdditionsWithoutEdge; i++) {
				additions.add(nonNeighborQueue.poll());
			}
			for (int i = 0; i < numDropoutsWithoutEdge; i++) {
				dropouts.add(nonNeighborQueue.poll());
			}
			nonNeighborQueue.forEach(pId -> initial.add(pId));

			if (initial.size() + additions.size() + dropouts.size() != universeSize - 1) {
				throw new IllegalArgumentException("all together must be equal to universe size");
			}

			initial.addAll(dropouts);

			// build member diff
			Map<String, Integer> memberDiff = new HashMap<>();
			additions.forEach(pId -> memberDiff.put(pId + "", 1));
			dropouts.forEach(pId -> memberDiff.put(pId + "", -1));

			this.deltaState = new DeltaUniverseState(WindowStatus.MERGED.code(), memberDiff, null);

			UniverseConfig universeConfig = new UniverseConfig(windowSizeMillis, k, epochSize);
			this.baseUniverseState = new UniverseState(epoch, t, universeConfig);
			this.baseUniverseState.setEpochs(new ConcurrentHashMap<>());
			this.baseUniverseState.setMembers(initial);

			Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = DataLoader.createAndLoadSharedKeys(nodeIds,
					producerIds, dataDir, null, true);

			// setup secure aggregation instance
			this.secureAggregation = new SecureAggregationNative();
			this.secureAggregation.addSharedKeys(sharedKeys);

			SecretKey heacKey = CryptoUtil.generateKey();
			this.heac = new Heac(heacKey);
		}

	}

	// can be used to identify the edges by printing them out in native code
//	public static void main(String[] args) {
//
//		int universeSize = 1000;
//		String dataDir = "C:/Development/zeph/data";
//		Set<Long> producerIds = LongStream.rangeClosed(1, universeSize).boxed().collect(Collectors.toSet());
//
//		long nodeId = 1l;
//		Set<Long> nodeIds = Collections.singleton(nodeId);
//		int k = ErdosRenyiUtil.getK(universeSize, 0.5, 1.0e-5);
//
//		long windowSize = 10000;
//		long epochSize = ErdosRenyiUtil.getNumberOfGraphs(k) * windowSize;
//
//		long windowStart = 1504 * windowSize;
//		long epoch = WindowUtil.getWindowStart(windowStart, epochSize);
//
//		short t = (short) ((windowStart - epoch) / (double) windowSize);
//
//		System.out.println("k=" + k);
//		System.out.println("epoch=" + epoch);
//		System.out.println("epochSize=" + epochSize);
//		System.out.println("t=" + t);
//		System.out.println("wstart=" + windowStart);
//
//		SecureAggregationNative secureAggregation = new SecureAggregationNative();
//		Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = DataLoader.createAndLoadSharedKeys(
//				nodeIds, producerIds, dataDir, null, true);
//
//		secureAggregation.addSharedKeys(sharedKeys);
//
//		secureAggregation.buildEpochNeighbourhoodsER(epoch, nodeIds, producerIds, k);
//
//		Set<Long> dropped = Collections.emptySet();
//
//		Digest digest = secureAggregation.getDummyKeySumER(windowStart, epoch, t, nodeId, dropped);
//
//		secureAggregation.close();
//	}

}
