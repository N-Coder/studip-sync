package de.ncoder.studipsync.data.merge;

public class MergeFailedException extends Exception {
	private Object valueA;
	private Object valueB;
	private String field;

	public MergeFailedException(Object valueA, Object valueB, String field) {
		this.valueA = valueA;
		this.valueB = valueB;
		this.field = field;
	}

	public MergeFailedException(Object valueA, Object valueB) {
		this.valueA = valueA;
		this.valueB = valueB;
	}

	public MergeFailedException(Throwable cause) {
		super(cause);
	}

	public MergeFailedException(Throwable cause, String field) {
		super(cause);
		this.field = field;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public Object getValueA() {
		return valueA;
	}

	public Object getValueB() {
		return valueB;
	}

	@Override
	public String getMessage() {
		if (getValueA() != null || getValueB() != null) {
			return "Field " + getField() + " doesn't match: " + getValueA() + " =/= " + getValueB();
		} else if (getField() != null) {
			return "Exception while merging " + getField();
		} else {
			return "Exception while merging";
		}
	}
}