package ch.ethz.infk.pps.zeph.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.Bytes.ByteArrayComparator;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class SecureAggregationDream {

	final ByteArrayComparator comp = Bytes.BYTES_LEXICO_COMPARATOR;

	private ConcurrentHashMap<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = null;
	private byte[] bound;

	public SecureAggregationDream() {
		bound = new byte[16];
		bound[0] = 2;
	}

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

				if (key != null) {
					aggregator = DigestOp.add(aggregator, key);
				}

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

			byte[] input = cipher.doFinal(Longs.toByteArray(window.getStart() + 1));
			if (comp.compare(bound, input) >= 0) {
				boolean minusKey = otherProducerId > producerId;
				return CryptoUtil.applyPRF(cipher, window.getStart(), minusKey);
			} else {
				return null;
			}

		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
				| BadPaddingException e) {
			throw new IllegalArgumentException("failed to build dummy key", e);
		}
	}

}
