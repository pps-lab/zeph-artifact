package ch.ethz.infk.pps.shared.avro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.google.common.base.Optional;

import ch.ethz.infk.pps.zeph.shared.DigestOp;

public class ApplicationAdapter {

	public static Input add(Input a, Input b) {

		Input result = ApplicationAdapter.empty();

		// for plaintext we only aggregate pageLoadTimes (while for digests (encrypted) we aggregate all fields)
		Map<String, Integer> pageLoadTime = a.getPageLoadTime();
		b.getPageLoadTime().forEach((key, value) -> pageLoadTime.merge(key, value, (v1, v2) -> v1 + v2));
		result.setPageLoadTime(pageLoadTime);

		long count = Optional.fromNullable(a.getCount()).or(1l) + Optional.fromNullable(b.getCount()).or(1l);
		result.setCount(count);

		return result;

	}

	public static Input empty() {
		Input input = new Input();
		input.setPageUserFlow(new HashMap<String, Integer>(1));
		input.setPageExit(new HashMap<String, Integer>(1));
		input.setPageView(new HashMap<String, Integer>(1));

		input.setPageNetworkTime(new HashMap<String, Integer>(1));
		input.setPageTransferTime(new HashMap<String, Integer>(1));
		input.setPageLoadTime(new HashMap<String, Integer>(1));
		input.setPageServerTime(new HashMap<String, Integer>(1));

		input.setHoverGrid(new HashMap<String, Integer>(1));
		input.setClickGrid(new HashMap<String, Integer>(1));

		input.setCount(0l);

		return input;
	}

	public static Input random() {

		Input input = new Input();

		// draw
		int kMainPages = 3;
		int kNumPages = 100;

		// start from one of three main pages (for which we have detailed statistics)
		int startPageInt = ThreadLocalRandom.current().nextInt(0, kMainPages);

		int newPageInt = ThreadLocalRandom.current().nextInt(0, kNumPages);
		while (newPageInt == startPageInt) {
			newPageInt = ThreadLocalRandom.current().nextInt(0, kNumPages);
		}

		String startPage = String.valueOf(startPageInt);
		String newPage = String.valueOf(newPageInt);

		input.setPageUserFlow(new HashMap<>(Map.of(startPage + "_" + newPage, 1)));

		input.setPageExit(new HashMap<>(Map.of(startPage, 1)));
		input.setPageView(new HashMap<>(Map.of(newPage, 1)));

		int networkTime = ThreadLocalRandom.current().nextInt(0, 1000);
		input.setPageNetworkTime(new HashMap<>(Map.of(newPage, networkTime)));

		int transferTime = ThreadLocalRandom.current().nextInt(0, 1000);
		input.setPageTransferTime(new HashMap<>(Map.of(newPage, transferTime)));

		int loadTime = ThreadLocalRandom.current().nextInt(0, 1000);
		input.setPageLoadTime(new HashMap<>(Map.of(newPage, loadTime)));

		int serverTime = ThreadLocalRandom.current().nextInt(0, 1000);
		input.setPageServerTime(new HashMap<>(Map.of(newPage, serverTime)));

		// set hovers
		int numberOfHovers = 30;
		List<Integer> hoverX = ThreadLocalRandom.current().ints(numberOfHovers, 0, 20).boxed()
				.collect(Collectors.toList());
		List<Integer> hoverY = ThreadLocalRandom.current().ints(numberOfHovers, 0, 11).boxed()
				.collect(Collectors.toList());
		Map<String, Integer> hoverMap = new HashMap<>(numberOfHovers);
		for (int i = 0; i < numberOfHovers; i++) {
			hoverMap.merge(startPage + "_" + hoverX.get(i) + "_" + hoverY.get(i), 1, (v1, v2) -> v1 + v2);
		}
		input.setHoverGrid(hoverMap);

		// set clicks
		int hoverIdx = ThreadLocalRandom.current().nextInt(0, numberOfHovers);
		int clickX = hoverX.get(hoverIdx);
		int clickY = hoverY.get(hoverIdx);
		input.setClickGrid(new HashMap<>(Map.of(startPage + "_" + clickX + "_" + clickY, 1)));

		input.setCount(null);

		return input;
	}

	public static Long getCount(Input input) {
		return input.getCount();
	}

	public static Long getSum(Input input) {
		return input.getPageLoadTime().getOrDefault("0", 0) + 0l;
	}

