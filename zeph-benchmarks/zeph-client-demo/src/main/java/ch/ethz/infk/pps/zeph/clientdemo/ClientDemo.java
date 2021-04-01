package ch.ethz.infk.pps.zeph.clientdemo;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.ethz.infk.pps.zeph.clientdemo.config.TestProfileDefinition;

public class ClientDemo {

	private final static Logger LOG = LogManager.getLogger();
	public final static Marker MARKER = MarkerManager.getMarker("benchmark");

	public static final String CONFIG = "config";
	public static final String OUTPUT_FORMAT = "output-format";

	public static void main(String[] args) {

		CommandLineParser parser = new DefaultParser();

		Options options = buildOptions();

		boolean displayHelp = false;
		String errorMessage = null;

		try {
			CommandLine commandLine = parser.parse(options, args);
			String config = commandLine.getOptionValue(CONFIG);
			try {
				ObjectMapper mapper = new ObjectMapper();
				TestProfileDefinition testProfileDefinition = mapper.readValue(new File(config),
						TestProfileDefinition.class);

				testProfileDefinition.setRunId(System.currentTimeMillis());

				LOG.info(MARKER, System.lineSeparator()
						+ mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testProfileDefinition));

				TestProfileRunner testProfileRunner = new TestProfileRunner(testProfileDefinition);
				testProfileRunner.run();

			} catch (JsonMappingException | JsonParseException e) {
				errorMessage = "Unable to parse " + config + ": " + e.getOriginalMessage();
				displayHelp = true;
			} catch (IOException e) {
				errorMessage = "Unable to load " + config + ": " + e.getMessage();
				displayHelp = true;
			}
		} catch (ParseException e) {
			displayHelp = true;
		}

		if (displayHelp) {
			HelpFormatter formatter = new HelpFormatter();
			if (errorMessage != null) {
				System.out.println(errorMessage + System.lineSeparator());
			}
			formatter.printHelp("kafka-perf-test", options);
		}

	}

	private static Options buildOptions() {
		Options options = new Options();
		options.addOption(Option.builder("c")
				.longOpt(CONFIG)
				.desc("config file that defines the test profile(s) to run")
				.hasArg()
				.argName("FILE")
				.required(true)
				.build());

		return options;
	}

}
