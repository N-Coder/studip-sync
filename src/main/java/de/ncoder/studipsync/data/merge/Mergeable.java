package de.ncoder.studipsync.data.merge;

public interface Mergeable<T> {
	public T merge(T other) throws MergeFailedException;
}