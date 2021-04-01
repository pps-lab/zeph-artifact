package ch.ethz.infk.pps.zeph.clientdemo;

import java.io.File;

public class ClientDemoPath {

	public static String BASE = "";

	public static File getCertificatesFile() {
		File file = new File(BASE + "/master-data/global-certificates.avro");
		file.getParentFile().mkdirs();
		return file;
	}

	public static File getKeysFile() {
		File file = new File(BASE + "/master-data/local-producers.avro");
		file.getParentFile().mkdirs();
		return file;
	}

	public static File getSharedKeysFile(long producerId) {
		String path = String.format(BASE + "/master-data/producers/p%d/shared-keys-p%d.avro", producerId, producerId);
		File file = new File(path);
		file.getParentFile().mkdirs();
		return file;
	}

	public static String getGlobalKeystorePath() {
		return BASE + "/master-data/keystore.p12";

	}

	public static String getKeystorePath(long producerId) {
		String path = String.format(BASE + "/master-data/producers/p%d/keystore-p%d.avro", producerId, producerId);
		return path;
	}

}
