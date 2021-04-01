package ch.ethz.infk.pps.zeph.shared;

public class Names {

	// topics
	public static final String UNIVERSE_PARTITION_UPDATES_TOPIC = "universe-partition-updates";
	public static final String UNIVERSE_RESULTS_TOPIC = "universe-results";

	// stores
	public static final String UNIVERSE_STATUS_STORE = "status-store";
	public static final String UNIVERSE_TASK_STATUS_STORE = "task-status-store";
	public static final String UNIVERSE_RESULT_STORE = "result-store";
	public static final String UNIVERSE_MEMBERSHIP_STORE = "membership-store";

	public final Long UNIVERSE_ID;
	public final String UNIVERSE_PREFIX;

	// universe topics
	public final String CIPHERTEXTS_TOPIC;
	public final String TOKEN_TOPIC;
	public final String INFO_TOPIC;

	// universe stores
	public final String CIPHERTEXT_SUM_STORE;
	public final String COMMITTING_SUM_STORE;
	public final String TRANSFORMING_SUM_STORE;

	public final String COMMIT_BUFFER_STORE;

	public final String EXPECTED_TRANSFORMATION_TOKEN_STORE;
	public final String MEMBER_DELTA_STORE;
	public final String MEMBER_STORE;

	public Names(Long universe) {
		this.UNIVERSE_ID = universe;
		this.UNIVERSE_PREFIX = "u" + universe;

		// topics
		this.CIPHERTEXTS_TOPIC = this.UNIVERSE_PREFIX + "-ciphertexts";
		this.TOKEN_TOPIC = getTokensTopic(universe);
		this.INFO_TOPIC = getInfosTopic(universe);

		// stores
		this.CIPHERTEXT_SUM_STORE = this.UNIVERSE_PREFIX + "-ciphertext-sum-store";
		this.COMMITTING_SUM_STORE = this.UNIVERSE_PREFIX + "-committing-sum-store";
		this.TRANSFORMING_SUM_STORE = this.UNIVERSE_PREFIX + "-transforming-sum-store";

		this.COMMIT_BUFFER_STORE = this.UNIVERSE_PREFIX + "-commit-buffer-store";

		this.EXPECTED_TRANSFORMATION_TOKEN_STORE = this.UNIVERSE_PREFIX + "-expected-transformation-token-store";
		this.MEMBER_DELTA_STORE = this.UNIVERSE_PREFIX + "-member-delta";
		this.MEMBER_STORE = this.UNIVERSE_PREFIX + "-member";
	}

	public static String getTokensTopic(long universeId) {
		return "u" + universeId + "-tokens";

	}

	public static String getInfosTopic(long universeId) {
		return "u" + universeId + "-infos";

	}

}
