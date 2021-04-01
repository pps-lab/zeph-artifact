package ch.ethz.infk.pps.zeph.benchmark.macro;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import ch.ethz.infk.pps.zeph.benchmark.macro.controller.PrivacyControllerBenchmarkRunner;
import ch.ethz.infk.pps.zeph.benchmark.macro.e2e.E2EBenchmarkRunner;

public class Main {

	public static void main(String[] args) {

		CommandLine commandLine;
		CommandLineParser parser = new DefaultParser();
		Options options = buildOptions();

		try {
			commandLine = parser.parse(options, args, true);

			if (commandLine.hasOption("e2e")) {
				E2EBenchmarkRunner.main(args);
			} else if (commandLine.hasOption("pc")) {
				PrivacyControllerBenchmarkRunner.main(args);
			} else {
				throw new IllegalArgumentException();
			}

		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("macrobenchmark", options);
			System.exit(0);
		}

	}

	private static Options buildOptions() {
		Options options = new Options();
		OptionGroup group = new OptionGroup();
		group.addOption(Option.builder("e2e").longOpt("end-to-end").hasArg(false)
				.desc("run end to end macrobenchmark")
				.required().build());
		group.addOption(Option.builder("pc").longOpt("privacy-controller").hasArg(false)
				.desc("run privacy controller macrobenchmark")
				.required().build());

		group.setRequired(true);
		options.addOptionGroup(group);
		return options;
	}

}
