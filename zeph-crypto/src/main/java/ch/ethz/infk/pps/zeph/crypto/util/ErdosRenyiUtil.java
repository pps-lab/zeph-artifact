package ch.ethz.infk.pps.zeph.crypto.util;

public class ErdosRenyiUtil {

	public static void main(String[] args) {

		int N = 10000;
		double alpha = 0.5;
		double delta = 1.0e-5;

		int k = getK(N, alpha, delta);

		System.out.println(k);
	}

	/**
	 * @param N     aggregation size (minimum number of participants)
	 * @param alpha min fraction of non-malicious clients
	 * @param delta probability error bound
	 * @return
	 */
	public static Integer getK(int N, double alpha, double delta) {
		int n = (int) (alpha * N);

		for (int k = 1; k < 32; k++) {

			int w = 1 << k;
			int W = 128 / k * w;
			double p = 1.0 / w;

			double failBound = getFailProbabilityBound(W, n, p);

			if (failBound > delta) {
				if (k - 1 > 0) {
					return k - 1;
				} else {
					return null;
				}
			}
		}

		return null;
	}

	/**
	 * @param k
	 * @return the number of graphs which can be constructed with a given k (i.e.
	 *         the number of times secure aggregation can be performed before new
	 *         graphs need to be constructed)
	 */
	public static int getNumberOfGraphs(int k) {
		int w = 1 << k;
		int W = 128 / k * w;
		return W;
	}

	/**
	 * 
	 * @param N aggregation size (non-malicious and malicious together)
	 * @param k
	 * @return expected number of neighbours in the graph
	 */
	public static double getExpectedDegree(int N, int k) {
		int w = 1 << k;
		return (N - 1) / (double) w;
	}

	/**
	 * 
	 * @param W number of graphs
	 * @param n minimum number of non-malicious clients
	 * @param p probability that an edge is included
	 * @return union bound over the event that at least one of the W graphs over the
	 *         non-malicious clients is disconnected
	 */
	public static double getFailProbabilityBound(int W, int n, double p) {
		return W * getErdosRenyiProbabilityBound(n, p);
	}

	private static double getErdosRenyiProbabilityBound(int n, double p) {
		double bound = 0.0;
		int limit = n / 2;
		for (int j = 1; j <= limit; j++) {
			double uj = Math.pow((Math.E * n) / j * Math.pow(1 - p, n - j), j);
			bound += uj;
		}
		return bound;
	}

}
