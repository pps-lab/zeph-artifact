package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

import java.util.List;

public class Timeline {

	private String auth_token = "YOUR_AUTH_TOKEN";
	private List<TimelineEvent> events;

	public Timeline() {
	}

	public Timeline(List<TimelineEvent> events) {
		this.events = events;
	}

	public String getAuth_token() {
		return auth_token;
	}

	public void setAuth_token(String auth_token) {
		this.auth_token = auth_token;
	}

	public List<TimelineEvent> getEvents() {
		return events;
	}

	public void setEvents(List<TimelineEvent> events) {
		this.events = events;
	}

}