	// the main field of the event (which is used for get mean get std and so on)
	public static final String MAIN_BASE_FIELD_NAME = "pageLoadTimeSum";
	public static final int MAIN_BASE_FIELD_BITS = 32;

	public static Digest emptyDigest() {

		// website with k=100 pages and 3 main pages

		List<Long> pageExit = new ArrayList<Long>(Collections.nCopies(25, 0l)); // k=100 pages with 16bit => 25 longs
		List<Long> pageView = new ArrayList<Long>(Collections.nCopies(25, 0l)); // k=100 pages with 16bit => 25 longs


		List<Long> pageNetworkTimeSum = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageNetworkTimeSumOfSquares = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageNetworkTimeCount = new ArrayList<Long>(Collections.nCopies(25, 0l)); // k=100 pages with 16bit => 25 longs

		List<Long> pageTransferTimeSum = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageTransferTimeSumOfSquares = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageTransferTimeCount = new ArrayList<Long>(Collections.nCopies(25, 0l)); // k=100 pages with 16bit => 25 longs

		List<Long> pageLoadTimeSum = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageLoadTimeSumOfSquares = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageLoadTimeCount = new ArrayList<Long>(Collections.nCopies(25, 0l)); // k=100 pages with 16bit => 25 longs

		List<Long> pageServerTimeSum = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageServerTimeSumOfSquares = new ArrayList<Long>(Collections.nCopies(50, 0l)); // k=100 pages with 32bit => 50 longs
		List<Long> pageServerTimeCount = new ArrayList<Long>(Collections.nCopies(25, 0l)); // k=100 pages with 16bit => 25 longs

		// detailed stats for the three main pages
		List<Long> page0Userflow = new ArrayList<Long>(Collections.nCopies(25, 0l)); // for k=100 pages with 16bit => 25 longs
		List<Long> page1Userflow = new ArrayList<Long>(Collections.nCopies(25, 0l)); // for k=100 pages with 16bit => 25 longs
		List<Long> page2Userflow = new ArrayList<Long>(Collections.nCopies(25, 0l)); // for k=100 pages with 16bit => 25 longs

		List<Long> page0HoverGrid = new ArrayList<Long>(Collections.nCopies(55, 0l)); // for 20 * 11 grid (16 bit) => 55 longs
		List<Long> page1HoverGrid = new ArrayList<Long>(Collections.nCopies(55, 0l)); // for 20 * 11 grid (16 bit) => 55 longs
		List<Long> page2HoverGrid = new ArrayList<Long>(Collections.nCopies(55, 0l)); // for 20 * 11 grid (16 bit) => 55 longs

		List<Long> page0ClickGrid = new ArrayList<Long>(Collections.nCopies(55, 0l)); // for 20 * 11 grid (16 bit) => 55 longs
		List<Long> page1ClickGrid = new ArrayList<Long>(Collections.nCopies(55, 0l)); // for 20 * 11 grid (16 bit) => 55 longs
		List<Long> page2ClickGrid = new ArrayList<Long>(Collections.nCopies(55, 0l)); // for 20 * 11 grid (16 bit) => 55 longs

		List<Long> count = new ArrayList<Long>(Collections.nCopies(1, 0l)); // 64 bit => 1 long
		HeacHeader header = null;

		return new Digest(pageExit, pageView, pageNetworkTimeSum, pageNetworkTimeSumOfSquares, pageNetworkTimeCount,
				pageTransferTimeSumOfSquares, pageTransferTimeSum, pageTransferTimeCount, pageLoadTimeSum,
				pageLoadTimeSumOfSquares, pageLoadTimeCount, pageServerTimeSum, pageServerTimeSumOfSquares,
				pageServerTimeCount, page0Userflow, page1Userflow, page2Userflow, page0ClickGrid, page1ClickGrid,
				page2ClickGrid, page0HoverGrid, page1HoverGrid, page2HoverGrid, count, header);

	}

