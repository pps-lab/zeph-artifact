package ch.ethz.infk.pps.shared.avro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Optional;

import ch.ethz.infk.pps.zeph.shared.DigestOp;

public class ApplicationAdapter {

    public static Input add(Input a, Input b){
		return new Input(a.getValue() + b.getValue(),
		Optional.fromNullable(a.getCount()).or(1l) + Optional.fromNullable(b.getCount()).or(1l));
    }

    public static Input empty(){
        return new Input(0l, 0l);
    }

    public static Input random(){
        long value = ThreadLocalRandom.current().nextLong(0, 100);
		return new Input(value, null);
    }


	public static Long getCount(Input input){
		return input.getCount();	
	}


	public static Long getSum(Input input){
		return input.getValue();
	}


	// the main field of the event (which is used for get mean get std and so on)
	public static final String MAIN_BASE_FIELD_NAME = "valueBase";
	public static final int MAIN_BASE_FIELD_BITS = 64;


	public static Digest emptyDigest(){

		List<Long> valueBase = new ArrayList<Long>(Collections.nCopies(2, 0l));
		List<Long> count = new ArrayList<Long>(Collections.nCopies(1, 0l));
		HeacHeader header = null;

		return new Digest(valueBase, count, header);
	}

	// Standard Specific Encoding
	public static Digest toDigest(Input input){

		Digest digest = DigestOp.empty();

		// set the count
		digest.getCount().set(0, 1l);

		// set the base fields with sum and sum of squares
		DigestOp.setBase(input.getValue(), digest.getValueBase());
		
		return digest;
		
	}


	public static Input fromCsv(String[] parts) {
		throw new IllegalStateException("reading from csv is not implemented for the standard application");
	}
    
}