package ch.ethz.infk.pps.shared.avro.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import ch.ethz.infk.pps.zeph.shared.pojo.ProducerIdentity;
import ch.ethz.infk.pps.shared.avro.tuple.AESKey;
import ch.ethz.infk.pps.shared.avro.tuple.ProducerKeyTuple;

public class ProducerKeyFileStore {

	public static Map<Long, ProducerIdentity> load(File file, SecretKey fileDecryptionKey) {
		Map<Long, ProducerIdentity> result = new HashMap<>();

		if (fileDecryptionKey != null) {
			CodecFactory codec = new AESGCMCodec.Option("all", fileDecryptionKey);
			CodecFactory.addCodec("AES/GCM/all", codec);
		}

		try {

			DatumReader<ProducerKeyTuple> datumReader = new SpecificDatumReader<>(ProducerKeyTuple.class);
			DataFileReader<ProducerKeyTuple> dataFileReader = new DataFileReader<>(file, datumReader);

			ProducerKeyTuple tuple = null;
			KeyFactory kf = KeyFactory.getInstance("EC");

			while (dataFileReader.hasNext()) {
				tuple = dataFileReader.next(tuple);

				long pId = tuple.getProducerId();
				Certificate certificate = X509CertificateConverter.fromBytes(tuple.getCertificate());

				PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(tuple.getPrivateKey().array()));
				SecretKeySpec tokensFileKey = new SecretKeySpec(tuple.getDummyFileKey().bytes(), "AES");
				SecretKeySpec sharedKeysFileKey = new SecretKeySpec(tuple.getSharedFileKey().bytes(), "AES");
				SecretKeySpec heacKey = new SecretKeySpec(tuple.getHeacKey().bytes(), "AES");

				ProducerIdentity info = new ProducerIdentity()
						.withCertificate(certificate)
						.withHeacKey(heacKey)
						.withPrivateKey(privateKey)
						.withSharedKeysFileKey(sharedKeysFileKey)
						.withTokensFileKey(tokensFileKey);

				result.put(pId, info);
			}

			dataFileReader.close();
			return result;

		} catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("failed to load producer key tuple", e);
		}

	}

	public static void store(File file, SecretKey fileEncryptionKey, Map<Long, ProducerIdentity> producerInfos) {

		DatumWriter<ProducerKeyTuple> datumWriter = new SpecificDatumWriter<>(ProducerKeyTuple.class);
		DataFileWriter<ProducerKeyTuple> dataFileWriter = new DataFileWriter<>(datumWriter);

		if (fileEncryptionKey != null) {
			CodecFactory codec = new AESGCMCodec.Option("all", fileEncryptionKey);
			CodecFactory.addCodec("AES/GCM/all", codec);
			dataFileWriter.setCodec(codec);
		}

		try {
			dataFileWriter.create(ProducerKeyTuple.getClassSchema(), file);

			producerInfos.forEach((pId, info) -> {
				try {

					ProducerKeyTuple tuple = new ProducerKeyTuple(pId,
							ByteBuffer.wrap(info.getCertificate().getEncoded()),
							ByteBuffer.wrap(info.getPrivateKey().getEncoded()),
							new AESKey(info.getSharedKeysFileKey().getEncoded()),
							new AESKey(info.getTokensFileKey().getEncoded()),
							new AESKey(info.getHeacKey().getEncoded()));

					dataFileWriter.append(tuple);

				} catch (IOException | CertificateEncodingException e) {
					throw new IllegalStateException("failed to write producers to file", e);
				}

			});

			dataFileWriter.close();

		} catch (IOException e) {
			throw new IllegalStateException("failed to write producers to file", e);
		}
	}

}
