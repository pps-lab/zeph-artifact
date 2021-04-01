package ch.ethz.infk.pps.shared.avro.util;

import ch.ethz.infk.pps.shared.avro.Window;

public class FileStoreNames {

	public static String OUTPUT_FOLDER = "./out/";

	public static String getSharedKeyFileName(long producerId) {
		return OUTPUT_FOLDER + String.format("shared-keys-p%d.avro", producerId);
	}

	public static String getDummyKeySumFileName(long universeId, long producerId, Window universeWindow,
			Window startWindow, Window endWindow) {
		return OUTPUT_FOLDER + String.format("dummy-key-sums-u%d-p%d-w%d-start%d-end%d.avro", universeId, producerId,
				universeWindow.getStart(), startWindow.getStart(), endWindow.getStart());
	}

	public static String getCertificateFileName(long universeId) {
		return OUTPUT_FOLDER + String.format("certificates-u%d.avro", universeId);
	}

	public static String getSharedKeyId(long producerId) {
		return "sk" + producerId;
	}

	public static String getTransformationTokenId(long producerId) {
		return "dks" + producerId;
	}

}
