package ch.ethz.infk.pps.shared.avro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Optional;

import ch.ethz.infk.pps.zeph.shared.DigestOp;

public class ApplicationAdapter {

	public static Input add(Input a, Input b) {
		Input result = new Input(a.getVibrationVelocity() + b.getVibrationVelocity(),
				a.getVibrationDisplacement() + b.getVibrationDisplacement(),
				a.getVibrationAcceleration() + b.getVibrationAcceleration(),
				a.getSoundPressure() + b.getSoundPressure(), a.getHumidity() + b.getHumidity(),
				a.getSpeed() + b.getSpeed(), a.getFuelLevel() + b.getFuelLevel(),
				a.getTirePressure() + b.getTirePressure(), a.getAmbientAirTemperature() + b.getAmbientAirTemperature(),
				a.getOilTemperature() + b.getOilTemperature(), a.getEngineTemperature() + b.getEngineTemperature(),
				Optional.fromNullable(a.getCount()).or(1l) + Optional.fromNullable(b.getCount()).or(1l));
		return result;
	}

	public static Input random() {
		long vibrationVelocity = ThreadLocalRandom.current().nextInt(0, 16);
		long vibrationDisplacement = ThreadLocalRandom.current().nextInt(0, 120);
		long vibrationAcceleration = ThreadLocalRandom.current().nextInt(0, 40);
		long soundPressure = ThreadLocalRandom.current().nextInt(0, 120);
		long humidity = ThreadLocalRandom.current().nextInt(0, 100);
		long speed = ThreadLocalRandom.current().nextInt(0, 200);
		long fuelLevel = ThreadLocalRandom.current().nextInt(0, 100);
		long tirePressure = ThreadLocalRandom.current().nextInt(12, 40);
		long ambientAirTemperature = ThreadLocalRandom.current().nextInt(-7, 40);
		long oilTemperature = ThreadLocalRandom.current().nextInt(0, 160);
		long engineTemperature = ThreadLocalRandom.current().nextInt(0, 100);

		Long count = null;
		return new Input(vibrationVelocity, vibrationDisplacement, vibrationAcceleration, soundPressure, humidity,
				speed, fuelLevel, tirePressure, ambientAirTemperature, oilTemperature, engineTemperature, count);
	}

	public static Input empty() {
		return new Input(0l, 0l, 0l, 0l, 0l, 0l, 0l, 0l, 0l, 0l, 0l, 0l);
	}

	public static Long getCount(Input input) {
		return input.getCount();
	}

	public static Long getSum(Input input) {
		return input.getOilTemperature();
	}

	// the main field of the event (which is used for get mean get std and so on)
	public static final String MAIN_BASE_FIELD_NAME = "oilTemperatureBase";
	public static final int MAIN_BASE_FIELD_BITS = 64;

	public static Digest toDigest(Input input) {
		Digest digest = DigestOp.empty();

		int bits16 = 16;

		long vibrationVelocity = input.getVibrationVelocity();
		long vibrationDisplacement = input.getVibrationDisplacement();
		long vibrationAcceleration = input.getVibrationAcceleration();

		long soundPressure = input.getSoundPressure();
		long humidity = input.getHumidity();
		long speed = input.getSpeed();
		long fuelLevel = input.getFuelLevel();
		long tirePressure = input.getTirePressure();

		long ambientAirTemperature = input.getAmbientAirTemperature();
		long oilTemperature = input.getOilTemperature();
		long engineTemperature = input.getEngineTemperature();

		// set the base fields with sum and sum of squares
		DigestOp.setBase(vibrationVelocity, digest.getVibrationVelocityBase());
		DigestOp.setBase(vibrationDisplacement, digest.getVibrationDisplacementBase());
		DigestOp.setBase(vibrationAcceleration, digest.getVibrationAccelerationBase());
		DigestOp.setBase(soundPressure, digest.getSoundPressureBase());
		DigestOp.setBase(humidity, digest.getHumidityBase());
		DigestOp.setBase(speed, digest.getSpeedBase());
		DigestOp.setBase(fuelLevel, digest.getFuelLevelBase());
		DigestOp.setBase(tirePressure, digest.getTirePressureBase());
		DigestOp.setBase(ambientAirTemperature, digest.getAmbientAirTemperatureBase());
		DigestOp.setBase(oilTemperature, digest.getOilTemperatureBase());
		DigestOp.setBase(engineTemperature, digest.getEngineTemperatureBase());

		DigestOp.setHist(vibrationVelocity, 1l, digest.getVibrationVelocityHist(), bits16, 0l, null);
		DigestOp.setHist(vibrationDisplacement, 1l, digest.getVibrationDisplacementHist(), bits16, 0l, null);
		DigestOp.setHist(vibrationAcceleration, 1l, digest.getVibrationAccelerationHist(), bits16, 0l, null);

		DigestOp.setHist(soundPressure, 1l, digest.getSoundPressureHist(), bits16, 0l, null);
		DigestOp.setHist(humidity, 1l, digest.getHumidityHist(), bits16, 0l, null);
		DigestOp.setHist(speed / 5, 1l, digest.getSpeedHist(), bits16, 0l, null);
		DigestOp.setHist(fuelLevel / 5, 1l, digest.getFuelLevelHist(), bits16, 2l, null);
		DigestOp.setHist(tirePressure, 1l, digest.getTirePressureHist(), bits16, 12l, null);

		DigestOp.setHist(ambientAirTemperature, 1l, digest.getAmbientAirTemperatureHist(), bits16, -7l, null);
		DigestOp.setHist(oilTemperature / 5, 1l, digest.getOilTemperatureHist(), bits16, 0l, null);
		DigestOp.setHist(engineTemperature / 5, 1l, digest.getEngineTemperatureHist(), bits16, 0l, null);

		digest.getCount().set(0, 1l);

		return digest;
	}

