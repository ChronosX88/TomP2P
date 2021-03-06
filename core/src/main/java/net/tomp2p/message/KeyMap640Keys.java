package net.tomp2p.message;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.Set;

import net.tomp2p.peers.Number256;
import net.tomp2p.utils.Utils;

public class KeyMap640Keys {

	private final NavigableMap<Object, Collection<Number256>> keysMap;

	public KeyMap640Keys(NavigableMap<Object, Collection<Number256>> keysMap) {
		this.keysMap = keysMap;
	}

	public NavigableMap<Object, Collection<Number256>> keysMap() {
		return keysMap;
	}

	public int size() {
		return keysMap.size();
	}

	public void put(Object key, Set<Number256> value) {
		keysMap.put(key, value);
	}

	@Override
	public int hashCode() {
		return keysMap.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (!(obj instanceof KeyMap640Keys)) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		final KeyMap640Keys k = (KeyMap640Keys) obj;
		final boolean test1 = Utils.isSameSets(k.keysMap.keySet(),
				keysMap.keySet());
		final boolean test2 = Utils.isSameCollectionSets(k.keysMap.values(),
				keysMap.values());
		return test1 && test2;
	}
}
