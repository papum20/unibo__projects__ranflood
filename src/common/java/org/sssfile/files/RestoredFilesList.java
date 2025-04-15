/******************************************************************************
 * Copyright 2024 (C) by Daniele D'Ugo <danieledugo1@gmail.com>               *
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

 package org.sssfile.files;

 import com.republicate.json.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
 import java.util.stream.Collectors;

 import org.sssfile.exceptions.InvalidShardException;
import org.sssfile.files.RestoredFilesList.OriginalFileEntry;


public class RestoredFilesList extends LinkedHashMap<Integer, OriginalFileEntry> {


	public RestoredFilesList() {
		super();
	}

	private RestoredFilesList(Map<Integer, OriginalFileEntry> map) {
		super(map);
	}

	public static RestoredFilesList fromJson(Json json) {
		return new RestoredFilesList( json.asArray()
				.stream()
				.map( e -> ( Json.Object ) e )
				.collect( Collectors.toMap(
						e -> Integer.valueOf( e.get( "hashCode" ).toString() ),
						e -> OriginalFileEntry.fromJson(( Json.Array ) e.get( "shards" ))
				))
		);

	}


	public void addShard(ShardFile shard) {

		OriginalFileEntry original_file = get(shard.hashCode());
		if(original_file == null) {
			original_file = new OriginalFileEntry(shard.original_file, true);
			put(shard.hashCode(), original_file);
		}
		
		original_file.add(shard.path);
	}


	public Json.Array toJsonArray() {
		Json.Array a = new Json.Array();
		forEach( ( key, value ) -> {
			// object containing list of shards paths
			Json.Array a_shards = new Json.Array();
			value.forEach( shard -> {
				Json.Object o = new Json.Object();
				o.put( "path", shard.toString() );
				a_shards.add( o );
			} );

			// object containing original files 
			Json.Object o = new Json.Object();
			o.put( "hashCode", key );
			o.put( "shards", a_shards );
			a.add( o );
		} );
		return a;
	}


	/**
	 * An entry for an original file, represented as the list of its shards paths,
	 * so that their contents can be retrieved later.
	 */
	public static class OriginalFileEntry extends LinkedList<Path> {

		private Path path;
		/**
		 * Whether the path refers to the original file or only to a shards of its
		 * (because the original path was not found yet).
		 */
		private Boolean is_original_path = false;

		/**
		 * Instantiate an Original File Entry, either alredy giving its original path,
		 * or only that of one of its shards, if we don't have it yet.
		 * @param path
		 * @param is_original_path
		 */
		public OriginalFileEntry(Path path, Boolean is_original_path) {
			super();
			this.path = path;
			this.is_original_path = is_original_path;
		}

		private OriginalFileEntry(Collection<Path> values) {
			super(values);
			if (!isEmpty())
				this.path = getFirst();
		}

		public static OriginalFileEntry fromJson(Json.Array json) {
			return new OriginalFileEntry(
				json.stream()
				.map( shard -> Path.of(( String) (( Json.Object ) shard).get( "path" )) )
				.collect( Collectors.toList() )
			);
		}


		@Override
		public boolean add(Path path) {
			return super.add(path);
		}


		public OriginalFile getOriginalFile() {

			OriginalFile original_file = null;

			for (Path path : this) {

				try {
					ShardFile shard = ShardFile.fromFile(path);
					if (original_file == null) {
						original_file = shard.getOriginalFile();
					}
					original_file.addShard(path, shard.key, shard.shard);
				} catch (InvalidShardException | IOException e) {
					// shouldn't happen here:
					// if a shard arrived here, it was already considered valid when adding it
					e.printStackTrace();
				}

			}

			return original_file;
		}

		public Path getPath() {
			return path;
		}


		/**
		 * Set an original path for this entry (not a shard's path).
		 * @param path
		 */
		public void updateOriginalPath(Path path) {
			this.path = path;
			this.is_original_path = true;
		}

	}

}