	// Web Specific Encoding
	public static Digest toDigest(Input input) {

		Digest digest = DigestOp.empty();

		int bits16 = 16;
		int bits32 = 32;

		// setting the user flow for the three main pages to all others
		input.getPageUserFlow().forEach((key, value) -> {
			String[] parts = key.split("_");
			int startPage = Integer.parseInt(parts[0]);
			int destPage = Integer.parseInt(parts[1]);

			List<Long> encoding = null;

			switch (startPage) {
			case 0:
				encoding = digest.getPage0Userflow();
				break;
			case 1:
				encoding = digest.getPage1Userflow();
				break;
			case 2:
				encoding = digest.getPage2Userflow();
				break;
			default:
				throw new IllegalArgumentException("cannot convert userflow: startpage not one of the main pages");
			}

			DigestOp.setHist(destPage, value, encoding, bits16, 0, null);
		});

		input.getHoverGrid().forEach((key, value) -> {
			String[] parts = key.split("_");
			int page = Integer.parseInt(parts[0]);
			int x = Integer.parseInt(parts[1]);
			int y = Integer.parseInt(parts[2]);

			List<Long> encoding = null;

			switch (page) {
			case 0:
				encoding = digest.getPage0HoverGrid();
				break;
			case 1:
				encoding = digest.getPage1HoverGrid();
				break;
			case 2:
				encoding = digest.getPage2HoverGrid();
				break;
			default:
				throw new IllegalArgumentException("cannot convert hover grid: page not one of the main pages");
			}
			int idx = x * 11 + y;
			DigestOp.setHist(idx, value, encoding, bits16, 0, null);
		});

		input.getClickGrid().forEach((key, value) -> {
			String[] parts = key.split("_");
			int page = Integer.parseInt(parts[0]);
			int x = Integer.parseInt(parts[1]);
			int y = Integer.parseInt(parts[2]);

			List<Long> encoding = null;

			switch (page) {
			case 0:
				encoding = digest.getPage0ClickGrid();
				break;
			case 1:
				encoding = digest.getPage1ClickGrid();
				break;
			case 2:
				encoding = digest.getPage2ClickGrid();
				break;
			default:
				throw new IllegalArgumentException("cannot convert click grid: page not one of the main pages");
			}
			int idx = x * 11 + y;
			DigestOp.setHist(idx, value, encoding, bits16, 0, null);
		});

		input.getPageExit().forEach((k, v) -> {
			long page = Long.parseLong(k);
			DigestOp.setHist(page, v, digest.getPageExit(), bits16, 0, null);
		});

		input.getPageView().forEach((k, v) -> {
			long page = Long.parseLong(k);
			DigestOp.setHist(page, v, digest.getPageView(), bits16, 0, null);
		});

		// performance metrics
		input.getPageNetworkTime().forEach((pageStr, time) -> {

			int page = Integer.parseInt(pageStr);
			long timeSquared = (long) Math.pow(time, 2);
			long count = 1l;

			DigestOp.setHist(page, time, digest.getPageNetworkTimeSum(), bits32, 0, null);
			DigestOp.setHist(page, timeSquared, digest.getPageNetworkTimeSumOfSquares(), bits32, 0, null);
			DigestOp.setHist(page, count, digest.getPageNetworkTimeCount(), bits16, 0, null);
		});

		input.getPageTransferTime().forEach((pageStr, time) -> {

			int page = Integer.parseInt(pageStr);
			long timeSquared = (long) Math.pow(time, 2);
			long count = 1l;

			DigestOp.setHist(page, time, digest.getPageTransferTimeSum(), bits32, 0, null);
			DigestOp.setHist(page, timeSquared, digest.getPageTransferTimeSumOfSquares(), bits32, 0, null);
			DigestOp.setHist(page, count, digest.getPageTransferTimeCount(), bits16, 0, null);
		});

		input.getPageLoadTime().forEach((pageStr, time) -> {

			int page = Integer.parseInt(pageStr);
			long timeSquared = (long) Math.pow(time, 2);
			long count = 1l;

			DigestOp.setHist(page, time, digest.getPageLoadTimeSum(), bits32, 0, null);
			DigestOp.setHist(page, timeSquared, digest.getPageLoadTimeSumOfSquares(), bits32, 0, null);
			DigestOp.setHist(page, count, digest.getPageLoadTimeCount(), bits16, 0, null);
		});

		input.getPageServerTime().forEach((pageStr, time) -> {

			int page = Integer.parseInt(pageStr);
			long timeSquared = (long) Math.pow(time, 2);
			long count = 1l;

			DigestOp.setHist(page, time, digest.getPageServerTimeSum(), bits32, 0, null);
			DigestOp.setHist(page, timeSquared, digest.getPageServerTimeSumOfSquares(), bits32, 0, null);
			DigestOp.setHist(page, count, digest.getPageServerTimeCount(), bits16, 0, null);
		});

		digest.getCount().set(0, 1l);

		return digest;

	}

	public static Input fromCsv(String[] parts) {
		throw new IllegalStateException("reading from csv is not implemented for the web application");
	}

}