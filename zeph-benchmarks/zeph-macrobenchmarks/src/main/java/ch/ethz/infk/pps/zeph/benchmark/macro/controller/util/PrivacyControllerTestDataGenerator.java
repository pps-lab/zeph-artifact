package ch.ethz.infk.pps.zeph.benchmark.macro.controller.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Random;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Multimap;

import ch.ethz.infk.pps.zeph.benchmark.macro.controller.config.PrivacyControllerBenchmarkConfig;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.zeph.shared.serde.SpecificAvroSimpleSerializer;
import ch.ethz.infk.pps.zeph.shared.serde.WindowedUniverseIdSerde.WindowedUniverseIdSerializer;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Universe;
import ch.ethz.infk.pps.shared.avro.Window;

public class PrivacyControllerTestDataGenerator {

	private static final Logger LOG = LogManager.getLogger();

	public static final long GRACE = 5000;
	public static final long COMMITTING_TIME = 4000;
	public static final long MERGING_TIME = 500;
	public static final long CLOSING_TIME = 6000;

	private Map<Long, Universe> universeConfigs;
	private Multimap<Long, Long> universes;

	private int numWindows;
	private double dropoutProb;
	private double comebackProb;

	private KafkaProducer<WindowedUniverseId, DeltaUniverseState> kafkaProducer;

	public PrivacyControllerTestDataGenerator(Map<Long, Universe> universeConfigs, Multimap<Long, Long> universes,
			PrivacyControllerBenchmarkConfig config) {
		this.universeConfigs = universeConfigs;
		this.universes = universes;

		this.numWindows = config.getWindows();
		this.dropoutProb = config.getClientDropoutProb();
		this.comebackProb = config.getClientComebackProb();

		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServer());
		props.put(ProducerConfig.CLIENT_ID_CONFIG, "init");

		WindowedUniverseIdSerializer keySerializer = new WindowedUniverseIdSerializer();
		SpecificAvroSimpleSerializer<DeltaUniverseState> valueSerializer = new SpecificAvroSimpleSerializer<>(
				DeltaUniverseState.getClassSchema());

		this.kafkaProducer = new KafkaProducer<>(props, keySerializer, valueSerializer);
	}

	private List<Long> present;
	private List<Long> dropped;
	private RandomGenerator rng;

	private List<Integer> diffSizes;

	public void generateRecords(Map<Long, Boolean> universeIds) {

		diffSizes = new ArrayList<>();
		rng = RandomGeneratorFactory.createRandomGenerator(new Random());

		PriorityQueue<ProducerRecord<WindowedUniverseId, DeltaUniverseState>> queue = new PriorityQueue<>(getCmp());
		long offset = System.currentTimeMillis();

		universeConfigs.forEach((universeId, universe) -> {

			if (universeIds.get(universeId)) { // only if topic needs to initialized

				present = new ArrayList<>();
				dropped = new ArrayList<>();

				long windowSizeMillis = universe.getFirstWindow().getEnd() - universe.getFirstWindow().getStart();

				System.out.println("Num Windows = " + numWindows);
				System.out.print("<");
				for (int i = 0; i <= numWindows; i++) {

					Window window = new Window(offset + i * windowSizeMillis, offset + (i + 1) * windowSizeMillis);
					long timestamp = window.getStart();

					// clear records up to timestamp
					int size = queue.size();
					for (int j = 0; j < size; j++) {
						if (queue.peek().timestamp() < timestamp) {
							ProducerRecord<WindowedUniverseId, DeltaUniverseState> record = queue.poll();
							kafkaProducer.send(new ProducerRecord<>(record.topic(), record.key(), record.value()));
						} else {
							break;
						}
					}

					queue.add(getRecord(universeId, window, WindowStatus.OPEN, timestamp));
					timestamp += (windowSizeMillis + GRACE);
					queue.add(getRecord(universeId, window, WindowStatus.STAGED, timestamp));
					timestamp += COMMITTING_TIME;
					queue.add(getRecord(universeId, window, WindowStatus.COMMITTED, timestamp));
					timestamp += MERGING_TIME;
					queue.add(getRecord(universeId, window, WindowStatus.MERGED, timestamp));
					timestamp += CLOSING_TIME;
					queue.add(getRecord(universeId, window, WindowStatus.CLOSED, timestamp));

					if (i % 100000 == 0) {
						System.out.print("=");
					}
				}
				System.out.println(">");
			}

		});

		kafkaProducer.close();

	}

	private ProducerRecord<WindowedUniverseId, DeltaUniverseState> getRecord(long universeId, Window window,
			WindowStatus status, long timestamp) {
		WindowedUniverseId windowedUniverseId = new WindowedUniverseId(universeId, window);

		Digest transformationToken = null;
		Map<String, Integer> memberDiff = null;
		if (status == WindowStatus.MERGED) {
			memberDiff = buildMemberDiff(universeId);

		} else if (status == WindowStatus.CLOSED) {
			transformationToken = new Digest();
		}

		DeltaUniverseState state = new DeltaUniverseState(status.code(), memberDiff, transformationToken);
		return new ProducerRecord<>(Names.getInfosTopic(universeId), null, timestamp, windowedUniverseId, state);

	}

	private Map<String, Integer> buildMemberDiff(long universeId) {
		Map<String, Integer> memberDiff = new HashMap<>();

		int droppedSize = dropped.size();
		int presentSize = present.size();

		if (presentSize == 0 && droppedSize == 0) {
			present.addAll(universes.get(universeId));
			present.forEach(pId -> memberDiff.put(pId + "", 1));

			// LOG.info("u={} initial member diff = {}", universeId, memberDiff);

		} else {

			BinomialDistribution dropoutDistribution = new BinomialDistribution(rng, present.size(), dropoutProb);

			if (droppedSize > 0) {
				BinomialDistribution comebackDistribution = new BinomialDistribution(rng, dropped.size(), comebackProb);
				int comebackCount = comebackDistribution.sample();
				for (int i = 0; i < comebackCount; i++) {
					Long pId = dropped.remove(0);
					present.add(pId);
					memberDiff.put(pId + "", 1);
				}
			}

			int dropCount = dropoutDistribution.sample();
			for (int i = 0; i < dropCount; i++) {
				Long pId = present.remove(0);
				dropped.add(pId);
				memberDiff.put(pId + "", -1);
			}

			diffSizes.add(memberDiff.size());

		}

		return memberDiff;
	}

	private static Comparator<ProducerRecord<WindowedUniverseId, DeltaUniverseState>> getCmp() {
		Comparator<ProducerRecord<WindowedUniverseId, DeltaUniverseState>> cmp = new Comparator<ProducerRecord<WindowedUniverseId, DeltaUniverseState>>() {
			@Override
			public int compare(ProducerRecord<WindowedUniverseId, DeltaUniverseState> o1,
					ProducerRecord<WindowedUniverseId, DeltaUniverseState> o2) {

				return ComparisonChain.start()
						.compare(o1.timestamp(), o2.timestamp())
						.compare(o1.key().getUniverseId(),
								o2.key().getUniverseId())
						.result();

			}
		};

		return cmp;
	}

}
