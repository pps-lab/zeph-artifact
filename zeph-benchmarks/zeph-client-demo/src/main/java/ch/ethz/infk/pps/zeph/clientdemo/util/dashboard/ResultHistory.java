package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import ch.ethz.infk.pps.zeph.shared.DigestOp;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Window;

public class ResultHistory {

	private JSONObject object;

	public ResultHistory(TreeMap<Window, Digest> data, String authToken) {
		object = new JSONObject();
		object.put("auth_token", authToken);

		JSONArray points = new JSONArray();
		points.put(getHeader());

		data.entrySet().stream().forEach(e -> {

			Window window = e.getKey();
			int windowId = Dashboard.getWindowIdDay(window);
			String id = "W" + windowId + "\n" + Dashboard.formatWindow(window);
			Digest digest = e.getValue();
			JSONArray point = getPoint(id, DigestOp.getMean(digest), DigestOp.getStdDev(digest));
			points.put(point);
		});

		object.put("points", points);
	}

	public ResultHistory(String authToken) {
		object = new JSONObject();
		object.put("auth_token", authToken);

		JSONArray points = new JSONArray();
		points.put(getHeader());
		points.put(getPoint("", 0.0, 0.0));
		object.put("points", points);
	}

	public String toJsonString() {
		return object.toString(0);
	}

	private JSONArray getPoint(String id, double mean, double variance) {
		JSONArray point = new JSONArray();
		point.put(id);
		point.put(mean);
		point.put(mean - variance / 2);
		point.put(mean + variance / 2);

		return point;

	}

	private JSONArray getHeader() {
		JSONArray header = new JSONArray();
		header.put("window");
		header.put("mean");

		JSONObject interval1 = new JSONObject();
		interval1.put("id", "i1");
		interval1.put("type", "number");
		interval1.put("role", "interval");

		JSONObject interval2 = new JSONObject();
		interval2.put("id", "i2");
		interval2.put("type", "number");
		interval2.put("role", "interval");

		header.put(interval1);
		header.put(interval2);

		return header;
	}

}
