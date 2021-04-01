package ch.ethz.infk.pps.zeph.shared.pojo;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import javax.crypto.SecretKey;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;

public class ProducerIdentity {

	private Certificate certificate;
	private PrivateKey privateKey;
	private SecretKey heacKey;
	private SecretKey sharedKeysFileKey;
	private SecretKey tokensFileKey;

	public static ProducerIdentity generate(long producerId) {
		KeyPair kp = CryptoUtil.generateKeyPair();
		Certificate certificate = CryptoUtil.generateCertificate(kp, producerId);
		SecretKey sharedKeysFileKey = CryptoUtil.generateKey();
		SecretKey tokensFileKey = CryptoUtil.generateKey();
		SecretKey heacKey = CryptoUtil.generateKey();

		ProducerIdentity identity = new ProducerIdentity()
				.withCertificate(certificate)
				.withPrivateKey(kp.getPrivate())
				.withHeacKey(heacKey)
				.withSharedKeysFileKey(sharedKeysFileKey)
				.withTokensFileKey(tokensFileKey);

		return identity;
	}

	public static ProducerIdentity generate(long producerId, byte[] seed) {
		KeyPair kp = CryptoUtil.generateKeyPair(seed);
		Certificate certificate = CryptoUtil.generateCertificate(kp, producerId);
		SecretKey sharedKeysFileKey = CryptoUtil.generateKey();
		SecretKey tokensFileKey = CryptoUtil.generateKey();
		SecretKey heacKey = CryptoUtil.generateKey(seed);

		ProducerIdentity identity = new ProducerIdentity()
				.withCertificate(certificate)
				.withPrivateKey(kp.getPrivate())
				.withHeacKey(heacKey)
				.withSharedKeysFileKey(sharedKeysFileKey)
				.withTokensFileKey(tokensFileKey);

		return identity;
	}

	public ProducerIdentity withCertificate(Certificate certificate) {
		this.certificate = certificate;
		return this;
	}

	public ProducerIdentity withPrivateKey(PrivateKey privateKey) {
		this.privateKey = privateKey;
		return this;
	}

	public ProducerIdentity withHeacKey(SecretKey heacKey) {
		this.heacKey = heacKey;
		return this;
	}

	public ProducerIdentity withSharedKeysFileKey(SecretKey sharedKeysFileKey) {
		this.sharedKeysFileKey = sharedKeysFileKey;
		return this;
	}

	public ProducerIdentity withTokensFileKey(SecretKey tokensFileKey) {
		this.tokensFileKey = tokensFileKey;
		return this;
	}

	public Certificate getCertificate() {
		return certificate;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public SecretKey getHeacKey() {
		return heacKey;
	}

	public SecretKey getSharedKeysFileKey() {
		return sharedKeysFileKey;
	}

	public SecretKey getTokensFileKey() {
		return tokensFileKey;
	}
}
