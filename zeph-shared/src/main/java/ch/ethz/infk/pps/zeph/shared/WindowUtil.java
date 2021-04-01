package ch.ethz.infk.pps.zeph.shared;

import ch.ethz.infk.pps.shared.avro.Window;

public class WindowUtil {

	/**
	 * Method to get the window start for a timestamp.
	 */
	public static long getWindowStart(long timestamp, long windowSize) {
		return timestamp - (timestamp + windowSize) % windowSize;
	}

	private static String format = "W[%d, %d)";

	public static String f(Window window) {
		return String.format(format, window.getStart(), window.getEnd());

	}

	public static String f(long start, long sizeMillis) {
		return String.format(format, start, start + sizeMillis);

	}

}
