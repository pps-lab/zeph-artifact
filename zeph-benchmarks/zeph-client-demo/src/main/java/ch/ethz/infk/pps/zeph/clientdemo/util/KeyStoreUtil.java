package ch.ethz.infk.pps.zeph.clientdemo.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyStoreUtil {

	private static final Logger LOG = LoggerFactory.getLogger(KeyStoreUtil.class);

	public static KeyStore buildKeyStore(String path, char[] pwd, String type) {

		try {
			KeyStore ks = KeyStore.getInstance(type);

			try (FileInputStream in = new FileInputStream(path)) {
				ks.load(in, pwd);
			} catch (FileNotFoundException e) {
				LOG.info("KeyStore not found -> Creating new KeyStore");
				ks.load(null, pwd);
			}
			return ks;

		} catch (NoSuchAlgorithmException | CertificateException | IOException | KeyStoreException e) {
			throw new IllegalStateException("failed to build KeyStore", e);
		}

	}

	public static void flushKeyStore(KeyStore ks, String ksPath, char[] pwd) {

		try (FileOutputStream fos = new FileOutputStream(ksPath)) {
			ks.store(fos, pwd);
		} catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
			throw new IllegalArgumentException("failed to store KeyStore", e);
		}

		LOG.info("stored keystore to disk: ks={}", ksPath);

	}

}
