package ch.ethz.infk.pps.zeph.clientdemo.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.ethz.infk.pps.zeph.shared.pojo.ProducerIdentity;

public class ProducerDefinition {

	private Long producerId;
	private List<Long> producerIdRange;

	private int testTimeSec;

	private Double silentProb;
	private int silentTimeSec;

	private List<Integer> randomDataRange;
	private Double randomDataExponentialDelayLambda;

	private String dataImport;

	@JsonIgnore
	private ProducerIdentity producerInfo;

	public ProducerDefinition copy() {
		ProducerDefinition pDef = new ProducerDefinition();
		pDef.setProducerId(producerId);
		pDef.setProducerIdRange(producerIdRange);
		pDef.setTestTimeSec(testTimeSec);
		pDef.setSilentProb(silentProb);
		pDef.setSilentTimeSec(silentTimeSec);
		pDef.setRandomDataRange(randomDataRange);
		pDef.setRandomDataExponentialDelayLambda(randomDataExponentialDelayLambda);
		pDef.setDataImport(dataImport);
		pDef.setProducerInfo(producerInfo);
		return pDef;
	}

	public Long getProducerId() {
		return producerId;
	}

	public void setProducerId(Long producerId) {
		this.producerId = producerId;
	}

	public List<Long> getProducerIdRange() {
		return producerIdRange;
	}

	public void setProducerIdRange(List<Long> producerIdRange) {
		this.producerIdRange = producerIdRange;
	}

	public int getTestTimeSec() {
		return testTimeSec;
	}

	public void setTestTimeSec(int testTimeSec) {
		this.testTimeSec = testTimeSec;
	}

	public Double getSilentProb() {
		return silentProb;
	}

	public void setSilentProb(double silentProb) {
		this.silentProb = silentProb;
	}

	public int getSilentTimeSec() {
		return silentTimeSec;
	}

	public void setSilentTimeSec(int silentTimeSec) {
		this.silentTimeSec = silentTimeSec;
	}

	public List<Integer> getRandomDataRange() {
		return randomDataRange;
	}

	public void setRandomDataRange(List<Integer> randomDataRange) {
		this.randomDataRange = randomDataRange;
	}

	public Double getRandomDataExponentialDelayLambda() {
		return randomDataExponentialDelayLambda;
	}

	public void setRandomDataExponentialDelayLambda(double randomDataExponentialDelayLambda) {
		this.randomDataExponentialDelayLambda = randomDataExponentialDelayLambda;
	}

	public String getDataImport() {
		return dataImport;
	}

	public void setDataImport(String dataImport) {
		this.dataImport = dataImport;
	}

	@JsonIgnore
	public ProducerIdentity getProducerInfo() {
		return producerInfo;
	}

	public void setProducerInfo(ProducerIdentity producerInfo) {
		this.producerInfo = producerInfo;
	}

}
