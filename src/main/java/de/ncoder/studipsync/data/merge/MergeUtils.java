package de.ncoder.studipsync.data.merge;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class MergeUtils {
	private MergeUtils() {}

	public static <T extends Mergeable<T>> T merge(T a, T b, String fieldName) throws MergeFailedException {
		if (a == null) {
			if (b == null) {
				return null;
			} else {
				return b;
			}
		} else {
			if (b == null) {
				return a;
			} else {
				return a.merge(b);
			}
		}
	}

	public static <T> T mergePlain(T a, T b, String fieldName) throws MergeFailedException {
		if (a == null) {
			if (b == null) {
				return null;
			} else {
				return b;
			}
		} else {
			if (b == null) {
				return a;
			} else {
				if (!a.equals(b)) {
					throw new MergeFailedException(a, b, fieldName);
				}
				return a;
			}
		}
	}

	public static <T extends Mergeable<T>> List<T> mergeList(List<T> listA, List<T> listB, String fieldName) {
		List<T> out = new ArrayList<T>(Math.max(listA.size(), listB.size()));

		Iterator<T> itA = listA.iterator();
		while (itA.hasNext()) {
			T a = itA.next();

			Iterator<T> itB = listB.iterator();
			while (itB.hasNext()) {
				T b = itB.next();

				try {
					T m = merge(a, b, fieldName);
					out.add(m);
					itA.remove();
					itB.remove();
					break;
				} catch (MergeFailedException e) {
				}
			}
		}

		out.addAll(listA);
		out.addAll(listB);
		return out;
	}

	public static long mergeSize(long a, long b) throws MergeFailedException {
		if (a < 0) {
			return b;
		} else if (b < 0) {
			return a;
		} else {
			if (Math.abs(a - b) < ((a + b) / (1 * 10))) {
				if (Long.lowestOneBit(a) >= Long.lowestOneBit(b)) {
					return a;
				} else {
					return b;
				}
			} else {
				throw new MergeFailedException(a, b, "size");
			}
		}
	}

	public static Date mergeLatestDate(Date a, Date b, String fieldName) throws MergeFailedException {
		if (a == null) {
			if (b == null) {
				return null;
			} else {
				return b;
			}
		} else {
			if (b == null) {
				return a;
			} else {
				if (a.after(b)) {
					return a;
				} else {
					return b;
				}
			}
		}
	}
}
