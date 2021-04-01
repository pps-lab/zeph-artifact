package ch.ethz.infk.pps.zeph.shared;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.shared.avro.Digest;

public class CryptoUtil {

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	public static class Const {

		public static final String MY_CERTIFICATE_ALIAS = "my-cert";
		public static final String PRIVATE_KEY_ALIAS = "sk";

		public static final String SHARED_KEY_PREFIX = "s-";
		public static final String CERTIFICATE_PREFIX = "c-";
		public static final String CERTIFICATE_ALIAS_TEMPLATE = CERTIFICATE_PREFIX + "p%d";
		public static final String SHARED_KEY_ALIAS_TEMPLATE = SHARED_KEY_PREFIX + "p%d-p%d";

		public static final int CERTIFICATE_VALIDITY_DAYS = 365;

		public static final String MY_CERTIFICATE_COUNTRY_NAME = "CH";
		public static final String MY_CERTIFICATE_ORGANIZATION = "ETH";
		public static final String MY_CERTIFICATE_ORGANIZATIONAL_UNIT = "PPS";
		public static final String MY_CERTIFICATE_DISTINGUISHED_NAME = "";
		public static final String MY_CERTIFICATE_STATE = "Zurich";
		public static final String MY_CERTIFICATE_COMMON_NAME_TEMPLATE = "p%d";

		public static final String MY_CERTIFICATE_SERIAL_NUMBER_TEMPLATE = "%d";

		public static final String ECDH_CURVE = "secp256r1";

	}

	public static long aesOutputToLong(byte[] keyBytes) {

		// perform length matching hash function
		// (AES output is 128 bits, long is 64 bits => xor two halves of aes output to
		// obtain 64 bit long key)
		for (int j = 0; j < Longs.BYTES; j++) {
			keyBytes[j] = (byte) (keyBytes[j] ^ keyBytes[j + Longs.BYTES]);
		}

		// convert first 8 byte to long
		long key = Longs.fromBytes(keyBytes[0], keyBytes[1], keyBytes[2], keyBytes[3], keyBytes[4], keyBytes[5],
				keyBytes[6], keyBytes[7]);

		return key;
	}

