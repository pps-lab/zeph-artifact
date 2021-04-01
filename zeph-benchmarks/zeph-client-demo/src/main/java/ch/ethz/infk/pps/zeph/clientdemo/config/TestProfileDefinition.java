package ch.ethz.infk.pps.zeph.clientdemo.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TestProfileDefinition {

	private String profileId;

	@JsonIgnore
	private long runId;

	private ClientDemoDefinition clientDemo;

	private List<UniverseDefinition> universes;

	private List<PrivacyControllerDefinition> privacyControllers;

	private List<ProducerDefinition> producers;

	private KafkaDefinition kafka;

	private DashboardDefinition dashboard;

	public String getProfileId() {
		return profileId;
	}

	public void setProfileId(String profileId) {
		this.profileId = profileId;
	}

	@JsonIgnore
	public long getRunId() {
		return runId;
	}

	public void setRunId(long runId) {
		this.runId = runId;
	}

	public ClientDemoDefinition getClientDemo() {
		return clientDemo;
	}

	public void setClientDemo(ClientDemoDefinition clientDemo) {
		this.clientDemo = clientDemo;
	}

	public List<UniverseDefinition> getUniverses() {
		return universes;
	}

	public void setUniverses(List<UniverseDefinition> universes) {
		this.universes = universes;
	}

	public List<PrivacyControllerDefinition> getPrivacyControllers() {
		return privacyControllers;
	}

	public void setPrivacyControllers(List<PrivacyControllerDefinition> privacyControllers) {
		this.privacyControllers = privacyControllers;
	}

	public List<ProducerDefinition> getProducers() {
		return producers;
	}

	public void setProducers(List<ProducerDefinition> producers) {
		this.producers = producers;
	}

	public KafkaDefinition getKafka() {
		return kafka;
	}

	public void setKafka(KafkaDefinition kafka) {
		this.kafka = kafka;
	}

	public DashboardDefinition getDashboard() {
		return dashboard;
	}

	public void setDashboard(DashboardDefinition dashboard) {
		this.dashboard = dashboard;
	}

}
