package ch.ethz.infk.pps.shared.avro.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import ch.ethz.infk.pps.shared.avro.tuple.CertificateTuple;

public class CertificateFileStore {

	public static Map<Long, Certificate> load(File file) {

		Map<Long, Certificate> certs = new HashMap<>();

		try {
			DatumReader<CertificateTuple> datumReader = new SpecificDatumReader<>(CertificateTuple.class);
			DataFileReader<CertificateTuple> dataFileReader = new DataFileReader<>(file, datumReader);

			CertificateTuple tuple = null;

			while (dataFileReader.hasNext()) {
				tuple = dataFileReader.next(tuple);

				Certificate cert = X509CertificateConverter.fromBytes(tuple.getCertificate());
				certs.put(tuple.getProducerId(), cert);
			}

			dataFileReader.close();

			return certs;

		} catch (IOException e) {
			throw new IllegalStateException("failed to load certificates");
		}

	}

	public static void store(File file, Map<Long, Certificate> data) {
		DatumWriter<CertificateTuple> datumWriter = new SpecificDatumWriter<>(CertificateTuple.class);
		DataFileWriter<CertificateTuple> dataFileWriter = new DataFileWriter<>(datumWriter);

		try {
			dataFileWriter.create(CertificateTuple.getClassSchema(), file);

			data.forEach((pId, cert) -> {
				try {

					ByteBuffer certBuffer = X509CertificateConverter.fromBytes(cert);
					CertificateTuple tuple = new CertificateTuple(pId, certBuffer);
					dataFileWriter.append(tuple);
				} catch (IOException e) {
					throw new IllegalStateException("failed to write certificates to file", e);
				}

			});

			dataFileWriter.close();

		} catch (IOException e) {
			throw new IllegalStateException("failed to write certificates to file", e);
		}
	}

}
