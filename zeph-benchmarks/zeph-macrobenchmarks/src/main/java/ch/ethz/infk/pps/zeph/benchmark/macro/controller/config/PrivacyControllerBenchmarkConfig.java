package ch.ethz.infk.pps.zeph.benchmark.macro.controller.config;

public class PrivacyControllerBenchmarkConfig {

	private String bootstrapServer;
	private int partitions;

	private int universes;
	private int universeSize;
	private int universeMinimalSize;

	private double alpha;
	private double delta;

	private int clients; // of privacy controller per universe
	private double clientDropoutProb;
	private double clientComebackProb;

	private int testTimeSec;
	private int windows;

	private String dataFolder;

	private String outputFile;

	private boolean deleteTokenTopics;
	private boolean deleteAllInfoTopics;

	public boolean deleteTokenTopics() {
		return deleteTokenTopics;
	}

	public void setDeleteTokenTopics(boolean deleteTopics) {
		this.deleteTokenTopics = deleteTopics;
	}

	public boolean deleteAllInfoTopics() {
		return deleteAllInfoTopics;
	}

	public void setDeleteAllInfoTopics(boolean deleteTopics) {
		this.deleteAllInfoTopics = deleteTopics;
	}

	public String getBootstrapServer() {
		return bootstrapServer;
	}

	public void setBootstrapServer(String bootstrapServer) {
		this.bootstrapServer = bootstrapServer;
	}

	public String getOutputFile() {
		return outputFile;
	}

	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	public int getPartitions() {
		return partitions;
	}

	public void setPartitions(int partitions) {
		this.partitions = partitions;
	}

	public int getUniverses() {
		return universes;
	}

	public void setUniverses(int universes) {
		this.universes = universes;
	}

	public int getUniverseSize() {
		return universeSize;
	}

	public void setUniverseSize(int universeSize) {
		this.universeSize = universeSize;
	}

	public int getUniverseMinimalSize() {
		return universeMinimalSize;
	}

	public void setUniverseMinimalSize(int universeMinimalSize) {
		this.universeMinimalSize = universeMinimalSize;
	}

	public double getAlpha() {
		return alpha;
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	public double getDelta() {
		return delta;
	}

	public void setDelta(double delta) {
		this.delta = delta;
	}

	public int getClients() {
		return clients;
	}

	public void setClients(int clients) {
		this.clients = clients;
	}

	public double getClientDropoutProb() {
		return clientDropoutProb;
	}

	public void setClientDropoutProb(double clientDropoutProb) {
		this.clientDropoutProb = clientDropoutProb;
	}

	public double getClientComebackProb() {
		return clientComebackProb;
	}

	public void setClientComebackProb(double clientComebackProb) {
		this.clientComebackProb = clientComebackProb;
	}

	public int getTestTimeSec() {
		return testTimeSec;
	}

	public void setTestTimeSec(int testTimeSec) {
		this.testTimeSec = testTimeSec;
	}

	public int getWindows() {
		return windows;
	}

	public void setWindows(int windows) {
		this.windows = windows;
	}

	public String getDataFolder() {
		return dataFolder;
	}

	public void setDataFolder(String dataFolder) {
		this.dataFolder = dataFolder;
	}

}
