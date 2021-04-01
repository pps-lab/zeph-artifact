package ch.ethz.infk.pps.zeph.shared;

import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.security.Security;

import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Arrays;
import org.junit.Test;

import com.google.common.primitives.Longs;

public class CryptoUtilTest {

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	@Test
	public void testSeedGenerateKeyPair() {

		byte[] seed1 = Longs.toByteArray(1l);
		KeyPair kp1 = CryptoUtil.generateKeyPair(seed1);

		byte[] seed2 = Longs.toByteArray(2l);
		KeyPair kp2 = CryptoUtil.generateKeyPair(seed2);

		byte[] seed3 = Longs.toByteArray(1l);
		KeyPair kp3 = CryptoUtil.generateKeyPair(seed3);

		byte[] seed4 = Longs.toByteArray(2l);
		KeyPair kp4 = CryptoUtil.generateKeyPair(seed4);

		assertTrue(Arrays.areEqual(kp1.getPrivate().getEncoded(), kp3.getPrivate().getEncoded()));
		assertTrue(Arrays.areEqual(kp1.getPublic().getEncoded(), kp3.getPublic().getEncoded()));

		assertTrue(Arrays.areEqual(kp2.getPrivate().getEncoded(), kp4.getPrivate().getEncoded()));
		assertTrue(Arrays.areEqual(kp2.getPublic().getEncoded(), kp4.getPublic().getEncoded()));

	}

	@Test
	public void testSeedGenerateKey() {
		byte[] seed1 = Longs.toByteArray(1l);
		SecretKey k1 = CryptoUtil.generateKey(seed1);

		byte[] seed2 = Longs.toByteArray(2l);
		SecretKey k2 = CryptoUtil.generateKey(seed2);

		byte[] seed3 = Longs.toByteArray(1l);
		SecretKey k3 = CryptoUtil.generateKey(seed3);

		byte[] seed4 = Longs.toByteArray(2l);
		SecretKey k4 = CryptoUtil.generateKey(seed4);

		assertTrue(Arrays.areEqual(k1.getEncoded(), k3.getEncoded()));
		assertTrue(Arrays.areEqual(k2.getEncoded(), k4.getEncoded()));

	}

}
