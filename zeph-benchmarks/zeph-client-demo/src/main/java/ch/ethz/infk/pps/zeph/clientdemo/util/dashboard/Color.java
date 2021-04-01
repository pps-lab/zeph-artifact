package ch.ethz.infk.pps.zeph.clientdemo.util.dashboard;

public enum Color {

	SHADE1("#2a4d69"), SHADE2("#4b86b4"), SHADE3("#63ace5"), SHADE4("#adcbe3"), SHADE5("#e7eff6"), INACTIVE("#749dad");

	private String hex;

	Color(String hex) {
		this.hex = hex;
	}

	public String hex() {
		return hex;
	}

}