	public static Digest emptyDigest() {

		List<Long> vibrationVelocityHist = new ArrayList<Long>(Collections.nCopies(4, 0l)); // 0-15 with 16bit => 4 longs
		List<Long> vibrationDisplacementHist = new ArrayList<Long>(Collections.nCopies(30, 0l)); // 0-119 with 16bit => 30 longs
		List<Long> vibrationAccelerationHist = new ArrayList<Long>(Collections.nCopies(10, 0l)); // 0-39 with 16 bit => 10 longs
		List<Long> soundPressureHist = new ArrayList<Long>(Collections.nCopies(30, 0l)); // 20-139 with 16 bit => 30 longs
		List<Long> humidityHist = new ArrayList<Long>(Collections.nCopies(25, 0l)); // 0-100 with 16 bit => 25 longs
		List<Long> speedHist = new ArrayList<Long>(Collections.nCopies(10, 0l)); // 0-200 with 16 bit in 5km/h buckets => 10 longs
		List<Long> fuelLevelHist = new ArrayList<Long>(Collections.nCopies(5, 0l)); // 0-100 with 16 bit in 5% buckets => 5 longs
		List<Long> tirePressureHist = new ArrayList<Long>(Collections.nCopies(7, 0l)); // 12-40 with 16 bit => 7 longs
		List<Long> ambientAirTemperatureHist = new ArrayList<Long>(Collections.nCopies(12, 0l)); // -7-40 with 16 bit => 12 longs 
		List<Long> oilTemperatureHist = new ArrayList<Long>(Collections.nCopies(8, 0l)); // 0-160 with 16 bit in 5 buckets => 8 longs
		List<Long> engineTemperatureHist = new ArrayList<Long>(Collections.nCopies(5, 0l)); // 0-100 with 16 bit in 5 bucket => 5 longs

		// each of the base fields contains the value itself (64 bit) and it's square (64 bit) => 2 longs each 
		List<Long> vibrationVelocityBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> vibrationDisplacementBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> vibrationAccelerationBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> soundPressureBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> humidityBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> speedBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> fuelLevelBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> tirePressureBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> ambientAirTemperatureBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> oilTemperatureBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> engineTemperatureBase = new ArrayList<Long>(Collections.nCopies(2, 0l));

		List<Long> count = new ArrayList<Long>(Collections.nCopies(1, 0l));  // 64 bit => 1 long
		HeacHeader header = null;

		return new Digest(vibrationVelocityHist, vibrationDisplacementHist, vibrationAccelerationHist,
				soundPressureHist, humidityHist, speedHist, fuelLevelHist, tirePressureHist, ambientAirTemperatureHist,
				oilTemperatureHist, engineTemperatureHist,

				vibrationVelocityBase, vibrationDisplacementBase, vibrationAccelerationBase, soundPressureBase,
				humidityBase, speedBase, fuelLevelBase, tirePressureBase, ambientAirTemperatureBase, oilTemperatureBase,
				engineTemperatureBase,

				count, header);
	}

	public static Input fromCsv(String[] parts) {
		throw new IllegalStateException("reading from csv is not implemented for the car application");
	}

}