package ch.ethz.infk.pps.shared.avro.util;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collections;

import org.apache.avro.util.ByteBufferInputStream;

public class X509CertificateConverter {

	public static Certificate fromBytes(ByteBuffer buffer) {
		try {
			InputStream inputStream = new ByteBufferInputStream(Collections.singletonList(buffer));
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			Certificate cert = certificateFactory.generateCertificate(inputStream);
			return cert;
		} catch (CertificateException e) {
			throw new IllegalArgumentException("failed to create cert", e);
		}

	}

	public static ByteBuffer fromBytes(Certificate certificate) {
		try {
			return ByteBuffer.wrap(certificate.getEncoded());
		} catch (CertificateEncodingException e) {
			throw new IllegalArgumentException("cannot convert certificate", e);
		}
	}

}
