package ch.ethz.infk.pps.zeph.clientdemo.drivers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.apache.kafka.clients.consumer.ConsumerRecords;

import ch.ethz.infk.pps.zeph.client.facade.IPrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.client.facade.PrivacyControllerFacade;
import ch.ethz.infk.pps.zeph.clientdemo.config.DashboardDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.config.UniverseDefinition;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.Color;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.Dashboard;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.MembershipHistory;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.Result;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.ResultHistory;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.TextWidget;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.Timeline;
import ch.ethz.infk.pps.zeph.clientdemo.util.dashboard.TimelineEvent;
import ch.ethz.infk.pps.zeph.shared.WindowUtil;
import ch.ethz.infk.pps.zeph.shared.pojo.ImmutablePair;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowStatus;
import ch.ethz.infk.pps.zeph.shared.pojo.WindowedUniverseId;
import ch.ethz.infk.pps.shared.avro.Digest;
import ch.ethz.infk.pps.shared.avro.DeltaUniverseState;
import ch.ethz.infk.pps.shared.avro.Window;

public class SmashingDashboardDriver extends Driver {

	private Client restClient;
	private IPrivacyControllerFacade facade;

	private TreeMap<Window, WindowStatus> statusHistory;
	private TreeMap<Window, Digest> resultHistory;
	private TreeMap<Window, ImmutablePair<Integer, String>> membershipHistory;

	private Dashboard dashboard;
	private String authToken;
	private int membershipHistoryLimit;
	private int statusHistoryLimit;
	private int resultHistoryLimit;
	private long universeId;
	private int universeMemberThreshold;
	private long windowSizeMillis;

	public SmashingDashboardDriver(DashboardDefinition dDef, UniverseDefinition uDef,
			String kafkaBootstrapServers, Duration pollTimeout) {

		this.universeId = uDef.getUniverseId();

		this.facade = new PrivacyControllerFacade(0, kafkaBootstrapServers, pollTimeout);
		this.facade.init(Collections.singleton(universeId));

		Comparator<Window> comp = new Comparator<Window>() {
			@Override
			public int compare(Window o1, Window o2) {
				return Long.compare(o1.getStart(), o2.getStart());
			}
		};

		this.statusHistory = new TreeMap<>(comp);
		this.resultHistory = new TreeMap<>(comp);
		this.membershipHistory = new TreeMap<>(comp);

		this.dashboard = new Dashboard(dDef.getHostInfo());
		this.authToken = dDef.getAuthToken();
		this.membershipHistoryLimit = dDef.getMembershipHistoryLimit();
		this.statusHistoryLimit = dDef.getStatusHistoryLimit();
		this.resultHistoryLimit = dDef.getResultHistoryLimit();

		this.universeMemberThreshold = uDef.getMemberThreshold();
		this.windowSizeMillis = uDef.getWindowSizeMillis();

		this.restClient = ClientBuilder.newBuilder()
				.connectTimeout(1, TimeUnit.SECONDS)
				.readTimeout(1, TimeUnit.SECONDS)
				.build();

		initDashboard(universeId, Duration.ofMillis(windowSizeMillis), universeMemberThreshold);
	}

	@Override
	protected void drive() {

		try {
			while (true) {
				if (isShutdownRequested()) {
					break;
				}

				ConsumerRecords<WindowedUniverseId, DeltaUniverseState> records = facade.pollInfos();
				records.forEach(record -> {
					WindowedUniverseId windowedUniverseId = record.key();
					if (windowedUniverseId.getUniverseId() == this.universeId) {

						Window window = windowedUniverseId.getWindow();
						DeltaUniverseState state = record.value();
						WindowStatus status = WindowStatus.of(state.getStatus());

						while (statusHistory.size() >= statusHistoryLimit) {
							statusHistory.pollFirstEntry();
						}
						statusHistory.put(window, status);

						int windowId = Dashboard.getWindowIdDay(window);

						List<TimelineEvent> timeline = buildTimeline(statusHistory);
						updateTimeline(timeline);

						if (status == WindowStatus.CLOSED) {

							while (resultHistory.size() >= resultHistoryLimit) {
								resultHistory.pollFirstEntry();
							}
							double previous = Dashboard.getMean(resultHistory.lastEntry());

							resultHistory.put(window, state.getResult());
							ResultHistory history = new ResultHistory(resultHistory, authToken);

							double current = Dashboard.getMean(resultHistory.lastEntry());

							String moreInfo = String.format("Window %d - %s", windowId, Dashboard.formatWindow(window));

							Result result = new Result(current, previous, moreInfo, authToken);
							updateResult(result);
							updateResultHistory(history);
						} else if (status == WindowStatus.MERGED) {
							Optional<Integer> diff = state.getMemberDiff().values().stream()
									.reduce((v1, v2) -> v1 + v2);

							String newMembers = state.getMemberDiff().entrySet().stream()
									.filter(e -> e.getValue() == 1)
									.map(e -> e.getKey().toString()).collect(Collectors.joining(", "));

							String droppedMembers = state.getMemberDiff().entrySet().stream()
									.filter(e -> e.getValue() == -1)
									.map(e -> e.getKey().toString()).collect(Collectors.joining(", "));

							int prevUniverseSize = Optional.ofNullable(membershipHistory.lastEntry())
									.map(e -> e.getValue().getLeft())
									.orElse(0);
							int universeSize = prevUniverseSize + diff.orElse(0);

							while (membershipHistory.size() >= membershipHistoryLimit) {
								membershipHistory.pollFirstEntry();
							}

							String diffStr = String.format("Universe Size %d\n+\t[%s]\n-\t[%s]", universeSize,
									newMembers,
									droppedMembers);

							membershipHistory.put(windowedUniverseId.getWindow(),
									ImmutablePair.of(universeSize, diffStr));
							updateMembershipHistory(
									new MembershipHistory(membershipHistory, universeMemberThreshold, authToken));
						}
					}
				});
			}
		} finally {
			facade.close();
		}

	}

