package ch.ethz.infk.pps.zeph.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.avro.Schema;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.HeacHeader;
import ch.ethz.infk.pps.shared.avro.Input;

public class DigestOp {

	public static void main(String[] args) {

		Input input = ApplicationAdapter.random();
		System.out.println("Input: " + input);

		System.out.println("\nEncoding     Number of Fields: " + DIGEST_AGG_FIELDS.size() + "   Number of Longs: " + getTotalNumberOfElements());

	}

	public static final List<String> DIGEST_AGG_FIELDS = Digest.getClassSchema().getFields().stream()
			.filter(field -> field.schema().getType() == Schema.Type.ARRAY).map(field -> field.name())
			.collect(Collectors.toList());
	public static final int NUM_DIGEST_AGG_FIELDS = DIGEST_AGG_FIELDS.size();

	public static long getTotalNumberOfElements(){
		Digest digest = DigestOp.empty();
		return DigestOp.DIGEST_AGG_FIELDS.stream().map(name -> {
			@SuppressWarnings("unchecked")
			List<Long> elements = (List<Long>) digest.get(name);
			return elements;
			})
			.flatMap(elements -> elements.stream())
			.count();
	}

	public static Digest add(Digest d1, Digest d2) {

		Digest result = DigestOp.empty();

		for (int i = 0; i < NUM_DIGEST_AGG_FIELDS; i++) {
			String fieldName = DIGEST_AGG_FIELDS.get(i);
			@SuppressWarnings("unchecked")
			List<Long> elements1 = (List<Long>) d1.get(fieldName);
			@SuppressWarnings("unchecked")
			List<Long> elements2 = (List<Long>) d2.get(fieldName);

			@SuppressWarnings("unchecked")
			List<Long> resultElements = (List<Long>) result.get(fieldName);

			final int nElements = elements1.size();
			assert nElements == elements2.size();

			for (int j = 0; j < nElements; j++) {
				long res = elements1.get(j) + elements2.get(j);
				resultElements.set(j, res);
			}
		}

		HeacHeader header = null;
		if (d1.getHeader() != null && d2.getHeader() != null) {
			long start = Math.min(d1.getHeader().getStart(), d2.getHeader().getStart());
			long end = Math.max(d1.getHeader().getEnd(), d2.getHeader().getEnd());
			header = new HeacHeader(start, end);
		}

		result.setHeader(header);
		return result;
	}

	public static Digest subtract(Digest d1, Digest d2) {
		Digest result = DigestOp.empty();

		for (int i = 0; i < NUM_DIGEST_AGG_FIELDS; i++) {
			String fieldName = DIGEST_AGG_FIELDS.get(i);
			@SuppressWarnings("unchecked")
			List<Long> elements1 = (List<Long>) d1.get(fieldName);
			@SuppressWarnings("unchecked")
			List<Long> elements2 = (List<Long>) d2.get(fieldName);

			@SuppressWarnings("unchecked")
			List<Long> resultElements = (List<Long>) result.get(fieldName);

			final int nElements = elements1.size();
			assert nElements == elements2.size();

			for (int j = 0; j < nElements; j++) {
				long res = elements1.get(j) - elements2.get(j);
				resultElements.set(j, res);
			}
		}

		result.setHeader(null);
		return result;
	}


	public static Long getCount(Digest digest){
		long count = digest.getCount().get(0);
		return count;
	}

	public static Long getSum(Digest digest){
	
		@SuppressWarnings("unchecked")
		List<Long> hist =  ((List<Long>) digest.get(ApplicationAdapter.MAIN_BASE_FIELD_NAME));
		
		long sum = formatHist(hist, ApplicationAdapter.MAIN_BASE_FIELD_BITS).get(0);

		return sum;
	}

	public static Double getMean(Digest digest) {
		double count = digest.getCount().get(0);
		if (count == 0.0) {
			return null;
		}
		@SuppressWarnings("unchecked")
		long sum = ((List<Long>) digest.get(ApplicationAdapter.MAIN_BASE_FIELD_NAME)).get(0);
		return sum / count;
	}

	public static Double getVariance(Digest digest) {
		double count = digest.getCount().get(0);
		if (count == 0.0) {
			return null;
		}
		@SuppressWarnings("unchecked")
		long sum = ((List<Long>) digest.get(ApplicationAdapter.MAIN_BASE_FIELD_NAME)).get(0);

		@SuppressWarnings("unchecked")
		long sumOfSquares = ((List<Long>) digest.get(ApplicationAdapter.MAIN_BASE_FIELD_NAME)).get(1);

		return sumOfSquares / count - Math.pow(sum / count, 2);
	}

	public static Double getStdDev(Digest digest) {
		Double variance = getVariance(digest);
		if (variance == null) {
			return null;
		}
		return Math.sqrt(variance);
	}

	public static void setHist(long value, long binValue, List<Long> hist, int bits, long minValue, Long maxValue) {
		int binsPerLong = 64 / bits;

		int idx = (int) ((value - minValue)/ binsPerLong );
		int shift = (int) ((value % binsPerLong) * bits);

		hist.set(idx, binValue << shift);
	}

	public static List<Long> formatHist(List<Long> hist, int bits) {

		if(bits==64){
			return hist;
		}

		List<Long> expandedHist = new ArrayList<>(hist.size() * 64 / bits);

		// lower "bits" number of bits are all 1, rest 0
		long mask = (1l << bits) - 1l;
		//System.out.println(Long.toBinaryString(mask));

		int numBinsPerLong = 64 / bits;
		assert 64 % bits == 0;
		for (Long elem : hist) {

			for (int i = 0; i < numBinsPerLong; i++) {
				expandedHist.add((elem >> (i * bits)) & mask);
			}
		}
		return expandedHist;
	}

	public static void setBase(long value, List<Long> base) {
		base.set(0, value); // sum
		base.set(1, (long) Math.pow(value, 2)); // sum of squares
	}

	public static Digest empty() {
		return ApplicationAdapter.emptyDigest();
	}

	public static Digest random() {
		Input value = ApplicationAdapter.random();
		return ApplicationAdapter.toDigest(value);
	}

	
	public static Digest of(long[] fields){

		Digest digest = DigestOp.empty();

		int fieldIdx = 0;
		
		for(int i = 0; i < NUM_DIGEST_AGG_FIELDS; i++){
			String fieldName = DigestOp.DIGEST_AGG_FIELDS.get(i);
			@SuppressWarnings("unchecked")
			List<Long> elements = (List<Long>) digest.get(fieldName);
			final int size = elements.size();
			for(int j=0; j < size; j++){
				elements.set(j, fields[fieldIdx]);
				fieldIdx++;
			}
		}
		return digest;
	}



}