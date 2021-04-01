package ch.ethz.infk.pps.zeph.clientdemo.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.ethz.infk.pps.zeph.shared.pojo.HostInfo;

public class KafkaDefinition {

	private List<HostInfo> kafkaBootstrapServers;
	private List<HostInfo> interactiveQueriesServers;

	private Map<String, Object> dataProducerConfig;

	private Map<String, Object> privacyControllerProducerConfig;
	private Map<String, Object> privacyControllerConsumerConfig;

	public List<HostInfo> getKafkaBootstrapServers() {
		return kafkaBootstrapServers;
	}

	public void setKafkaBootstrapServers(List<HostInfo> kafkaBootstrapServers) {
		this.kafkaBootstrapServers = kafkaBootstrapServers;
	}

	@JsonIgnore
	public String getKafkaBootstrapServersString() {
		List<String> servers = this.kafkaBootstrapServers.stream()
				.map(hostInfo -> hostInfo.getPath())
				.collect(Collectors.toList());
		return String.join(",", servers);
	}

	public List<HostInfo> getInteractiveQueriesServers() {
		return interactiveQueriesServers;
	}

	public void setInteractiveQueriesServers(List<HostInfo> interactiveQueriesServers) {
		this.interactiveQueriesServers = interactiveQueriesServers;
	}

	public Map<String, Object> getDataProducerConfig() {
		return dataProducerConfig;
	}

	public void setDataProducerConfig(Map<String, Object> dataProducerConfig) {
		this.dataProducerConfig = dataProducerConfig;
	}

	public Map<String, Object> getPrivacyControllerProducerConfig() {
		return privacyControllerProducerConfig;
	}

	public void setPrivacyControllerProducerConfig(Map<String, Object> privacyControllerProducerConfig) {
		this.privacyControllerProducerConfig = privacyControllerProducerConfig;
	}

	public Map<String, Object> getPrivacyControllerConsumerConfig() {
		return privacyControllerConsumerConfig;
	}

	public void setPrivacyControllerConsumerConfig(Map<String, Object> privacyControllerConsumerConfig) {
		this.privacyControllerConsumerConfig = privacyControllerConsumerConfig;
	}
}