	private List<TimelineEvent> buildTimeline(TreeMap<Window, WindowStatus> statusHistory) {
		List<TimelineEvent> timeline = new ArrayList<>();

		long prevDayWindow = -1;
		long dayWindowSize = Duration.ofDays(1).toMillis();

		for (Entry<Window, WindowStatus> e : statusHistory.entrySet()) {
			Window window = e.getKey();
			long windowStart = window.getStart();
			WindowStatus status = e.getValue();

			long dayWindow = WindowUtil.getWindowStart(windowStart, dayWindowSize);
			if (dayWindow > prevDayWindow) {
				prevDayWindow = dayWindow;
				long t = Math.max(windowStart - windowSizeMillis, dayWindow);
				TimelineEvent daybreak = new TimelineEvent()
						.withName("=====================")
						.withDate(t)
						.withBackground(Color.INACTIVE);
				timeline.add(daybreak);
			}

			int windowId = Dashboard.getWindowIdDay(window);
			double opacity = status == WindowStatus.CLOSED ? 0.5 : 1;

			TimelineEvent event = new TimelineEvent()
					.withName(String.format("Window %d - %s", windowId, status.toString().toLowerCase()))
					.withDate(windowStart)
					.withBackground(fromStatusToColor(status))
					.withOpacity(opacity);

			timeline.add(event);
		}

		long end = statusHistory.firstKey().getStart() + statusHistoryLimit * windowSizeMillis;
		TimelineEvent endMarker = new TimelineEvent()
				.withName("")
				.withDate(end)
				.withBackground(Color.INACTIVE);

		timeline.add(endMarker);

		return timeline;
	}

	private void initDashboard(long universeId, Duration windowSize, int universeSizeThreshold) {

		String title = String.format("PCrypt :: Universe %d", universeId);
		String text = String.format("Window %s", windowSize.toString().replaceFirst("PT", "").toLowerCase());
		String moreInfo = String.format("Threshold %d Members", universeSizeThreshold);

		TextWidget textWidget = new TextWidget(title, text, moreInfo, authToken);

		restClient.target(dashboard.getUniverseInfoUri())
				.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.json(textWidget));

		updateTimeline(new ArrayList<TimelineEvent>());
		updateMembershipHistory(new MembershipHistory(membershipHistory, membershipHistoryLimit, authToken));
		updateResultHistory(new ResultHistory(authToken));
		updateResult(new Result(0.0, 0.0, "", authToken));

	}

	private void updateTimeline(List<TimelineEvent> timeline) {
		Timeline entity = new Timeline();
		entity.setEvents(timeline);

		restClient.target(dashboard.getStatusHistoryUri())
				.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.json(entity));
	}

	private void updateResultHistory(ResultHistory history) {
		restClient.target(dashboard.getResultHistoryUri())
				.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.json(history.toJsonString()));
	}

	private void updateResult(Result result) {
		restClient.target(dashboard.getResultLiveUri())
				.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.json(result));
	}

	private void updateMembershipHistory(MembershipHistory history) {
		restClient.target(dashboard.getMembershipHistoryUri())
				.request()
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(Entity.json(history.toJsonString()));
	}

	private Color fromStatusToColor(WindowStatus status) {
		switch (status) {
		case OPEN:
			return Color.SHADE2;
		case STAGED:
			return Color.SHADE3;
		case COMMITTED:
			return Color.SHADE4;
		case MERGED:
			return Color.SHADE5;
		case CLOSED:
			return Color.INACTIVE;
		default:
			throw new IllegalArgumentException();
		}
	}

}