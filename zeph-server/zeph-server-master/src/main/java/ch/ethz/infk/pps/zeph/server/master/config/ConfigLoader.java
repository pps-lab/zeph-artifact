package ch.ethz.infk.pps.zeph.server.master.config;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class ConfigLoader {

	public static MasterConfig getConfig(String[] args) {

		CommandLineParser parser = new DefaultParser();
		Options options = buildOptions();

		try {
			CommandLine cmd = parser.parse(options, args);

			MasterConfig config = new MasterConfig();
			config.setTimeToCommitMillis(Long.parseLong(cmd.getOptionValue("time-to-commit")));
			config.setKafkaBootstrapServers(cmd.getOptionValue("bootstrap-server"));
			config.setNumPartitions(Integer.parseInt(cmd.getOptionValue("universe-partitions", "3")));
			config.setReplication(Integer.parseInt(cmd.getOptionValue("universe-replications", "1")));
			config.setDeleteTopics(cmd.hasOption("delete-topics"));
			config.setStateDir(cmd.getOptionValue("state-dir"));
			config.setStreamThreads(Integer.parseInt(cmd.getOptionValue("stream-threads", "1")));

			return config;

		} catch (Exception e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("zeph-server-master", options);
			System.exit(0);
		}

		return null;
	}

	private static Options buildOptions() {
		Options options = new Options();

		options.addOption(Option.builder().longOpt("bootstrap-server").argName("HOST:PORT")
				.desc("kafka bootstrap server host:port").hasArg().required().build());

		options.addOption(Option.builder().longOpt("time-to-commit").argName("MILLIS")
				.desc("number of milliseconds for privacy controllers to commit").hasArg().required().build());

		options.addOption(Option.builder().longOpt("state-dir").argName("PATH").desc("kafka streams state directory")
				.hasArg().required().build());

		options.addOption(
				Option.builder().longOpt("delete-topics").hasArg(false).desc("delete the created topics").build());

		options.addOption(Option.builder().longOpt("universe-partitions").argName("NUMBER")
				.desc("number of topic partitions for universe topics (result and update topic)").hasArg().build());

		options.addOption(Option.builder().longOpt("stream-threads").argName("NUMBER")
				.desc("number of threads in kafka streams").hasArg().build());

		options.addOption(Option.builder().longOpt("universe-replications").argName("NUMBER")
				.desc("number of topic replications for universe topics (result and update topic)").hasArg().build());

		options.addOption(Option.builder().longOpt("interactive-queries-server").argName("HOST:PORT")
				.desc("interactive queries server host:port").hasArg().build());

		return options;
	}

}
