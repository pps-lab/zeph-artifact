package ch.ethz.infk.pps.shared.avro.util;

import java.io.File;
import java.io.IOException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.infk.pps.shared.avro.tuple.AESKey;
import ch.ethz.infk.pps.shared.avro.tuple.SharedKeyTuple;

public class SharedKeyFileStore {

	private static final Logger LOG = LoggerFactory.getLogger(SharedKeyFileStore.class);

	public static Map<Long, SecretKey> load(File file, String keyId, SecretKey fileDecryptionKey) {

		LOG.debug("load shared key file store: file={} keyId={}", file, keyId);

		if (fileDecryptionKey != null) {
			CodecFactory codec = new AESGCMCodec.Option(keyId, fileDecryptionKey);
			CodecFactory.addCodec("AES/GCM/" + keyId, codec);
		}

		Map<Long, SecretKey> sharedKeys = new HashMap<>();

		try {

			DatumReader<SharedKeyTuple> datumReader = new SpecificDatumReader<>(SharedKeyTuple.class);
			DataFileReader<SharedKeyTuple> dataFileReader = new DataFileReader<>(file, datumReader);

			SharedKeyTuple tuple = null;

			while (dataFileReader.hasNext()) {
				tuple = dataFileReader.next(tuple);
				SecretKeySpec key = new SecretKeySpec(tuple.getSharedKey().bytes(), "AES");
				sharedKeys.put(tuple.getProducerId(), key);
			}

			dataFileReader.close();

			return sharedKeys;

		} catch (IOException e) {
			throw new IllegalStateException("failed to load shared keys", e);
		}

	}

	public static void store(File file, String keyId, SecretKey fileEncryptionKey, Map<Long, SecretKey> data) {

		LOG.debug("store shared key file store: file={} keyId={}", file, keyId);

		DatumWriter<SharedKeyTuple> datumWriter = new SpecificDatumWriter<SharedKeyTuple>(SharedKeyTuple.class);
		DataFileWriter<SharedKeyTuple> dataFileWriter = new DataFileWriter<SharedKeyTuple>(datumWriter);

		if (fileEncryptionKey != null) {
			CodecFactory codec = new AESGCMCodec.Option(keyId, fileEncryptionKey);
			CodecFactory.addCodec("AES/GCM/" + keyId, codec);
			dataFileWriter.setCodec(codec);
		}

		try {
			dataFileWriter.create(SharedKeyTuple.getClassSchema(), file);

			data.forEach((pId, sharedKey) -> {
				try {
					byte[] sharedKeyBytes = sharedKey.getEncoded();

					SharedKeyTuple tuple = new SharedKeyTuple(pId, new AESKey(sharedKeyBytes));
					dataFileWriter.append(tuple);
				} catch (IOException e) {
					throw new IllegalStateException("failed to write shared keys to file", e);
				}

			});

			dataFileWriter.close();

		} catch (IOException e) {
			throw new IllegalStateException("failed to write shared keys to file", e);
		}
	}

}
