package ch.ethz.infk.pps.zeph.clientdemo.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PrivacyControllerDefinition {

	private long controllerId;
	private long pollTimeoutMillis;
	private Set<List<Long>> members;

	@JsonIgnore
	private Set<Long> unrolledMembers;

	public long getControllerId() {
		return controllerId;
	}

	public void setControllerId(long controllerId) {
		this.controllerId = controllerId;
	}

	public long getPollTimeoutMillis() {
		return pollTimeoutMillis;
	}

	public void setPollTimeoutMillis(long pollTimeoutMillis) {
		this.pollTimeoutMillis = pollTimeoutMillis;
	}

	public Set<List<Long>> getMembers() {
		return members;
	}

	public void setMembers(Set<List<Long>> members) {
		this.members = members;
	}

	public void unroll() {
		this.unrolledMembers = this.members.stream().flatMap(inner -> {
			if (inner.size() == 1) {
				return inner.stream();
			} else if (inner.size() == 2 && inner.get(0) < inner.get(1)) {
				return LongStream.rangeClosed(inner.get(0), inner.get(1)).boxed();
			} else {
				throw new IllegalArgumentException("illegal members field");
			}
		}).collect(Collectors.toSet());
	}

	@JsonIgnore
	public Set<Long> getUnrolledMembers() {
		return this.unrolledMembers;
	}

}
