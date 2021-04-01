package ch.ethz.infk.pps.zeph.shared.avro.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import javax.crypto.SecretKey;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ch.ethz.infk.pps.zeph.shared.CryptoUtil;
import ch.ethz.infk.pps.shared.avro.util.SharedKeyFileStore;

public class SharedKeyFileStoreTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testSharedKeyFileStore() throws IOException {

		int nData = 5;
		final File tempFile = tempFolder.newFile("shared-key.avro");

		// generate test data
		SecretKey fileKey = CryptoUtil.generateKey();
		String keyId = "s1";

		Map<Long, SecretKey> data = new HashMap<>();
		for (int i = 0; i < nData; i++) {
			data.put((long) i, CryptoUtil.generateKey());
		}

		SharedKeyFileStore.store(tempFile, keyId, fileKey, data);

		Map<Long, SecretKey> result = SharedKeyFileStore.load(tempFile, keyId, fileKey);

		assertEquals("result size", data.size(), result.size());

		result.forEach((pId, key) -> assertTrue("key: " + pId, key.equals(data.get(pId))));

	}

	@Test
	public void testConcurrentSharedKeyFileStore() throws IOException {

		int n = 10;
		int nData = 20;

		File[] tempFiles = new File[n];
		for (int i = 0; i < n; i++) {
			tempFiles[i] = tempFolder.newFile("shared-key" + i + ".avro");
		}

		SecretKey[] fileKeys = new SecretKey[n];
		for (int i = 0; i < n; i++) {
			fileKeys[i] = CryptoUtil.generateKey();
		}

		ConcurrentMap<Long, SecretKey> data = new ConcurrentHashMap<>();
		for (int i = 0; i < nData; i++) {
			data.put((long) i, CryptoUtil.generateKey());
		}

		IntStream.range(0, n).parallel().forEach(i -> {
			SharedKeyFileStore.store(tempFiles[i], "s" + i, fileKeys[i], data);
		});

		IntStream.range(0, n).parallel().forEach(i -> {
			Map<Long, SecretKey> result = SharedKeyFileStore.load(tempFiles[i], "s" + i, fileKeys[i]);
			assertEquals("result size", data.size(), result.size());
			result.forEach((pId, key) -> assertTrue("key: " + pId, key.equals(data.get(pId))));
		});

	}

}
