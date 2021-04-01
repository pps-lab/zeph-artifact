package ch.ethz.infk.pps.zeph.server.worker.config;

public class WorkerConfig {

	private String instanceId;

	private String kafkaBootstrapServers;
	private String stateDir;

	private long universeId;
	private long universeSize;
	private long windowSizeMillis;
	private long graceSizeMillis;

	private int streamThreads;
	private long retentionPeriodHours;
	private int numPartitions;
	private int replication;

	private boolean deleteTopics;

	public String getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	public String getKafkaBootstrapServers() {
		return kafkaBootstrapServers;
	}

	public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
		this.kafkaBootstrapServers = kafkaBootstrapServers;
	}

	public int getStreamThreads() {
		return streamThreads;
	}

	public void setStreamThreads(int streamThreads) {
		this.streamThreads = streamThreads;
	}

	public boolean deleteTopics() {
		return deleteTopics;
	}

	public void setDeleteTopics(boolean deleteTopics) {
		this.deleteTopics = deleteTopics;
	}

	public String getStateDir() {
		return stateDir;
	}

	public void setStateDir(String stateDir) {
		this.stateDir = stateDir;
	}

	public long getUniverseSize() {
		return universeSize;
	}

	public void setUniverseSize(long universeSize) {
		this.universeSize = universeSize;
	}

	public long getUniverseId() {
		return universeId;
	}

	public void setUniverseId(long universeId) {
		this.universeId = universeId;
	}

	public long getWindowSizeMillis() {
		return windowSizeMillis;
	}

	public void setWindowSizeMillis(long windowSizeMillis) {
		this.windowSizeMillis = windowSizeMillis;
	}

	public long getGraceSizeMillis() {
		return graceSizeMillis;
	}

	public void setGraceSizeMillis(long graceSizeMillis) {
		this.graceSizeMillis = graceSizeMillis;
	}

	public long getRetentionPeriodHours() {
		return retentionPeriodHours;
	}

	public void setRetentionPeriodHours(long retentionPeriodHours) {
		this.retentionPeriodHours = retentionPeriodHours;
	}

	public int getNumPartitions() {
		return numPartitions;
	}

	public void setNumPartitions(int numPartitions) {
		this.numPartitions = numPartitions;
	}

	public int getReplication() {
		return replication;
	}

	public void setReplication(int replication) {
		this.replication = replication;
	}

}
