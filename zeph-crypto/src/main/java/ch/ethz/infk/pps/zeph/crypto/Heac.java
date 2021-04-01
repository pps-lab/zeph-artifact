package ch.ethz.infk.pps.zeph.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.shared.avro.Digest;

public class Heac implements IHeac {

	private Cipher cipher;

	private Long cachedTime = null;
	private Digest cachedHeacKey = null;

	public Heac(SecretKey key) {
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
			throw new IllegalArgumentException("failed to init heac", e);
		}
	}

	@Override
	public Digest encrypt(long msgTimestamp, Digest msg, long prevTimestamp) {
		Digest key = getKey(prevTimestamp, msgTimestamp);
		return DigestOp.add(msg, key);
	}

	@Override
	public Digest decrypt(long ctTimestamp, Digest ct, long prevTimestamp) {
		Digest key = getKey(prevTimestamp, ctTimestamp);
		return DigestOp.subtract(ct, key);
	}

	@Override
	public Digest getKey(long maxPrevTime, long maxTime) {

		if (cachedTime == null || maxPrevTime != cachedTime) {
			cachedTime = maxPrevTime;
			cachedHeacKey = CryptoUtil.applyPRF(cipher, maxPrevTime, false);
		}

		Digest heacKey = CryptoUtil.applyPRF(cipher, maxTime, false);

		Digest key = DigestOp.subtract(heacKey, cachedHeacKey);

		// update cached key
		cachedTime = maxTime;
		cachedHeacKey = heacKey;

		return key;
	}

	public List<Long> encrypt(long msgTimestamp, List<Long> msg, long prevTimestamp) {
		int size = msg.size();
		long[] startKey = CryptoUtil.applyPRF(size, cipher, prevTimestamp, false);
		long[] endKey = CryptoUtil.applyPRF(size, cipher, msgTimestamp, false);

		List<Long> ct = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			ct.add(msg.get(i) + endKey[i] - startKey[i]);
		}
		return ct;
	}

	public List<Long> decrypt(long ctTimestamp, List<Long> ct, long prevTimestamp) {
		int size = ct.size();
		long[] startKey = CryptoUtil.applyPRF(size, cipher, prevTimestamp, false);
		long[] endKey = CryptoUtil.applyPRF(size, cipher, ctTimestamp, false);
		List<Long> msg = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			msg.add(ct.get(i) - endKey[i] + startKey[i]);
		}
		return msg;
	}

}
