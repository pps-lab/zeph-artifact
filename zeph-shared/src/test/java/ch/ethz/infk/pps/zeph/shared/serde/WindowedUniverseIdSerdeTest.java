package ch.ethz.infk.pps.zeph.shared.serde;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.google.common.primitives.UnsignedBytes;

import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.Window;

public class WindowedUniverseIdSerdeTest {

	@Test
	public void testOrdering() {

		WindowedUniverseIdSerde serde = new WindowedUniverseIdSerde();

		long universe1 = 1l;
		long universe2 = 2l;
		long universe3 = 3l;

		long windowSize = 10000;
		long windowStart1 = 1581861980000l;
		long windowStart2 = windowStart1 + windowSize;
		long windowStart3 = windowStart2 + windowSize;

		Window window1 = new Window(windowStart1, windowStart1 + windowSize);
		Window window2 = new Window(windowStart2, windowStart2 + windowSize);
		Window window3 = new Window(windowStart3, windowStart3 + windowSize);

		WindowedUniverseId v1 = new WindowedUniverseId(universe1, window1);
		WindowedUniverseId v2 = new WindowedUniverseId(universe1, window2);
		WindowedUniverseId v3 = new WindowedUniverseId(universe1, window3);

		WindowedUniverseId v4 = new WindowedUniverseId(universe2, window1);
		WindowedUniverseId v5 = new WindowedUniverseId(universe2, window2);
		WindowedUniverseId v6 = new WindowedUniverseId(universe2, window3);

		WindowedUniverseId v7 = new WindowedUniverseId(universe3, window1);
		WindowedUniverseId v8 = new WindowedUniverseId(universe3, window2);
		WindowedUniverseId v9 = new WindowedUniverseId(universe3, window3);

		List<WindowedUniverseId> expectedResult = Arrays.asList(v1, v2, v3, v4, v5, v6, v7, v8, v9);

		List<byte[]> byteRepresentations = expectedResult.stream().map(v -> serde.serializer().serialize(null, v))
				.collect(Collectors.toList());

		Collections.shuffle(byteRepresentations);
		Collections.sort(byteRepresentations, UnsignedBytes.lexicographicalComparator());

		List<WindowedUniverseId> result = byteRepresentations.stream()
				.map(b -> serde.deserializer().deserialize(null, b))
				.collect(Collectors.toList());

		for (int i = 0; i < expectedResult.size(); i++) {
			assertEquals(expectedResult.get(i), result.get(i));
		}

		serde.close();

	}

}
