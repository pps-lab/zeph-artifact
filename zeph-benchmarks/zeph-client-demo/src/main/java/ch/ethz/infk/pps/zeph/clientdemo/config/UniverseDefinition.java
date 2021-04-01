package ch.ethz.infk.pps.zeph.clientdemo.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.ethz.infk.pps.shared.avro.Window;

public class UniverseDefinition {

	private long universeId;
	private long windowSizeMillis;
	private int memberThreshold;
	private Set<List<Long>> members;
	private double alpha;
	private double delta;

	@JsonIgnore
	private Window firstWindow;

	@JsonIgnore
	private Set<Long> unrolledMembers;

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

	public int getMemberThreshold() {
		return memberThreshold;
	}

	public void setMemberThreshold(int memberThreshold) {
		this.memberThreshold = memberThreshold;
	}

	public Set<List<Long>> getMembers() {
		return members;
	}

	public void setMembers(Set<List<Long>> members) {
		this.members = members;
	}

	@JsonIgnore
	public Window getFirstWindow() {
		return firstWindow;
	}

	public void setFirstWindow(Window firstWindow) {
		this.firstWindow = firstWindow;
	}

	public void unroll() {
		this.unrolledMembers = members.stream().flatMap(inner -> {
			if (inner.size() == 1) {
				return inner.stream();
			} else if (inner.size() == 2 && inner.get(0) < inner.get(1)) {
				return LongStream.rangeClosed(inner.get(0), inner.get(1)).boxed();
			} else {
				throw new IllegalArgumentException("illegal members field: " + members);
			}
		}).collect(Collectors.toSet());
	}

	@JsonIgnore
	public Set<Long> getUnrolledMembers() {
		return this.unrolledMembers;
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

}
