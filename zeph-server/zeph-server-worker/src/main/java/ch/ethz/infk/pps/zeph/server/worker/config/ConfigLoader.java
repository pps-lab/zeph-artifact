package ch.ethz.infk.pps.zeph.server.worker.config;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class ConfigLoader {

	public static WorkerConfig loadConfig(String[] args) {
		CommandLineParser parser = new DefaultParser();
		Options options = buildOptions();

		try {
			CommandLine cmd = parser.parse(options, args);

			WorkerConfig config = new WorkerConfig();

			config.setKafkaBootstrapServers(cmd.getOptionValue("bootstrap-server"));

			config.setUniverseId(Long.parseLong(cmd.getOptionValue("universe-id")));
			config.setUniverseSize(Long.parseLong(cmd.getOptionValue("universe-size", "-1")));
			config.setWindowSizeMillis(Long.parseLong(cmd.getOptionValue("window-size")));
			config.setGraceSizeMillis(Long.parseLong(cmd.getOptionValue("grace-size")));
			config.setRetentionPeriodHours(Long.parseLong(cmd.getOptionValue("retention-time")));
			config.setStreamThreads(Integer.parseInt(cmd.getOptionValue("stream-threads", "1")));
			config.setNumPartitions(Integer.parseInt(cmd.getOptionValue("partitions", "3")));
			config.setReplication(Integer.parseInt(cmd.getOptionValue("replications", "1")));
			config.setStateDir(cmd.getOptionValue("state-dir"));
			config.setDeleteTopics(cmd.hasOption("delete-topics"));

			return config;

		} catch (Exception e) {
			System.out.println(e);
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("zeph-server-worker", options);
			System.exit(0);
		}

		return null;
	}

	private static Options buildOptions() {
		Options options = new Options();

		options.addOption(Option.builder().longOpt("universe-id").argName("NUMBER").desc("universe id").hasArg()
				.required().build());

		options.addOption(
				Option.builder().longOpt("universe-size").argName("NUMBER").desc("universe size").hasArg().build());

		options.addOption(Option.builder().longOpt("window-size").argName("MILLIS").desc("window size in milliseconds")
				.hasArg().required().build());

		options.addOption(Option.builder().longOpt("grace-size").argName("MILLIS")
				.desc("grace size in milliseconds (i.e. time late records are accepted)").hasArg().required().build());

		options.addOption(Option.builder().longOpt("retention-time").argName("HOURS")
				.desc("retention period for streams state").hasArg().build());

		options.addOption(Option.builder().longOpt("state-dir").argName("PATH").desc("kafka streams state directory")
				.hasArg().required().build());

		options.addOption(Option.builder().longOpt("bootstrap-server").argName("HOST:PORT")
				.desc("kafka bootstrap server host:port").hasArg().required().build());

		options.addOption(Option.builder().longOpt("partitions").argName("NUMBER")
				.desc("number of partitions of token and value topics").hasArg().required().build());

		options.addOption(Option.builder().longOpt("replications").argName("NUMBER")
				.desc("number of replications of token and value topic").hasArg().build());

		options.addOption(Option.builder().longOpt("stream-threads").argName("NUMBER")
				.desc("number of threads in kafka streams").hasArg().build());

		options.addOption(Option.builder().longOpt("delete-topics").hasArg(false)
				.desc("delete the created topics when shutting down").build());

		return options;
	}

}
