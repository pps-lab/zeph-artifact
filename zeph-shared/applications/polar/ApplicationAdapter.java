package ch.ethz.infk.pps.shared.avro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Optional;

import ch.ethz.infk.pps.zeph.shared.DigestOp;

public class ApplicationAdapter {

    public static Input add(Input a, Input b){

        return new Input(a.getHeartrate() + b.getHeartrate(), 
						    a.getAltitude() + b.getAltitude(), 
						    a.getSpeed() + b.getSpeed(), 
						    a.getAscent() + b.getAscent(), 
						    a.getDescent() + b.getDescent(), 
                            a.getTemperature() + b.getTemperature(), 
                            Optional.fromNullable(a.getCount()).or(1l) + Optional.fromNullable(b.getCount()).or(1l));

    }

    public static Input empty(){
        return new Input(0l, 0l, 0l, 0l, 0l, 0l, 0l);
    }

    public static Input random(){
        long heartrate = ThreadLocalRandom.current().nextLong(0, 220);
		long altitude = ThreadLocalRandom.current().nextLong(-100, 3000);
		long speed = ThreadLocalRandom.current().nextLong(0, 44);
        long ascentdescent = ThreadLocalRandom.current().nextLong(-49, 50);
        long ascent = 0;
		long descent = 0;
		if (ascentdescent > 0) {
			ascent = ascentdescent;
		} else {
			descent = -1 * ascentdescent;
		}

		long temperature = ThreadLocalRandom.current().nextLong(-10, 41);

		Input value = new Input(heartrate, altitude, speed, ascent, descent, temperature, null);
		return value;
    }


	public static Long getCount(Input input){
		return input.getCount();	
	}


	public static Long getSum(Input input){
		return input.getHeartrate();
	}


	// the main field of the event (which is used for get mean get std and so on)
	public static final String MAIN_BASE_FIELD_NAME = "heartrateBase";
	public static final int MAIN_BASE_FIELD_BITS = 64;


	public static Digest emptyDigest(){

		List<Long> heartrateHist = new ArrayList<Long>(Collections.nCopies(55, 0l)); // 16 bit => 55 longs
		List<Long> heartrateXaltitude = new ArrayList<Long>(Collections.nCopies(310, 0l));  // 32 bit => 310 longs
		List<Long> heartrateXspeed = new ArrayList<Long>(Collections.nCopies(22, 0l));  // 32 bit => 22 longs
		List<Long> heartrateXascent = new ArrayList<Long>(Collections.nCopies(26, 0l)); // 32 bit => 26 longs
		List<Long> heartrateXdescent = new ArrayList<Long>(Collections.nCopies(26, 0l)); // 32 bit => 26 longs
		List<Long> heartrateXtemperature = new ArrayList<Long>(Collections.nCopies(26, 0l)); // 32 bit => 26 longs
		List<Long> altitudeHist = new ArrayList<Long>(Collections.nCopies(155, 0l)); // 16 bit => 155 longs
		List<Long> speedHist = new ArrayList<Long>(Collections.nCopies(11, 0l)); // 16 bit => 11 longs
		List<Long> ascentHist = new ArrayList<Long>(Collections.nCopies(13, 0l)); // 16 bit => 13 longs
		List<Long> descentHist = new ArrayList<Long>(Collections.nCopies(13, 0l)); // 16 bit => 13 longs
		List<Long> temperatureHist = new ArrayList<Long>(Collections.nCopies(13, 0l)); // 16 bit => 13 longs
		List<Long> heartrateBase = new ArrayList<Long>(Collections.nCopies(2, 0l)); // 64 bit => 2 longs (sum and sum^2)
		List<Long> altitudeBase = new ArrayList<Long>(Collections.nCopies(2, 0l)); // 64 bit => 2 longs (sum and sum^2)
		List<Long> speedBase = new ArrayList<Long>(Collections.nCopies(2, 0l)); // 64 bit => 2 longs (sum and sum^2)
		List<Long> ascentBase = new ArrayList<Long>(Collections.nCopies(2, 0l)); // 64 bit => 2 longs (sum and sum^2)
		List<Long> descentBase = new ArrayList<Long>(Collections.nCopies(2, 0l)); // 64 bit => 2 longs (sum and sum^2)
		List<Long> temperatureBase = new ArrayList<Long>(Collections.nCopies(2, 0l)); // 64 bit => 2 longs (sum and sum^2)
		List<Long> count = new ArrayList<Long>(Collections.nCopies(1, 0l)); // 64 bit => 1 long
		HeacHeader header = null;

		return new Digest(heartrateHist, heartrateXaltitude, heartrateXspeed, heartrateXascent, heartrateXdescent,
				heartrateXtemperature, altitudeHist, speedHist, ascentHist, descentHist, temperatureHist, heartrateBase,
				altitudeBase, speedBase, ascentBase, descentBase, temperatureBase, count, header);

	}

