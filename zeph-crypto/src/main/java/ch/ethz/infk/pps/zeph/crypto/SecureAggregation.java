package ch.ethz.infk.pps.zeph.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class SecureAggregation {

	private ConcurrentHashMap<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = null;

	public void addSharedKeys(Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys) {
		if (this.sharedKeys == null) {
			this.sharedKeys = new ConcurrentHashMap<>(sharedKeys.size());
		}
		this.sharedKeys.putAll(sharedKeys);
	}

	public Digest getDummyKeySum(Window window, long producerId, Collection<Long> otherProducerIds) {

		Digest aggregator = DigestOp.empty();
		final int size = otherProducerIds.size();
		Iterator<Long> iter = otherProducerIds.iterator();
		for (int i = 0; i < size; i++) {
			long otherProducerId = iter.next();

			if (producerId != otherProducerId) {
				ImmutableUnorderedPair<Long> pair = ImmutableUnorderedPair.of(producerId, otherProducerId);
				SecretKey sharedKey = sharedKeys.get(pair);

				if (sharedKey == null) {
					throw new IllegalArgumentException("not implemented yet: unknown shared key");
				}

				Digest key = getDummyKey(window, producerId, otherProducerId, sharedKey);
				aggregator = DigestOp.add(aggregator, key);

			}
		}

		return aggregator;
	}

	public ConcurrentHashMap<ImmutableUnorderedPair<Long>, SecretKey> getSharedKeys() {
		return sharedKeys;
	}

	private Digest getDummyKey(Window window, Long producerId, Long otherProducerId, SecretKey sharedKey) {
		if (producerId == otherProducerId) {
			return DigestOp.empty();
		}

		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, sharedKey);

			boolean minusKey = otherProducerId > producerId;
			return CryptoUtil.applyPRF(cipher, window.getStart(), minusKey);

		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new IllegalArgumentException("failed to build dummy key", e);
		}
	}

}