	public static Digest applyPRF(Cipher cipher, long time, boolean minusKey) {

		try {
			Digest digest = DigestOp.empty();
			ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES);
			buffer.putLong(time);

			int elementId = 0;
			for(int i = 0; i < DigestOp.NUM_DIGEST_AGG_FIELDS; i++){
				String fieldName = DigestOp.DIGEST_AGG_FIELDS.get(i);

				@SuppressWarnings("unchecked")
				List<Long> elements = (List<Long>)digest.get(fieldName);
				int size = elements.size();
				for(int j = 0; j < size; j++){
					// place field id in buffer
					buffer.putLong(Long.BYTES, i);

					// apply PRF
					byte[] keyBytes = cipher.doFinal(buffer.array());

					long key = CryptoUtil.aesOutputToLong(keyBytes);

					if (minusKey) {
						key = -1 * key;
					}

					elements.set(j, key);

					elementId += 1;
				}
			}
			buffer.clear();
			return digest;

		} catch (IllegalBlockSizeException | BadPaddingException e) {
			throw new IllegalArgumentException("failed to apply prf", e);
		}

	}

	public static long[] applyPRF(int size, Cipher cipher, long time, boolean minusKey) {

		try {
			long[] keys = new long[size];
			ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES);
			buffer.putLong(time);

			for (int i = 0; i < size; i++) {
				// place field id in buffer
				buffer.putLong(Long.BYTES, i);

				// apply PRF
				byte[] keyBytes = cipher.doFinal(buffer.array());

				// Note: could save half of PRF evaluations by not doing the XOR and use one aes block for two longs
				long key = CryptoUtil.aesOutputToLong(keyBytes);

				if (minusKey) {
					key = -1 * key;
				}
				keys[i] = key;
			}
			buffer.clear();
			return keys;

		} catch (IllegalBlockSizeException | BadPaddingException e) {
			throw new IllegalArgumentException("failed to apply prf", e);
		}

	}

	public static KeyPair generateKeyPair(byte[] seed) {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
			ECNamedCurveParameterSpec paramSpec = ECNamedCurveTable.getParameterSpec(Const.ECDH_CURVE);
			SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
			secureRandom.setSeed(seed);
			kpg.initialize(paramSpec, secureRandom);
			KeyPair kp = kpg.genKeyPair();
			return kp;

		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
			throw new IllegalStateException("failed to generate KeyPair", e);
		}
	}

	public static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
			ECNamedCurveParameterSpec paramSpec = ECNamedCurveTable.getParameterSpec(Const.ECDH_CURVE);
			// ECGenParameterSpec paramSpec = new ECGenParameterSpec("secp256k1");
			kpg.initialize(paramSpec);

			KeyPair kp = kpg.genKeyPair();
			return kp;

		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
			throw new IllegalStateException("failed to generate KeyPair", e);
		}
	}

	public static Certificate generateCertificate(KeyPair kp, long producerId) {

		Instant startInstance = Instant.now();
		Instant expiryInstance = startInstance.plus(Const.CERTIFICATE_VALIDITY_DAYS, ChronoUnit.DAYS);
		BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());

		X500Name issuer = new X500Name(RFC4519Style.INSTANCE, "CN=test,O=knu");

		X500Name subject = new X500NameBuilder().addRDN(BCStyle.C, Const.MY_CERTIFICATE_COUNTRY_NAME)
				.addRDN(BCStyle.O, Const.MY_CERTIFICATE_ORGANIZATION)
				.addRDN(BCStyle.OU, Const.MY_CERTIFICATE_ORGANIZATIONAL_UNIT)
				.addRDN(BCStyle.DN_QUALIFIER, Const.MY_CERTIFICATE_DISTINGUISHED_NAME)
				.addRDN(BCStyle.ST, Const.MY_CERTIFICATE_STATE)
				.addRDN(BCStyle.CN, String.format(Const.MY_CERTIFICATE_COMMON_NAME_TEMPLATE, producerId))
				.addRDN(BCStyle.SERIALNUMBER, String.format(Const.MY_CERTIFICATE_SERIAL_NUMBER_TEMPLATE, producerId))
				.build();

		X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(issuer, serialNumber,
				Date.from(startInstance), Date.from(expiryInstance), subject,
				SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded()));

		JcaContentSignerBuilder builder = new JcaContentSignerBuilder("SHA1withECDSA")
				.setProvider(BouncyCastleProvider.PROVIDER_NAME);

		try {
			ContentSigner signer = builder.build(kp.getPrivate());

			byte[] certBytes = certBuilder.build(signer).getEncoded();

			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			Certificate cert = certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));

			return cert;

		} catch (OperatorCreationException | IOException | CertificateException e) {
			throw new IllegalStateException("cannot generate certificate", e);
		}

	}

	public static SecretKey generateKey() {
		try {
			SecureRandom secureRandom = new SecureRandom();
			int keyBitSize = 256;
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");

			keyGenerator.init(keyBitSize, secureRandom);
			SecretKey secretKey = keyGenerator.generateKey();
			return secretKey;

		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("failed to generate key");
		}

	}

	public static List<SecretKey> generateKeys(int number) {
		try {
			SecureRandom secureRandom = new SecureRandom();
			int keyBitSize = 256;
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");

			keyGenerator.init(keyBitSize, secureRandom);

			List<SecretKey> secretKeys = new ArrayList<>(number);

			for (int i = 0; i < number; i++) {
				SecretKey secretKey = keyGenerator.generateKey();
				secretKeys.add(secretKey);

			}

			return secretKeys;

		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("failed to generate key");
		}

	}

	public static SecretKey generateKey(byte[] seed) {
		try {
			SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
			secureRandom.setSeed(seed);
			int keyBitSize = 256;
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");

			keyGenerator.init(keyBitSize, secureRandom);
			SecretKey secretKey = keyGenerator.generateKey();
			return secretKey;

		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("failed to generate key");
		}

	}

}
