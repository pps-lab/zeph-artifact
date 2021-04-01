package ch.ethz.infk.pps.zeph.crypto;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;

public class DiffieHellmanKeyExchange {

	public SecretKey generateSharedKey(PrivateKey privateKey, Certificate certificate) {
		try {
			KeyAgreement ecdh = KeyAgreement.getInstance("ECDH", "BC");
			ecdh.init(privateKey);
			ecdh.doPhase(certificate.getPublicKey(), true);
			SecretKey sharedKey = ecdh.generateSecret("AES");
			return sharedKey;
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
			throw new IllegalStateException("failed to generate the missing shared keys", e);
		}
	}

	public static void main(String[] args) throws CertificateEncodingException {
		KeyPair kp = CryptoUtil.generateKeyPair();
		System.out.println("Private Key Encoded: " + kp.getPrivate().getEncoded().length);
		System.out.println("Public Key Encoded: " + kp.getPublic().getEncoded().length);

		Certificate cert = CryptoUtil.generateCertificate(CryptoUtil.generateKeyPair(), 1l);
		System.out.println("Certificate Encoded: " + cert.getEncoded().length);

		DiffieHellmanKeyExchange ecdh = new DiffieHellmanKeyExchange();
		SecretKey sharedKey = ecdh.generateSharedKey(kp.getPrivate(), cert);

		System.out.println("Shared Key Encoded: " + sharedKey.getEncoded().length);

	}

}
