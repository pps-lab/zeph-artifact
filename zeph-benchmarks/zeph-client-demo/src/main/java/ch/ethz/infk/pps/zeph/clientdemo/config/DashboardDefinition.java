package ch.ethz.infk.pps.zeph.clientdemo.config;

import ch.ethz.infk.pps.zeph.shared.pojo.HostInfo;

public class DashboardDefinition {

	private HostInfo hostInfo;
	private String authToken;
	private long universeId;

	private int membershipHistoryLimit;
	private int resultHistoryLimit;
	private int statusHistoryLimit;

	public HostInfo getHostInfo() {
		return hostInfo;
	}

	public void setHostInfo(HostInfo hostInfo) {
		this.hostInfo = hostInfo;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public long getUniverseId() {
		return universeId;
	}

	public void setUniverseId(long universeId) {
		this.universeId = universeId;
	}

	public int getMembershipHistoryLimit() {
		return membershipHistoryLimit;
	}

	public void setMembershipHistoryLimit(int membershipHistoryLimit) {
		this.membershipHistoryLimit = membershipHistoryLimit;
	}

	public int getResultHistoryLimit() {
		return resultHistoryLimit;
	}

	public void setResultHistoryLimit(int resultHistoryLimit) {
		this.resultHistoryLimit = resultHistoryLimit;
	}

	public int getStatusHistoryLimit() {
		return statusHistoryLimit;
	}

	public void setStatusHistoryLimit(int statusHistoryLimit) {
		this.statusHistoryLimit = statusHistoryLimit;
	}

}
