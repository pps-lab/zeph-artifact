package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

public class Result {

	private double current;
	private double last;
	private String moreInfo;
	private String auth_token;

	public Result() {
	}

	public Result(double current, double last, String moreInfo, String authToken) {
		this.current = current;
		this.last = last;
		this.moreInfo = moreInfo;
		this.auth_token = authToken;
	}

	public String getAuth_token() {
		return auth_token;
	}

	public void setAuth_token(String auth_token) {
		this.auth_token = auth_token;
	}

	public double getCurrent() {
		return current;
	}

	public void setCurrent(double current) {
		this.current = current;
	}

	public double getLast() {
		return last;
	}

	public void setLast(double last) {
		this.last = last;
	}

	public String getMoreInfo() {
		return moreInfo;
	}

	public void setMoreInfo(String moreInfo) {
		this.moreInfo = moreInfo;
	}

}
