package ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfig.BenchmarkType;
import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.config.E2EConfig.E2EConfigBuilder;

public class E2EConfigParser {

	public static E2EConfig parseConfig(CommandLine commandLine) throws ParseException {

		String arg = null;
		try {
			BenchmarkType benchmarkType = null;

			if (commandLine.hasOption("zeph")) {
				benchmarkType = BenchmarkType.zeph;
			} else if (commandLine.hasOption("plaintext")) {
				benchmarkType = BenchmarkType.plaintext;
			} else {
				throw new IllegalArgumentException("e2e benchmark must contain zeph / plaintext option");
			}

			E2EConfigBuilder builder = new E2EConfigBuilder(benchmarkType);

			arg = "experiment-id";
			builder.withExperimentId(commandLine.getOptionValue(arg));

			arg = "test-time";
			builder.withTestTimeSec(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "application";
			builder.withApplication(commandLine.getOptionValue(arg, null));

			arg = "e2e-producer";
			builder.withE2EProducer(commandLine.hasOption(arg));

			arg = "e2e-controller";
			builder.withE2EController(commandLine.hasOption(arg));

			arg = "out-file";
			builder.withOutputFile(commandLine.getOptionValue(arg));

			arg = "delete-topics";
			builder.withDeleteTopics(commandLine.hasOption(arg));

			arg = "kafka-bootstrap-server";
			builder.withKafkaBootstrapServers(commandLine.getOptionValue(arg));

			arg = "universes";
			List<Long> universeIds = Arrays.asList(commandLine.getOptionValues(arg)).stream()
					.map(uIdstr -> Long.parseLong(uIdstr)).collect(Collectors.toList());
			builder.withUniverseIds(universeIds);

			arg = "universe-size";
			builder.withUniverseSize(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "window-size";
			builder.withWindowSizeMillis(Long.parseLong(commandLine.getOptionValue(arg)));

			arg = "producer-partition";
			builder.withProducerPartition(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "producer-partition-count";
			builder.withProducerPartitionCount(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "producer-partition-size";
			builder.withProducerPartitionSize(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "expo-delay-mean";
			builder.withExpoDelayMean(Double.parseDouble(commandLine.getOptionValue(arg)));

			arg = "data-dir";
			builder.withDataFolder(commandLine.getOptionValue(arg));

			if (benchmarkType == BenchmarkType.plaintext) {
				return builder.build();
			}

			arg = "controller-partition";
			builder.withControllerPartition(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "controller-partition-size";
			builder.withControllerPartitionSize(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "controller-partition-count";
			builder.withControllerPartitionCount(Integer.parseInt(commandLine.getOptionValue(arg)));

			arg = "universe-min-size";
			String uMinSize = commandLine.getOptionValue(arg);
			if (uMinSize == null) {
				uMinSize = commandLine.getOptionValue("universe-size");
			}
			builder.withUniverseMinSize(Integer.parseInt(uMinSize));

			arg = "alpha";
			builder.withAlpha(Double.parseDouble(commandLine.getOptionValue(arg, "0.5")));

			arg = "delta";
			builder.withDelta(Double.valueOf(commandLine.getOptionValue(arg, "1.0E-5")));

			arg = "producers-in-controller";
			builder.withProducersInController(Integer.parseInt(commandLine.getOptionValue(arg, "1")));

			arg = "universes-in-controller";
			builder.withUniversesInController(Integer.parseInt(commandLine.getOptionValue(arg, "1")));

			arg = "controller-poll-timeout";
			builder.withConsumerPollTimeout(
					Duration.ofMillis(Integer.parseInt(commandLine.getOptionValue(arg, "5000"))));

			return builder.build();

		} catch (NumberFormatException e) {
			throw new ParseException("argument must be a number: " + arg);
		}

	}

	public static Options buildOptions() {
		Options options = new Options();

		options.addOption(Option.builder("e2e").longOpt("end-to-end").hasArg(false)
				.desc("run end to end macrobenchmark").build());

		OptionGroup group = new OptionGroup();
		group.addOption(Option.builder().longOpt("plaintext").hasArg(false).desc("run benchmark in plaintext mode")
				.required().build());
		group.addOption(Option.builder().longOpt("zeph").hasArg(false).desc("run benchmark in zeph mode")
				.required().build());
		group.setRequired(true);
		options.addOptionGroup(group);

		options.addOption(Option.builder().longOpt("experiment-id").argName("ID")
				.desc("unique id of experiment (cannot be repeated) must be the same on all partiton instances")
				.hasArg().required().build());

		options.addOption(Option.builder().longOpt("test-time").argName("SECS")
				.desc("number of seconds to run the test").hasArg().required().build());

		options.addOption(Option.builder().longOpt("application").argName("FOLDER")
				.desc("folder name within data folder with producer stream csv files").hasArg().build());

		options.addOption(
				Option.builder().longOpt("e2e-producer").hasArg(false).desc("run the end-to-end producer").build());

		options.addOption(
				Option.builder().longOpt("e2e-controller").hasArg(false).desc("run the end-to-end controller").build());

		options.addOption(Option.builder().longOpt("out-file").argName("FILE").desc("name of output file").hasArg()
				.required().build());

		options.addOption(Option.builder().longOpt("delete-topics").hasArg(false)
				.desc("delete the partition sync topic at the end").build());

		options.addOption(Option.builder().longOpt("kafka-bootstrap-server").argName("HOST:PORT")
				.desc("kafka bootstrap server host:port").hasArg().required().build());

		options.addOption(Option.builder().longOpt("universes").argName("LIST").desc("(uId1, ..., uIdN").hasArgs()
				.valueSeparator(',').required().build());

		options.addOption(Option.builder().longOpt("universe-size").argName("NUMBER")
				.desc("number of participants in a universe").hasArg().required().build());

		options.addOption(Option.builder().longOpt("window-size").argName("MILLIS")
				.desc("size of the window in milliseconds").hasArg().required().build());

		options.addOption(Option.builder().longOpt("producer-partition").argName("NUMBER")
				.desc("producer partition, must be in range [0, producer-partition-count)").hasArg().required()
				.build());

		options.addOption(Option.builder().longOpt("producer-partition-count").argName("NUMBER")
				.desc("number of partitions per universe").hasArg().required().build());

		options.addOption(Option.builder().longOpt("producer-partition-size").argName("NUMBER")
				.desc("number of producers per universe (i.e. producer partition size)").hasArg().required().build());

		options.addOption(Option.builder().longOpt("expo-delay-mean").argName("NUMBER")
				.desc("exponential distribution lambda (sample time between two requests from same producer)").hasArg()
				.required().build());

		options.addOption(Option.builder().longOpt("data-dir").argName("PATH")
				.desc("base folder for producer info and shared keys").hasArg().required().build());

		// optional (needed for zeph)

		options.addOption(Option.builder().longOpt("controller-partition").argName("NUMBER")
				.desc("controller partition, must be in range [0, controller-partition-count)").hasArg().build());

		options.addOption(Option.builder().longOpt("controller-partition-size").argName("NUMBER")
				.desc("number of controllers per universe (i.e. controller partition size)").hasArg().build());

		options.addOption(Option.builder().longOpt("controller-partition-count").argName("NUMBER")
				.desc("number of controller partitions").hasArg().build());

		options.addOption(Option.builder().longOpt("universe-min-size").argName("NUMBER")
				.desc("minimum number of participants required in a universe").hasArg().build());

		options.addOption(Option.builder().longOpt("alpha").argName("PROB")
				.desc("probability that a member is non-malicious").hasArg().build());

		options.addOption(Option.builder().longOpt("delta").argName("PROB")
				.desc("failure error bound of erdos renyi secure aggregation").hasArg().build());

		options.addOption(Option.builder().longOpt("producers-in-controller").argName("NUMBER")
				.desc("number of producers assigned to privacy controller per universe").hasArg().build());

		options.addOption(Option.builder().longOpt("universes-in-controller").argName("NUMBER")
				.desc("number of universes assigned to a privacy controller").hasArg().build());

		options.addOption(Option.builder().longOpt("controller-poll-timeout").argName("MILLIS")
				.desc("poll timeout in millis").hasArg().build());

		return options;
	}

}
