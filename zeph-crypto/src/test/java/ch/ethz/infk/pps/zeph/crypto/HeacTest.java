package ch.ethz.infk.pps.zeph.crypto;

import static org.junit.Assert.assertEquals;

import javax.crypto.SecretKey;

import org.junit.Test;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.shared.avro.Digest;

public class HeacTest {

	@Test
	public void testHeac() {

		SecretKey heacKey = CryptoUtil.generateKey();
		IHeac encHeac = new Heac(heacKey);
		IHeac decHeac = new Heac(heacKey);

		long initTimestamp = 0;

		Digest msg1 = DigestOp.random();
		long timestamp1 = 1000l;
		Digest ct1 = encHeac.encrypt(timestamp1, msg1, initTimestamp);
		Digest res1 = decHeac.decrypt(timestamp1, ct1, initTimestamp);
		assertEquals("1st", msg1, res1);

		Digest msg2 = DigestOp.random();
		long timestamp2 = 1345l;
		Digest ct2 = encHeac.encrypt(timestamp2, msg2, timestamp1);
		Digest res2 = decHeac.decrypt(timestamp2, ct2, timestamp1);
		assertEquals("2nd", msg2, res2);

		Digest msg3 =  DigestOp.random();
		long timestamp3 = 1632l;
		Digest ct3 = encHeac.encrypt(timestamp3, msg3, timestamp2);
		Digest res3 = decHeac.decrypt(timestamp3, ct3, timestamp2);
		assertEquals("3rd", msg3, res3);

		Digest msgSum = DigestOp.add(DigestOp.add(msg1, msg2), msg3);
		Digest ctSum = DigestOp.add(DigestOp.add(ct1, ct2), ct3);
		Digest resSum = decHeac.decrypt(timestamp3, ctSum, initTimestamp);
		assertEquals("Sum", msgSum, resSum);

		Digest aggKey = decHeac.getKey(initTimestamp, timestamp3);
		Digest resSumV2 = DigestOp.subtract(ctSum, aggKey);

		assertEquals("Sum 2", msgSum, resSumV2);

	}

	@Test
	public void testHeac2() {

	}

}
