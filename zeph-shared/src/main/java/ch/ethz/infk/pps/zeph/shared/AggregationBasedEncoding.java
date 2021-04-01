package ch.ethz.infk.pps.zeph.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public class AggregationBasedEncoding {

	public static List<Long> encodeSum(long value) {
		return Collections.singletonList(value);
	}

	public static long decodeSum(List<Long> encoding) {
		return encoding.get(0);
	}

	public static List<Long> encodeCount(long value) {
		return Collections.singletonList(1l);
	}

	public static long decodeCount(List<Long> encoding) {
		return encoding.get(0);
	}

	public static List<Long> encodeAvg(long value) {
		return Arrays.asList(value, 1l);
	}

	public static double decodeAvg(List<Long> encoding) {
		return encoding.get(0) / (double) encoding.get(1);
	}

	public static List<Long> encodeVariance(long value) {
		return Arrays.asList(value, (long) Math.pow(value, 2.0), 1l);
	}

	public static double decodeVariance(List<Long> encoding) {
		double n = encoding.get(2);
		return (encoding.get(1) / n - Math.pow(encoding.get(0) / n, 2));
	}

	public static List<Long> encodeStandardDeviation(long value) {
		return encodeVariance(value);
	}

	public static double decodeStandardDeviation(List<Long> encoding) {
		return Math.sqrt(decodeVariance(encoding));
	}

	public static List<Long> encodeHist(long value, List<Long> bucketBoundaries) {
		int numBoundaries = bucketBoundaries.size();
		List<Long> buckets = new ArrayList<Long>(numBoundaries + 1);
		boolean isFound = false;
		for (int i = 0; i < numBoundaries; i++) {
			if (!isFound && value < bucketBoundaries.get(i)) {
				isFound = true;
				buckets.add(1l);
			} else {
				buckets.add(0l);
			}
		}

		if (isFound) {
			buckets.add(0l);
		} else {
			buckets.add(1l);
		}
		return buckets;
	}

	public static List<Long> decodeHist(List<Long> encoding) {
		return encoding;
	}

	public static List<Long> encodeMin(long value, List<Long> bucketBoundaries) {
		return encodeHist(value, bucketBoundaries);
	}

	public static int decodeMin(List<Long> encoding) {
		int buckets = encoding.size();
		for (int i = 0; i < buckets; i++) {
			if (encoding.get(i) > 0) {
				return i;
			}
		}
		return -1;
	}

	public static List<Long> encodeMax(long value, List<Long> bucketBoundaries) {
		return encodeHist(value, bucketBoundaries);
	}

	public static int decodeMax(List<Long> encoding) {
		int buckets = encoding.size();
		for (int i = buckets - 1; i >= 0; i--) {
			if (encoding.get(i) > 0) {
				return i;
			}
		}
		return -1;
	}

	public static List<Long> encodeRegression(long x, long y) {
		return Arrays.asList(x, (long) Math.pow(x, 2.0), y, x * y, 1l);
	}

	public static List<Double> decodeRegression(List<Long> encoding) {

		long x = encoding.get(0);
		long x2 = encoding.get(1);
		long y = encoding.get(2);
		long xy = encoding.get(3);
		long n = encoding.get(4);

		RealMatrix coefficients = new Array2DRowRealMatrix(new double[][] { { n, x }, { x, x2 } }, false);
		DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();

		RealVector constants = new ArrayRealVector(new double[] { y, xy }, false);
		RealVector solution = solver.solve(constants);

		double a0 = solution.getEntry(0);
		double a1 = solution.getEntry(0);

		return Arrays.asList(a0, a1);
	}

}
