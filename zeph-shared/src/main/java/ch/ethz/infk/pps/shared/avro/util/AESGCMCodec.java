package ch.ethz.infk.pps.shared.avro.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;

import org.apache.avro.file.Codec;
import org.apache.avro.file.CodecFactory;

public class AESGCMCodec extends Codec {

	static class Option extends CodecFactory {

		private final String keyId;
		private final SecretKey secretKey;

		public Option(String keyId, SecretKey secretKey) {
			this.keyId = keyId;
			this.secretKey = secretKey;
		}

		@Override
		protected Codec createInstance() {
			return new AESGCMCodec(keyId, secretKey);
		}
	}

	public static final int AES_KEY_SIZE = 256;
	public static final int GCM_IV_LENGTH = 12;
	public static final int GCM_TAG_LENGTH = 16;

	private final String keyId;

	private final SecretKey secretKey;
	private final Cipher encCipher;
	private GCMParameterSpec encGCMParameter;

	private final Cipher decCipher;
	private byte[] decIV;

	public AESGCMCodec(String keyId, SecretKey secretKey) {

		try {
			this.keyId = keyId;
			this.secretKey = secretKey;
			this.encCipher = Cipher.getInstance("AES/GCM/NoPadding");

			byte[] iv = new byte[GCM_IV_LENGTH];
			SecureRandom random = new SecureRandom();
			random.nextBytes(iv);
			this.encGCMParameter = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

			this.decCipher = Cipher.getInstance("AES/GCM/NoPadding");
			this.decIV = new byte[GCM_IV_LENGTH];

		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new IllegalStateException("failed to initialize GCMCodec");
		}
	}

	@Override
	public String getName() {
		return "AES/GCM/" + keyId;
	}

	@Override
	public ByteBuffer compress(ByteBuffer input) throws IOException {

		int inputSize = input.remaining();
		int outputSize = GCM_IV_LENGTH + GCM_TAG_LENGTH + inputSize;

		try {

			// increment iv and update gcm parameter
			byte[] iv = encGCMParameter.getIV();
			incrementIV(iv);
			encGCMParameter = new GCMParameterSpec(encGCMParameter.getTLen(), iv);

			// init the cipher
			encCipher.init(Cipher.ENCRYPT_MODE, secretKey, encGCMParameter);

			// write iv to byte buffer
			ByteBuffer output = ByteBuffer.allocate(outputSize);
			output.put(iv);

			// write encrypted input
			encCipher.doFinal(input, output);

			output.rewind();

			return output;

		} catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | ShortBufferException e) {
			throw new IllegalStateException("failed", e);
		}

	}

	@Override
	public ByteBuffer decompress(ByteBuffer input) throws IOException {

		try {

			// read iv from beginning of block
			input.get(decIV, 0, decIV.length);

			// init cipher
			GCMParameterSpec decGCMParameter = new GCMParameterSpec(GCM_TAG_LENGTH * 8, decIV);
			decCipher.init(Cipher.DECRYPT_MODE, secretKey, decGCMParameter);

			// decrypt the block
			byte[] output = decCipher.doFinal(input.array(), input.position(), input.remaining());

			return ByteBuffer.wrap(output);

		} catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException e) {
			throw new IllegalStateException("failed", e);
		}

	}

	private void incrementIV(byte[] iv) {
		int length = iv.length;
		boolean carry = true;

		for (int i = 0; i < length; i++) {
			if (carry) {
				iv[i] = (byte) ((iv[i] + 1) & 0xFF);
				carry = 0 == iv[i];
			} else {
				break;
			}
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((decCipher == null) ? 0 : decCipher.hashCode());
		result = prime * result + ((encCipher == null) ? 0 : encCipher.hashCode());
		result = prime * result + ((encGCMParameter == null) ? 0 : encGCMParameter.hashCode());
		result = prime * result + ((secretKey == null) ? 0 : secretKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		AESGCMCodec other = (AESGCMCodec) obj;
		if (decCipher == null) {
			if (other.decCipher != null) return false;
		} else if (!decCipher.equals(other.decCipher)) return false;
		if (encCipher == null) {
			if (other.encCipher != null) return false;
		} else if (!encCipher.equals(other.encCipher)) return false;
		if (encGCMParameter == null) {
			if (other.encGCMParameter != null) return false;
		} else if (!encGCMParameter.equals(other.encGCMParameter)) return false;
		if (secretKey == null) {
			if (other.secretKey != null) return false;
		} else if (!secretKey.equals(other.secretKey)) return false;
		return true;
	}

}
