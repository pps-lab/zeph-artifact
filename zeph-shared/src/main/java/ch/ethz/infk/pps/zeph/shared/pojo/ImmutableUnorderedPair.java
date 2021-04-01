package ch.ethz.infk.pps.zeph.shared.pojo;

import java.util.HashSet;
import java.util.Set;

public class ImmutableUnorderedPair<T> {

	private final Set<T> set;

	public ImmutableUnorderedPair(T a, T b) {
		if (a.equals(b)) {
			throw new IllegalArgumentException("pair of the same not allowed");
		}
		this.set = new HashSet<>(2);
		this.set.add(a);
		this.set.add(b);
	}

	@Override
	public String toString() {
		String[] array = set.stream().map(e -> e.toString()).sorted().toArray(String[]::new);
		return String.join("_", array);

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((set == null) ? 0 : set.hashCode());
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
		ImmutableUnorderedPair<?> other = (ImmutableUnorderedPair<?>) obj;
		if (set == null) {
			if (other.set != null)
				return false;
		} else if (!set.equals(other.set))
			return false;
		return true;
	}

	public static <T> ImmutableUnorderedPair<T> of(final T a, final T b) {
		return new ImmutableUnorderedPair<>(a, b);
	}

}
