package ch.ethz.infk.pps.zeph.benchmark.macro.e2e;

import java.util.concurrent.Callable;

public interface IProducerDriver extends Callable<Void> {

	public void init();

	public void requestShutdown();

}
