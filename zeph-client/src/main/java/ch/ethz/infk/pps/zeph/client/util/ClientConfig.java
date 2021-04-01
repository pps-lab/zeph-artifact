package ch.ethz.infk.pps.zeph.client.util;

import java.security.PrivateKey;
import java.util.Map;

import javax.crypto.SecretKey;

import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;

public class ClientConfig {

	private long producerId;
	private long universeId;
	private SecretKey heacKey;
	private PrivateKey privateKey;
	private Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys;

	public ClientConfig() {
	}

	public ClientConfig(long producerId, long universeId, SecretKey heacKey) {
		this.producerId = producerId;
		this.universeId = universeId;
		this.heacKey = heacKey;
	}

	public long getProducerId() {
		return producerId;
	}

	public void setProducerId(long producerId) {
		this.producerId = producerId;
	}

	public long getUniverseId() {
		return universeId;
	}

	public void setUniverseId(long universeId) {
		this.universeId = universeId;
	}

	public SecretKey getHeacKey() {
		return heacKey;
	}

	public void setHeacKey(SecretKey heacKey) {
		this.heacKey = heacKey;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(PrivateKey privateKey) {
		this.privateKey = privateKey;
	}

	public Map<ImmutableUnorderedPair<Long>, SecretKey> getSharedKeys() {
		return sharedKeys;
	}

	public void setSharedKeys(Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys) {
		this.sharedKeys = sharedKeys;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((heacKey == null) ? 0 : heacKey.hashCode());
		result = prime * result + ((privateKey == null) ? 0 : privateKey.hashCode());
		result = prime * result + (int) (producerId ^ (producerId >>> 32));
		result = prime * result + ((sharedKeys == null) ? 0 : sharedKeys.hashCode());
		result = prime * result + (int) (universeId ^ (universeId >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ClientConfig other = (ClientConfig) obj;
		if (heacKey == null) {
			if (other.heacKey != null)
				return false;
		} else if (!heacKey.equals(other.heacKey))
			return false;
		if (privateKey == null) {
			if (other.privateKey != null)
				return false;
		} else if (!privateKey.equals(other.privateKey))
			return false;
		if (producerId != other.producerId)
			return false;
		if (sharedKeys == null) {
			if (other.sharedKeys != null)
				return false;
		} else if (!sharedKeys.equals(other.sharedKeys))
			return false;
		if (universeId != other.universeId)
			return false;
		return true;
	}

}
