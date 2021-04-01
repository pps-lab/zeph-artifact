package ch.ethz.infk.pps.zeph.benchmark.macro.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.zeph.client.IProducer;

public class FileProducerDriver implements IProducerDriver {

	private IProducer producer;
	private ScheduledExecutorService executorService;
	private boolean isShutdownRequested = false;

	long timestamp = -1;

	private LinkedList<ImmutablePair<Long, Input>> queue = new LinkedList<>();

	public FileProducerDriver(IProducer producer, ScheduledExecutorService executorService, Path dataFilePath,
			int testTimeSec) {
		this.producer = producer;
		this.executorService = executorService;

		Long prev = -1l;
		Long end = Long.MAX_VALUE;

		try (Stream<String> lines = Files.lines(dataFilePath)) {

			Iterator<String> iter = lines.iterator();

			while (iter.hasNext() && prev < end) {
				String[] parts = iter.next().split(";");
				long timestamp = Long.parseLong(parts[0]);

				if (prev < 0) {
					prev = timestamp;
					end = timestamp + 1000 * testTimeSec;
				} else if (timestamp <= prev) {
					throw new IllegalStateException("does not support out of order files");
				}

				long delay = timestamp - prev;

				Input input = ApplicationAdapter.fromCsv(parts);
				queue.add(ImmutablePair.of(delay, input));
				prev = timestamp;
			}

			if (prev < end) {
				throw new IllegalStateException("file does not have enough records for: sec=" + testTimeSec);
			}

		} catch (IOException e) {
			throw new IllegalStateException("failed to read file");
		}

	}

	@Override
	public Void call() throws Exception {
		try {
			ImmutablePair<Long, Input> pair = queue.poll();
			Input value = pair.getRight();

			timestamp = Math.max(timestamp + 1, System.currentTimeMillis());
			producer.submit(value, timestamp);
		} finally {
			if (!isShutdownRequested) {
				long delay = queue.peek().getLeft();
				executorService.schedule(this, delay, TimeUnit.MILLISECONDS);
			}
		}
		return null;
	}

	@Override
	public void init() {
		this.producer.init();
	}

	@Override
	public void requestShutdown() {
		isShutdownRequested = true;
	}

}
