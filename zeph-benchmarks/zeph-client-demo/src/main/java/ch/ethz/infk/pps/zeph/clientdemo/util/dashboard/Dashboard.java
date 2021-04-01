package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Map.Entry;
import java.util.TimeZone;

import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.HostInfo;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class Dashboard {

	private String baseUri;

	public Dashboard(HostInfo hostInfo) {
		this.baseUri = hostInfo.getPath();
	}

	public String getUniverseInfoUri() {
		return "http://" + baseUri + "/widgets/universe-info";
	}

	public String getStatusHistoryUri() {
		return "http://" + baseUri + "/widgets/status-history";
	}

	public String getResultHistoryUri() {
		return "http://" + baseUri + "/widgets/result-history";
	}

	public String getResultLiveUri() {
		return "http://" + baseUri + "/widgets/result-live";
	}

	public String getMembershipHistoryUri() {
		return "http://" + baseUri + "/widgets/membership-history";
	}

	public static double getMean(Entry<Window, Digest> entry) {
		if (entry == null) {
			return 0.0;
		}
		return Math.round(DigestOp.getMean(entry.getValue()) * 100) / 100.0;
	}

	private static final long DAY_MILLIS = Duration.ofDays(1).toMillis();

	public static int getWindowIdDay(Window window) {
		long windowStart = window.getStart();
		long windowSize = window.getEnd() - windowStart;
		long dayStart = WindowUtil.getWindowStart(windowStart, DAY_MILLIS);
		return (int) ((windowStart - dayStart) / windowSize + 1);
	}

	public static String formatWindow(Window window) {
		DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = new Date(window.getStart());
		return formatter.format(date);
	}

}
