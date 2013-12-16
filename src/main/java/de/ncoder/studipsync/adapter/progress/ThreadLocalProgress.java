package de.ncoder.studipsync.adapter.progress;

public class ThreadLocalProgress {
	private ThreadLocalProgress() {}

	private static ThreadLocal<RelativeProgress> progress = new ThreadLocal<RelativeProgress>();

	public static void set(RelativeProgress progress) {
		ThreadLocalProgress.progress.set(progress);
	}

	public static RelativeProgress get() {
		return progress.get();
	}

	public static boolean isSet() {
		return get() != null;
	}

	public static float getProgress() {
		if (isSet()) {
			return get().getProgress();
		} else {
			return 0;
		}
	}

	public static void setValue(float value) {
		if (isSet()) {
			get().setValue(value);
		}
	}

	public static void increment(float value) {
		if (isSet()) {
			get().increment(value);
		}
	}

	public static void set(float value, float maximum) {
		if (isSet()) {
			get().set(value, maximum);
		}
	}

	public static float getValue() {
		if (isSet()) {
			return get().getValue();
		} else {
			return 0;
		}
	}

	public static float getMaximum() {
		if (isSet()) {
			return get().getMaximum();
		} else {
			return 0;
		}
	}

	public static void setMaximum(float maximum) {
		if (isSet()) {
			get().setMaximum(maximum);
		}
	}
}