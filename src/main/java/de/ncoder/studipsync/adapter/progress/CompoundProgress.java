package de.ncoder.studipsync.adapter.progress;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CompoundProgress implements Progress {
	private Map<Progress, Float> children = new HashMap<Progress, Float>();

	public CompoundProgress() {
		super();
	}

	@Override
	public float getProgress() {
		float p = 0;
		for (Entry<Progress, Float> c : children.entrySet()) {
			p += c.getKey().getProgress() * c.getValue();
		}
		return p;
	}

	public void spreadPercentages() {
		for (Entry<Progress, Float> c : children.entrySet()) {
			c.setValue(1f / children.size());
		}
	}

	public void addChild(Progress child, float percentage) {
		children.put(child, percentage);
	}

	public void removeChild(Progress child) {
		children.remove(child);
	}

	public Map<Progress, Float> getChildren() {
		return children;
	}
}
