/******************************************************************************
 * Copyright 2025 (C) by Daniele D'Ugo <danieledugo1@gmail.com>               *
 *                                                                            *
 * This program is free software; you can redistribute it and/or modify       *
 * it under the terms of the GNU Library General Public License as            *
 * published by the Free Software Foundation; either version 2 of the         *
 * License, or (at your option) any later version.                            *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU Library General Public          *
 * License along with this program; if not, write to the                      *
 * Free Software Foundation, Inc.,                                            *
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.                  *
 *                                                                            *
 * For details about the authors of this software, see the AUTHORS file.      *
 ******************************************************************************/

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
