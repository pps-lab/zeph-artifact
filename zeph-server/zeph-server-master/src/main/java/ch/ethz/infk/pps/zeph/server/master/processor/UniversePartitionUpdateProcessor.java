package ch.ethz.infk.pps.zeph.server.master.processor;

import java.time.Duration;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;

import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.shared.avro.UniversePartitionUpdate;
import ch.ethz.infk.pps.shared.avro.Window;

public class UniversePartitionUpdateProcessor implements Processor<Long, UniversePartitionUpdate> {

	private final Duration timeForCommit;

	private StatusChangeHandler statusHandler;

	public UniversePartitionUpdateProcessor(Duration timeForCommit) {
		this.timeForCommit = timeForCommit;
	}

	@Override
	public void init(ProcessorContext context) {
		this.statusHandler = new StatusChangeHandler(context, timeForCommit);
	}

	@Override
	public void process(Long universeId, UniversePartitionUpdate update) {
		WindowStatus status = WindowStatus.of(update.getStatus());
		Window window = update.getWindow();
		String taskId = update.getTaskId();

		switch (status) {
		case OPEN:
			statusHandler.handleStatusOpen(universeId, taskId, window);
			break;
		case STAGED:
			statusHandler.handleStatusStaged(universeId, taskId, window);
			break;
		case COMMITTED:
			statusHandler.handleStatusCommitted(universeId, taskId, window);
			break;
		case MERGED:
			statusHandler.handleStatusMerged(universeId, taskId, window, update.getMemberDiff());
			break;
		case CLOSED:
			statusHandler.handleStatusClosed(universeId, taskId, window, update.getResult());
			break;
		default:
			throw new IllegalStateException("not possible");
		}

	}

	@Override
	public void close() {
		// do nothing
	}

	public static class UniversePartitionUpdateProcessorSupplier
			implements ProcessorSupplier<Long, UniversePartitionUpdate> {

		private Duration timeForCommit;

		public UniversePartitionUpdateProcessorSupplier(Duration timeForCommit) {
			this.timeForCommit = timeForCommit;
		}

		@Override
		public Processor<Long, UniversePartitionUpdate> get() {
			return new UniversePartitionUpdateProcessor(timeForCommit);
		}

	}

}