	// Polar Specific Encoding
	public static Digest toDigest(Input input){

		Digest digest = DigestOp.empty();

		long heartrate = input.getHeartrate();
		long altitude = input.getAltitude();

		long ascent = input.getAscent();
		long descent = input.getDescent();

		long speed = input.getSpeed();
		long temperature = input.getTemperature();

		// set the count
		digest.getCount().set(0, 1l);

		// set the base fields with sum and sum of squares
		DigestOp.setBase(heartrate, digest.getHeartrateBase());
		DigestOp.setBase(altitude, digest.getAltitudeBase());
		DigestOp.setBase(ascent, digest.getAscentBase());
		DigestOp.setBase(descent, digest.getDescentBase());
		DigestOp.setBase(speed, digest.getSpeedBase());
		DigestOp.setBase(temperature, digest.getTemperatureBase());

		// set the hists
		long count = 1l;
		int bits16 = 16;
		int bits32 = 32;

		// set the 0/1 count hists
		DigestOp.setHist(heartrate, count, digest.getHeartrateHist(), bits16, 0, 220l);
		DigestOp.setHist(altitude / 10, count, digest.getAltitudeHist(), bits16, -10, 300l);
		DigestOp.setHist(ascent, count, digest.getAscentHist(), bits16, 0, 50l);
		DigestOp.setHist(descent, count, digest.getDescentHist(), bits16, 0, 50l);
		DigestOp.setHist(speed, count, digest.getSpeedHist(), bits16, 0, 44l);
		DigestOp.setHist(temperature, count, digest.getTemperatureHist(), bits16, -10, 40l);

		// set the heartrate group by hists
		long heartrateBinValue = heartrate;
		DigestOp.setHist(altitude / 10, heartrateBinValue, digest.getHeartrateXaltitude(), bits32, -10, 300l);
		DigestOp.setHist(ascent, heartrateBinValue, digest.getHeartrateXascent(), bits32, 0, 50l);
		DigestOp.setHist(descent, heartrateBinValue, digest.getHeartrateXdescent(), bits32, 0, 50l);
		DigestOp.setHist(speed, heartrateBinValue, digest.getHeartrateXspeed(), bits32, 0, 44l);
		DigestOp.setHist(temperature, heartrateBinValue, digest.getHeartrateXtemperature(), bits32, -10, 40l);

		return digest;
		
	}



	public static Input fromCsv(String[] parts){
		// parts[0] timestamp
		long heartrate = Long.parseLong(parts[1]);
		long altitude = Long.parseLong(parts[2]);
		long speed = Long.parseLong(parts[3]);
        long ascent = Long.parseLong(parts[4]);
        long descent = Long.parseLong(parts[5]);
		long temperature = Long.parseLong(parts[6]);

		Input input = new Input(heartrate, altitude, speed, ascent, descent, temperature, null);
		return input;
	}
    
}