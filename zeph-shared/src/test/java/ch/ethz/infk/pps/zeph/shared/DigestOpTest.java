package ch.ethz.infk.pps.zeph.shared;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ch.ethz.infk.pps.shared.avro.ApplicationAdapter;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.Input;
import ch.ethz.infk.pps.shared.avro.TransformationResult;
import ch.ethz.infk.pps.shared.avro.Window;

public class DigestOpTest {

    @Test
	public void testInputDigest() {

        for(int i = 0; i < 1000; i++){
            Input input1 = ApplicationAdapter.random();
            Input input2 = ApplicationAdapter.random();

            Digest digest1 = ApplicationAdapter.toDigest(input1);
            Digest digest2 = ApplicationAdapter.toDigest(input2);

            Digest result = DigestOp.add(digest1, digest2);
            //System.out.println(input1);
            //System.out.println(input2);
            //System.out.println(DigestOp.formatHist(result.getPageNetworkTimeSum(), 32));

            Input inputResult = ApplicationAdapter.add(input1, input2);
            Window window = new Window();

            TransformationResult expectedResult = TransformationResultOp.of(inputResult, window);
            TransformationResult actualResult = TransformationResultOp.of(result, window);
            assertEquals("expected= " + expectedResult, expectedResult, actualResult);
        }
    }
}
