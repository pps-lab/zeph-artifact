package ch.ethz.infk.pps.zeph.shared;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.Window;

public class TransformationResultOp {

    // could extract more information into result but here we just use the sum of the main field to not spam the output

    public static TransformationResult of(Input input, Window window){
        TransformationResult result = new TransformationResult();
        result.setWindow(window);
        result.setCount(input.getCount());
        result.setSum(ApplicationAdapter.getSum(input));
        return result;
    }

    public static TransformationResult of(Digest digest, Window window){
        TransformationResult result = new TransformationResult();
        result.setWindow(window);
        result.setCount(DigestOp.getCount(digest));
        result.setSum(DigestOp.getSum(digest));
        return result;
    } 
}
