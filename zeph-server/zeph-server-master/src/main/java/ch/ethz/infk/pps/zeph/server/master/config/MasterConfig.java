package ch.ethz.infk.pps.zeph.server.master.config;

public class MasterConfig {

	private String instanceId;

	private String kafkaBootstrapServers;
	private String interactiveQueriesServers;
	private String stateDir;

	private long timeToCommitMillis;

	private int numPartitions;
	private int replication;

	private int streamThreads;

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

	public String getInteractiveQueriesServers() {
		return interactiveQueriesServers;
	}

	public void setInteractiveQueriesServers(String interactiveQueriesServers) {
		this.interactiveQueriesServers = interactiveQueriesServers;
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

	public int getStreamThreads() {
		return streamThreads;
	}

	public void setStreamThreads(int streamThreads) {
		this.streamThreads = streamThreads;
	}

	public long getTimeToCommitMillis() {
		return timeToCommitMillis;
	}

	public void setTimeToCommitMillis(long timeToCommitMillis) {
		this.timeToCommitMillis = timeToCommitMillis;
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
