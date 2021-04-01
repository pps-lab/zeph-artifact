package ch.ethz.infk.pps.zeph.benchmark.macro.controller.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import ch.ethz.infk.pps.zeph.benchmark.macro.controller.config.PrivacyControllerBenchmarkConfig;
import ch.ethz.infk.pps.zeph.shared.Names;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.shared.avro.Token;
import ch.ethz.infk.pps.shared.avro.Window;

public class PrivacyControllerThroughputCalculator {

	private KafkaConsumer<Long, Token> kafkaConsumer;
	private List<String> topics;
	private PrivacyControllerBenchmarkConfig config;

	public PrivacyControllerThroughputCalculator(Set<Long> universeIds, KafkaConsumer<Long, Token> kafkaConsumer,
			PrivacyControllerBenchmarkConfig config) {
		this.kafkaConsumer = kafkaConsumer;
		this.topics = universeIds.stream().map(uId -> Names.getTokensTopic(uId)).collect(Collectors.toList());
		this.config = config;
	}

	public Table<Window, String, Long> calculateThroughput() {

		Duration timeout = Duration.ofSeconds(10);

		kafkaConsumer.subscribe(topics);

		long windowSize = 1000; // 1 sec
		Table<Window, String, Long> table = HashBasedTable.create();

		while (true) {
			ConsumerRecords<Long, Token> records = kafkaConsumer.poll(timeout);

			if (records.isEmpty()) {
				break;
			}

			records.forEach(record -> {

				Token token = record.value();
				if (token.getTransformation() != null) { // only count transformation tokens

					String runId = record.topic();

					record.timestampType();

					long windowStart = WindowUtil.getWindowStart(record.timestamp(), windowSize);
					Window window = new Window(windowStart, windowStart + windowSize);
					Long count = table.get(window, runId);
					if (count == null) {
						count = 0l;
					}

					table.put(window, runId, count + 1);
				}

			});

		}

		Map<String, Set<Window>> mapToDelete = new HashMap<>();

		table.columnKeySet().forEach(id -> {
			long start = table.column(id).keySet().stream().min(Comparator.comparing(Window::getStart)).get()
					.getStart();
			long end = start + (config.getTestTimeSec() + 1) * 1000;

			Set<Window> setToDelete = table.column(id).keySet().stream().filter(window -> window.getEnd() > end)
					.collect(Collectors.toSet());

			mapToDelete.put(id, setToDelete);

			System.out.println("delete " + setToDelete.size() + " entries from token count table (expired)");
		});

		mapToDelete.forEach((id, windows) -> windows.forEach(window -> table.remove(window, id)));

		return table;

	}

	public static void print(Table<Window, String, Long> table, PrivacyControllerBenchmarkConfig config,
			String outputFile) {
		List<String> runIds = table.columnKeySet().stream().sorted().collect(Collectors.toList());

		JSONObject result = new JSONObject();

		JSONObject configObject = new JSONObject(config);
		configObject.remove("bootstrapServer");
		result.put("config", configObject);

		System.out.println("run-id\twindow-end\tthroughput");
		JSONArray jsonArr = new JSONArray();
		runIds.forEach(runId -> {
			List<Window> windows = table.column(runId).keySet().stream().sorted().collect(Collectors.toList());
			windows.forEach(window -> {
				Long count = table.get(window, runId);
				System.out.println(runId + "\t" + window + "\t" + count);
				JSONObject jsonObj = new JSONObject();
				jsonObj.put("runid", runId);
				jsonObj.put("window", window.getEnd());
				jsonObj.put("count", count);
				jsonArr.put(jsonObj);
			});
		});
		result.put("data", jsonArr);

		try {
			Files.write(Paths.get(outputFile), result.toString(2).getBytes());
		} catch (JSONException | IOException e) {
			System.out.println(result.toString(2));
			throw new IllegalStateException("failed to write json output file", e);
		}
	}
}
