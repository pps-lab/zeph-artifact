package ch.ethz.infk.pps.zeph.client;

import ch.ethz.infk.pps.shared.avro.Input;

public interface IProducer {

	public void init();

	public void submit(Input value, long timestamp);

	public void submitHeartbeat(long timestamp);

}
