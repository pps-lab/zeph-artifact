package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TimelineEvent {

	private String name;
	private String date;
	private String background;
	private Double opacity;
	private Integer lineWidth = 0;

	@JsonIgnore
	public TimelineEvent withName(String name) {
		this.name = name;
		return this;
	}

	@JsonIgnore
	public TimelineEvent withDate(long timestamp) {
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = new Date(timestamp);
		this.date = formatter.format(date);
		return this;
	}

	@JsonIgnore
	public TimelineEvent withBackground(Color color) {
		this.background = color.hex();
		return this;
	}

	@JsonIgnore
	public TimelineEvent withOpacity(double opacity) {
		this.opacity = opacity;
		return this;
	}

	@JsonIgnore
	public TimelineEvent withLineWidth(int lineWidth) {
		this.lineWidth = lineWidth;
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public String getBackground() {
		return background;
	}

	public void setBackground(String background) {
		this.background = background;
	}

	public Double getOpacity() {
		return opacity;
	}

	public void setOpacity(Double opacity) {
		this.opacity = opacity;
	}

	public Integer getLineWidth() {
		return lineWidth;
	}

	public void setLineWidth(Integer lineWidth) {
		this.lineWidth = lineWidth;
	}

}
