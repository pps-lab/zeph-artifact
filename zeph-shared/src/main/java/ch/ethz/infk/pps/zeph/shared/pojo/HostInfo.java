package ch.ethz.infk.pps.zeph.shared.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class HostInfo {

	private String host;
	private int port;

	public HostInfo() {

	}

	public HostInfo(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@JsonIgnore
	public String getPath() {
		return host + ":" + port;
	}

	public static HostInfo of(String hostInfoStr) {
		String[] parts = hostInfoStr.split(":");
		return new HostInfo(parts[0], Integer.parseInt(parts[1]));
	}

}
