package de.ncoder.studipsync.adapter.progress;

public class RelativeProgress implements Progress {
	private float value;
	private float maximum;

	public RelativeProgress() {
		this(1);
	}

	public RelativeProgress(float maximum) {
		super();
		setMaximum(maximum);
	}

	public RelativeProgress(float value, float maximum) {
		this(maximum);
		setValue(value);
	}

	@Override
	public float getProgress() {
		return value / maximum;
	}

	public float getValue() {
		return value;
	}

	public void setValue(float value) {
		this.value = value;
	}

	public void increment(float value) {
		setValue(getValue() + value);
	}

	public float getMaximum() {
		return maximum;
	}

	public void setMaximum(float maximum) {
		this.maximum = maximum;
	}

	public void set(float value, float maximum) {
		setMaximum(maximum);
		setValue(value);
	}
}