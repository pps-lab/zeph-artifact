package ch.ethz.infk.pps.zeph.client.util;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Phaser;

public class UniverseState {

	public static final long NO_EPOCH = -1;

	private final UniverseConfig universeConfig;
	private Long epoch = NO_EPOCH;
	private Short t;

	private Long clearEpoch;
	private boolean epochChange;

	private Set<Long> members;
	private Set<Long> newMembers;
	private Set<Long> newMembersCummulative;
	private Set<Long> droppedMembersCummulative;

	private ConcurrentMap<Long, Phaser> epochs;// <epoch, Phaser>

	public UniverseState(UniverseConfig universeConfig) {
		this.universeConfig = universeConfig;
	}

	public UniverseState(long epoch, short t, UniverseConfig universeConfig) {
		this.epoch = epoch;
		this.t = t;
		this.universeConfig = universeConfig;
		this.epochChange = false;
	}

	public ConcurrentMap<Long, Phaser> getEpochs() {
		return epochs;
	}

	public void setEpochs(ConcurrentMap<Long, Phaser> epochs) {
		this.epochs = epochs;
	}

	public Long getEpoch() {
		return epoch;
	}

	public Short getT() {
		return t;
	}

	public boolean isEpochChange() {
		return epochChange;
	}

	public void markNewEpoch(Long oldEpoch) {
		if (oldEpoch != null) {
			this.epochChange = true;
		}
		this.clearEpoch = oldEpoch;
	}

	public Long getClearEpoch() {
		return clearEpoch;
	}

	public void setMembers(Set<Long> members) {
		this.members = Collections.unmodifiableSet(members);
	}

	public void setNewMembers(Set<Long> members) {
		this.newMembers = Collections.unmodifiableSet(members);
	}

	public void setNewMembersCummulative(Set<Long> members) {
		this.newMembersCummulative = Collections.unmodifiableSet(members);
	}

	public void setDroppedMembersCummulative(Set<Long> members) {
		this.droppedMembersCummulative = Collections.unmodifiableSet(members);
	}

	public Set<Long> getMembers() {
		return members;
	}

	public Optional<Set<Long>> getNewMembers() {
		return Optional.ofNullable(newMembers);
	}

	public Optional<Set<Long>> getNewMembersCummulative() {
		return Optional.ofNullable(newMembersCummulative);
	}

	public Optional<Set<Long>> getDroppedMembersCummulative() {
		return Optional.ofNullable(droppedMembersCummulative);
	}

	public boolean isER() {
		return universeConfig.isER;
	}

	public int getK() {
		return universeConfig.k;
	}

	public long getWindowSize() {
		return universeConfig.windowSize;
	}

	public long getEpochSize() {
		return universeConfig.epochSize;
	}

	public UniverseConfig getUniverseConfig() {
		return universeConfig;
	}

	public static class UniverseConfig {

		protected final boolean isER;
		protected final long windowSize;
		protected final Integer k;
		protected final Long epochSize;

		public UniverseConfig(long windowSize) {
			this.windowSize = windowSize;
			this.isER = false;
			this.k = null;
			this.epochSize = null;
		}

		public UniverseConfig(long windowSize, int k, long epochSize) {
			this.windowSize = windowSize;
			this.k = k;
			this.epochSize = epochSize;
			this.isER = true;
		}

	}

}
