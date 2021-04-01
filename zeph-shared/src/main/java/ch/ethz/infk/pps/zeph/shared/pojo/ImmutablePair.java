package ch.ethz.infk.pps.zeph.shared.pojo;

public class ImmutablePair<L, R> {

	private final L left;
	private final R right;

	public ImmutablePair(final L left, final R right) {
		this.left = left;
		this.right = right;
	}

	public L getLeft() {
		return left;
	}

	public R getRight() {
		return right;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((left == null) ? 0 : left.hashCode());
		result = prime * result + ((right == null) ? 0 : right.hashCode());
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
		ImmutablePair<?, ?> other = (ImmutablePair<?, ?>) obj;
		if (left == null) {
			if (other.left != null)
				return false;
		} else if (!left.equals(other.left))
			return false;
		if (right == null) {
			if (other.right != null)
				return false;
		} else if (!right.equals(other.right))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ImmutablePair [left=" + left + ", right=" + right + "]";
	}

	public static <L, R> ImmutablePair<L, R> of(final L left, final R right) {
		return new ImmutablePair<>(left, right);
	}

}
