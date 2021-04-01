package ch.ethz.infk.pps.zeph.clientdemo.config;

import java.util.Set;

public class ClientDemoDefinition {

	public enum Phase {
		CREATE_PRODUCER_IDENTITY, GENERATE_SHARED_KEYS, RUN_SYSTEM
	}

	private String baseFolder;

	private String keystorePassword;

	private int driverTimeoutSec;

	private Set<Phase> phases;

	public String getBaseFolder() {
		return baseFolder;
	}

	public void setBaseFolder(String baseFolder) {
		this.baseFolder = baseFolder;
	}

	public String getKeystorePassword() {
		return keystorePassword;
	}

	public void setKeystorePassword(String keystorePassword) {
		this.keystorePassword = keystorePassword;
	}

	public int getDriverTimeoutSec() {
		return driverTimeoutSec;
	}

	public void setDriverTimeoutSec(int driverTimeoutSec) {
		this.driverTimeoutSec = driverTimeoutSec;
	}

	public Set<Phase> getPhases() {
		return phases;
	}

	public void setPhases(Set<Phase> phases) {
		this.phases = phases;
	}

}
