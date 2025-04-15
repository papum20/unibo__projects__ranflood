package org.ranflood.util;

import java.util.LinkedHashMap;

public class Collections {

	/**
	 * 
	 * @param <K>
	 * @param <V>
	 * @param collection
	 * @param n
	 * @return the first n elements of the collection, or the whole collection if it has less than n elements
	 */
	public static <K, V> LinkedHashMap<K, V> subset(LinkedHashMap<K, V>  collection, int n) {

		if (n >= collection.size()) {
			return collection;
		}

		LinkedHashMap<K, V> result = new LinkedHashMap<>();
		int i = 0;
		for (K key : collection.keySet()) {
			if (i >= n) {
				break;
			}
			result.put(key, collection.get(key));
			i++;
		}
		return result;
	}
	
}
