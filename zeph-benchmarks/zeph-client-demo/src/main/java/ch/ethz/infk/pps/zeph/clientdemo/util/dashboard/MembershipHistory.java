package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import ch.ethz.infk.pps.zeph.shared.pojo.ImmutablePair;
import ch.ethz.infk.pps.shared.avro.Window;

public class MembershipHistory {

	private JSONObject object;

	public MembershipHistory(TreeMap<Window, ImmutablePair<Integer, String>> membership, int threshold,
			String authToken) {

		object = new JSONObject();
		object.put("auth_token", authToken);

		JSONArray points = new JSONArray();
		points.put(getHeader());
		points.put(getBoundary(threshold));

		membership.entrySet().stream().forEach(e -> {
			Window window = e.getKey();
			String id = "W" + Dashboard.getWindowIdDay(window);
			points.put(getPoint(id, e.getValue().getLeft(), threshold, e.getValue().getRight()));
		});

		points.put(getBoundary(threshold));

		object.put("points", points);
	}

	public String toJsonString() {
		return object.toString(0);
	}

	private JSONArray getBoundary(int threshold) {
		JSONArray point = new JSONArray();
		point.put("");
		point.put(0);
		point.put("");
		point.put(threshold);
		return point;
	}

	private JSONArray getPoint(String id, int universeSize, int threshold, String diffStr) {
		JSONArray point = new JSONArray();
		point.put(id);
		point.put(universeSize);
		point.put(diffStr);
		point.put(threshold);
		return point;
	}

	private JSONArray getHeader() {
		JSONArray header = new JSONArray();
		header.put("window");
		header.put("Universe Size");

		JSONObject diffTooltip = new JSONObject();
		diffTooltip.put("type", "string");
		diffTooltip.put("role", "tooltip");
		header.put(diffTooltip);

		header.put("Mimimum Size");

		return header;
	}

}
