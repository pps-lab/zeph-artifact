package ch.ethz.infk.pps.zeph.crypto.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.primitives.Longs;

import ch.ethz.infk.pps.zeph.shared.pojo.ImmutableUnorderedPair;

public class SecureAggregationPartitioner {

	private static final int PRF_OUTPUT_SIZE_BITS = 128;

	private final int bucketBits;
	private final int numBuckets;
	private final int expectedKeys;

	private ConcurrentHashMap<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys = null;

	public SecureAggregationPartitioner(int universeMemberThreshold, double alpha, double delta) {

		this.bucketBits = ErdosRenyiUtil.getK(universeMemberThreshold, alpha, delta);
		this.numBuckets = 1 << this.bucketBits;
		this.expectedKeys = PRF_OUTPUT_SIZE_BITS / bucketBits;

	}

	public void addSharedKeys(Map<ImmutableUnorderedPair<Long>, SecretKey> sharedKeys) {
		if (this.sharedKeys == null) {
			this.sharedKeys = new ConcurrentHashMap<>(sharedKeys.size());
		}
		this.sharedKeys.putAll(sharedKeys);
	}

	public Multimap<Integer, Long> getPartitions(long epoch, long producerId, Set<Long> universeMemberIds) {

		int expectedValuesPerKey = (int) (1 / (double) this.numBuckets * universeMemberIds.size()) + 20;

		Multimap<Integer, Long> partitions = MultimapBuilder.hashKeys(this.expectedKeys)
				.hashSetValues(expectedValuesPerKey)
				.build();

		final byte[] epochBytes = Longs.toByteArray(epoch);

		final int size = universeMemberIds.size();
		Iterator<Long> iter = universeMemberIds.iterator();
		for (int i = 0; i < size; i++) {
			long otherProducerId = iter.next();

			if (producerId != otherProducerId) {
				ImmutableUnorderedPair<Long> pair = ImmutableUnorderedPair.of(producerId, otherProducerId);
				SecretKey sharedKey = this.sharedKeys.get(pair);

				if (sharedKey == null) {
					throw new IllegalArgumentException("not implemented yet: unknown shared key");
				}

				List<Short> buckets = getBuckets(epochBytes, sharedKey);
				Iterator<Short> bucketIter = buckets.iterator();
				int nBuckets = buckets.size();

				for (int b = 0; b < nBuckets; b++) {
					int key = b * this.numBuckets + bucketIter.next();
					partitions.put(key, otherProducerId);
				}
			}
		}

		return partitions;
	}

	private List<Short> getBuckets(byte[] epochBytes, SecretKey sharedKey) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, sharedKey);
			byte[] input = cipher.doFinal(epochBytes);
			List<Short> buckets = parseBuckets(input);
			return buckets;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
				| BadPaddingException e) {
			throw new IllegalStateException("failed", e);
		}
	}

	private List<Short> parseBuckets(byte[] prfOutput) {

		// construct 0...001111111 mask
		final long mask = -1 >>> (Long.SIZE - this.bucketBits);

		final int shiftCount = Long.SIZE / this.bucketBits;
		final int remaining = Long.SIZE % this.bucketBits;

		assert (prfOutput.length == 16);

		try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(prfOutput))) {

			// split 128 bit output of the prf in two parts
			long part1 = in.readLong();
			long part2 = in.readLong();

			List<Short> buckets = new ArrayList<>(PRF_OUTPUT_SIZE_BITS / this.bucketBits);

			// parse the first 64 bits of the prf output into bucket assignments
			for (int i = 0; i < shiftCount; i++) {
				short bucket = (short) (part1 & mask);
				buckets.add(bucket);
				part1 = part1 >>> this.bucketBits;
			}

			// parse the second 64 bits of the prf output into bucket assignments
			for (int i = 0; i < shiftCount; i++) {
				short bucket = (short) (part2 & mask);
				buckets.add(bucket);
				part2 = part2 >>> this.bucketBits;
			}

			// combine the remaining bits of the two parts into a bucket assignment
			if (2 * remaining >= this.bucketBits) {
				part1 = part1 << remaining;
				long part = part1 | part2;
				short bucket = (short) (part & mask);
				buckets.add(bucket);
			}

			return buckets;

		} catch (final IOException e) {
			throw new RuntimeException("failed", e);
		}
	}

}
