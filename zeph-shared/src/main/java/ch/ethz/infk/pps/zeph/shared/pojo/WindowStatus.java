package ch.ethz.infk.pps.zeph.shared.pojo;

public enum WindowStatus {
	OPEN(1), STAGED(2), COMMITTED(3), MERGED(4), CLOSED(5);

	private Integer statusCode;

	WindowStatus(int statusCode) {
		this.statusCode = statusCode;
	}

	public boolean isBefore(WindowStatus o) {
		return statusCode < o.statusCode;
	}

	public boolean isAfter(WindowStatus o) {
		return statusCode > o.statusCode;
	}

	public int code() {
		return statusCode;
	}

	public static WindowStatus of(int statusCode) {

		switch (statusCode) {
		case 1:
			return OPEN;
		case 2:
			return STAGED;
		case 3:
			return COMMITTED;
		case 4:
			return MERGED;
		case 5:
			return CLOSED;
		default:
			throw new IllegalArgumentException("unknown window status");

		}

	}

}
