package ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class E2EConfig {

	private BenchmarkType benchmarkType;
	private String experimentId;

	private Boolean e2eProducer;
	private Boolean e2eController;

	private Long timestampSync;

	private Integer testTimeSec;
	private String outputFile;
	private Boolean deleteTopics;

	private Integer producerPartition;
	private Integer producerPartitionCount;
	private Integer producerPartitionSize;

	private List<Long> universeIds;
	private Integer universeSize;

	private Double expoDelayMean;
	private String kafkaBootstrapServers;
	private String dataFolder;

	private Long windowSizeMillis;

	private String application;

	// fields only required for zeph
	private Integer universeMinSize;
	private Double alpha;
	private Double delta;

	private Integer controllerPartitionCount;
	private Integer controllerPartition;
	private Integer controllerPartitionSize;

	private Integer universesInController;
	private Integer producersInController; // per universe

	private Duration consumerPollTimeout;

	public boolean isLeader() {
		return e2eProducer && producerPartition == 0l;
	}

	public Integer getTotalNumberOfProducers() {
		return producerPartitionSize * getNumUniverses();
	}

	public Integer getTotalNumberOfControllers() {
		if (benchmarkType != BenchmarkType.zeph) {
			return null;
		}
		Double total = (controllerPartitionSize * getNumUniverses() / (double) universesInController
				/ (double) producersInController);
		return total.intValue();
	}

	public Boolean isE2eProducer() {
		return e2eProducer;
	}

	public String getApplication() {
		return application;
	}

	public Boolean isE2eController() {
		return e2eController;
	}

	public void setTimestampSync(Long timestampSync) {
		this.timestampSync = timestampSync;
	}

	public Long getTimestampSync() {
		return timestampSync;
	}

	public String getExperimentId() {
		return experimentId;
	}

	public Integer getTestTimeSec() {
		return testTimeSec;
	}

	public String getOutputFile() {
		return outputFile;
	}

	public Boolean isDeleteTopics() {
		return deleteTopics;
	}

	public Integer getNumUniverses() {
		return universeIds.size();
	}

	public BenchmarkType getBenchmarkType() {
		return benchmarkType;
	}

	public Integer getProducerPartition() {
		return producerPartition;
	}

	public Integer getProducerPartitionCount() {
		return producerPartitionCount;
	}

	public Integer getProducerPartitionSize() {
		return producerPartitionSize;
	}

	public List<Long> getUniverseIds() {
		return universeIds;
	}

	public Integer getUniverseSize() {
		return universeSize;
	}

	public Double getExpoDelayMean() {
		return expoDelayMean;
	}

	public String getKafkaBootstrapServers() {
		return kafkaBootstrapServers;
	}

	public String getDataFolder() {
		return dataFolder;
	}

	public Long getWindowSizeMillis() {
		return windowSizeMillis;
	}

	public Integer getUniverseMinSize() {
		return universeMinSize;
	}

	public Double getAlpha() {
		return alpha;
	}

	public Double getDelta() {
		return delta;
	}

	public Integer getControllerPartitionCount() {
		return controllerPartitionCount;
	}

	public Integer getControllerPartition() {
		return controllerPartition;
	}

	public Integer getControllerPartitionSize() {
		return controllerPartitionSize;
	}

	public Integer getUniversesInController() {
		return universesInController;
	}

	public Integer getProducersInController() {
		return producersInController;
	}

	public Duration getConsumerPollTimeout() {
		return consumerPollTimeout;
	}

	@Override
	public String toString() {
		return "E2EConfig [benchmarkType=" + benchmarkType + ", experimentId=" + experimentId + ", e2eProducer="
				+ e2eProducer + ", e2eController=" + e2eController + ", timestampSync=" + timestampSync
				+ ", testTimeSec=" + testTimeSec + ", outputFile=" + outputFile + ", deleteTopics=" + deleteTopics
				+ ", producerPartition=" + producerPartition + ", producerPartitionCount=" + producerPartitionCount
				+ ", producerPartitionSize=" + producerPartitionSize + ", universeIds=" + universeIds
				+ ", universeSize=" + universeSize + ", expoDelayMean=" + expoDelayMean + ", kafkaBootstrapServers="
				+ kafkaBootstrapServers + ", dataFolder=" + dataFolder + ", windowSizeMillis=" + windowSizeMillis
				+ ", application=" + application + ", universeMinSize=" + universeMinSize + ", alpha=" + alpha
				+ ", delta=" + delta + ", controllerPartitionCount=" + controllerPartitionCount
				+ ", controllerPartition=" + controllerPartition + ", controllerPartitionSize="
				+ controllerPartitionSize + ", universesInController=" + universesInController
				+ ", producersInController=" + producersInController + ", consumerPollTimeout=" + consumerPollTimeout
				+ "]";
	}

	public static enum BenchmarkType {
		zeph, plaintext
	}

	public static class E2EConfigBuilder {
		private E2EConfig config;

		public E2EConfigBuilder(BenchmarkType benchmarkType) {
			config = new E2EConfig();
			config.benchmarkType = benchmarkType;
		}

		public E2EConfigBuilder withExperimentId(String experimentId) {
			config.experimentId = experimentId;
			return this;
		}

		public E2EConfigBuilder withE2EProducer(boolean e2eProducer) {
			config.e2eProducer = e2eProducer;
			return this;
		}

		public E2EConfigBuilder withE2EController(boolean e2eController) {
			config.e2eController = e2eController;
			return this;
		}

		public E2EConfigBuilder withTimestampSync(long timestampSync) {
			config.timestampSync = timestampSync;
			return this;
		}

		public E2EConfigBuilder withTestTimeSec(int testTimeSec) {
			config.testTimeSec = testTimeSec;
			return this;
		}

		public E2EConfigBuilder withApplication(String application) {
			config.application = application;
			return this;
		}

		public E2EConfigBuilder withOutputFile(String outputFile) {
			config.outputFile = outputFile;
			return this;
		}

		public E2EConfigBuilder withDeleteTopics(boolean deleteTopics) {
			config.deleteTopics = deleteTopics;
			return this;
		}

		public E2EConfigBuilder withProducerPartition(int producerPartition) {
			config.producerPartition = producerPartition;
			return this;
		}

		public E2EConfigBuilder withProducerPartitionCount(int producerPartitionCount) {
			config.producerPartitionCount = producerPartitionCount;
			return this;
		}

		public E2EConfigBuilder withProducerPartitionSize(int producerPartitionSize) {
			config.producerPartitionSize = producerPartitionSize;
			return this;
		}

		public E2EConfigBuilder withUniverseIds(List<Long> universeIds) {
			config.universeIds = universeIds;
			return this;
		}

		public E2EConfigBuilder withUniverseSize(int universeSize) {
			config.universeSize = universeSize;
			return this;
		}

		public E2EConfigBuilder withExpoDelayMean(double expoDelayMean) {
			config.expoDelayMean = expoDelayMean;
			return this;
		}

		public E2EConfigBuilder withWindowSizeMillis(long windowSizeMillis) {
			config.windowSizeMillis = windowSizeMillis;
			return this;
		}

		public E2EConfigBuilder withKafkaBootstrapServers(String kafkaBootstrapServers) {
			config.kafkaBootstrapServers = kafkaBootstrapServers;
			return this;
		}

		public E2EConfigBuilder withDataFolder(String dataFolder) {
			config.dataFolder = dataFolder;
			return this;
		}

		// zeph specific

		public E2EConfigBuilder withControllerPartition(int controllerPartition) {
			config.controllerPartition = controllerPartition;
			return this;
		}

		public E2EConfigBuilder withControllerPartitionCount(int controllerPartitionCount) {
			config.controllerPartitionCount = controllerPartitionCount;
			return this;
		}

		public E2EConfigBuilder withControllerPartitionSize(int controllerPartitionSize) {
			config.controllerPartitionSize = controllerPartitionSize;
			return this;
		}

		public E2EConfigBuilder withUniversesInController(int universesInController) {
			config.universesInController = universesInController;
			return this;
		}

		public E2EConfigBuilder withProducersInController(int producersInController) {
			config.producersInController = producersInController;
			return this;
		}

		public E2EConfigBuilder withUniverseMinSize(int universeMinSize) {
			config.universeMinSize = universeMinSize;
			return this;
		}

		public E2EConfigBuilder withAlpha(double alpha) {
			config.alpha = alpha;
			return this;
		}

		public E2EConfigBuilder withDelta(double delta) {
			config.delta = delta;
			return this;
		}

		public E2EConfigBuilder withConsumerPollTimeout(Duration consumerPollTimeout) {
			config.consumerPollTimeout = consumerPollTimeout;
			return this;
		}

		private void verifyBase() {
			Objects.requireNonNull(config.e2eProducer);
			Objects.requireNonNull(config.e2eController);

			Objects.requireNonNull(config.experimentId);
			Objects.requireNonNull(config.testTimeSec);
			Objects.requireNonNull(config.deleteTopics);
			Objects.requireNonNull(config.outputFile);

			Objects.requireNonNull(config.producerPartition);
			Objects.requireNonNull(config.producerPartitionCount);
			Objects.requireNonNull(config.producerPartitionSize);

			Objects.requireNonNull(config.universeIds);
			Objects.requireNonNull(config.universeSize);
			Objects.requireNonNull(config.expoDelayMean);
			Objects.requireNonNull(config.windowSizeMillis);
			Objects.requireNonNull(config.kafkaBootstrapServers);
			Objects.requireNonNull(config.dataFolder);

			requireTrue(!config.universeIds.isEmpty(), "universe ids cannot be empty");

			requireTrue(config.producerPartition >= 0 && config.producerPartition < config.producerPartitionCount,
					"producer partition must be within [0, producerPartitionCount)");

			requireTrue(config.producerPartitionSize * config.producerPartitionCount == config.universeSize,
					"producer partition size and count must match universe size");

		}

		private void verifyZeph() {

			Objects.requireNonNull(config.controllerPartition);
			Objects.requireNonNull(config.controllerPartitionCount);
			Objects.requireNonNull(config.controllerPartitionSize);
			Objects.requireNonNull(config.universeMinSize);
			Objects.requireNonNull(config.alpha);
			Objects.requireNonNull(config.delta);
			Objects.requireNonNull(config.universesInController);
			Objects.requireNonNull(config.producersInController);
			Objects.requireNonNull(config.consumerPollTimeout);

			requireTrue(config.controllerPartition >= 0 && config.controllerPartition < config.controllerPartitionCount,
					"controller partition must be within [0, controllerPartitionCount)");

			requireTrue(config.universesInController <= config.universeIds.size(),
					"cannot have more universes in controller than universes");

			requireTrue(config.producersInController <= config.controllerPartitionSize,
					"cannot assign more producers to controllers than there are in the partition");

			requireTrue(config.controllerPartitionSize * config.controllerPartitionCount == config.universeSize,
					"controller partition size and count must match universe size");

		}

		private void requireTrue(boolean condition, String msg) {
			if (!condition) {
				throw new IllegalArgumentException(msg);
			}
		}

		public E2EConfig build() {
			verifyBase();
			if (config.benchmarkType == BenchmarkType.zeph) {
				verifyZeph();
			}

			return config;
		}

	}

}
