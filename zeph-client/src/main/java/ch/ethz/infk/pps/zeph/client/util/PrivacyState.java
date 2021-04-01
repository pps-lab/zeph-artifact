package ch.ethz.infk.pps.zeph.client.util;

import ch.ethz.infk.pps.zeph.crypto.Heac;

public class PrivacyState {

	private final PrivacyConfig privacyConfig;

	public PrivacyState(PrivacyConfig privacyConfig) {
		this.privacyConfig = privacyConfig;
	}

	public Heac getHeac() {
		return privacyConfig.heac;
	}

	public PrivacyConfig getPrivacyConfig() {
		return privacyConfig;
	}

	public static class PrivacyConfig {

		protected final Heac heac;

		public PrivacyConfig(Heac heac) {
			this.heac = heac;
		}

	}

}
