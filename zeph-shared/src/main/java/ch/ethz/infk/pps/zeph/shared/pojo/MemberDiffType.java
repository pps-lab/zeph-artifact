package ch.ethz.infk.pps.zeph.shared.pojo;

public enum MemberDiffType {
	ADD(1), DEL(-1);

	private Integer code;

	MemberDiffType(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}

	public static MemberDiffType of(int code) {

		switch (code) {
		case 1:
			return ADD;
		case -1:
			return DEL;
		default:
			throw new IllegalArgumentException("unknown member diff type");

		}

	}

}
