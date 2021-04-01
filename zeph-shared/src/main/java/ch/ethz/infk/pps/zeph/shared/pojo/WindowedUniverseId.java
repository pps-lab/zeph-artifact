package ch.ethz.infk.pps.zeph.shared.pojo;

import ch.ethz.infk.pps.shared.avro.Window;

public class WindowedUniverseId {

	private long universeId;
	private Window window;

	public WindowedUniverseId() {

	}

	public WindowedUniverseId(long universeId, Window window) {
		this.universeId = universeId;
		this.window = window;
	}

	public long getUniverseId() {
		return universeId;
	}

	public void setUniverseId(long universeId) {
		this.universeId = universeId;
	}

	public Window getWindow() {
		return window;
	}

	public void setWindow(Window window) {
		this.window = window;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (universeId ^ (universeId >>> 32));
		result = prime * result + ((window == null) ? 0 : window.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WindowedUniverseId other = (WindowedUniverseId) obj;
		if (universeId != other.universeId)
			return false;
		if (window == null) {
			if (other.window != null)
				return false;
		} else if (!window.equals(other.window))
			return false;
		return true;
	}

}
