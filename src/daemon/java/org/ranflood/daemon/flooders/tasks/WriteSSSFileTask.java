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

package org.ranflood.daemon.flooders.tasks;

import org.ranflood.common.FloodMethod;
import org.ranflood.util.Jvm;
import org.sssfile.SSSSplitter;
import org.sssfile.exceptions.InvalidOriginalFileException;
import org.sssfile.files.FileNamesGenerator;
import org.sssfile.files.OriginalFile;
import org.sssfile.util.Security;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.ranflood.common.RanfloodLogger.log;
import static org.ranflood.common.RanfloodLogger.error;

public class WriteSSSFileTask extends WriteFileTask {

	private final SSSSplitter sss;
	private final String signature;


	public WriteSSSFileTask(Path filePath, byte[] content, FloodMethod floodMethod,  SSSSplitter sss, String signature ) {
		super( filePath, content, floodMethod );
		this.sss		= sss;
		this.signature	= signature;
	}

	public Runnable getRunnableTask() {
		return () -> {

			// in case of OutOfMemoryError, wait and retry with smaller n,k
			int retries_timeout_ms = 100;
			int retries_max = 8;	// 2**8 = 256, so will try all values of n until it's too small (<2)
			int retries_counter = 0;
			int new_n = sss.n,
				new_k = sss.k;

			while (retries_counter >= retries_max) {

				log("Task SSS for: " + filePath() + "; signature: " + signature + "; memoryFree: " + Jvm.freeMemory());

				File parentFolder = filePath().getParent().toFile();
				if ( !parentFolder.exists() ) {
					synchronized ( filePath() ) {
						parentFolder.mkdirs();
					}
				}

				try {
					// split with sss
					//long time_start = System.currentTimeMillis();
					OriginalFile original_file =
							(retries_counter > 0)
							? sss.getSplitFile( filePath(), content(), Security.hash_fromBase64(signature) )
							: sss.getSplitFile(
									filePath(), content(), Security.hash_fromBase64(signature),
									 new_n, new_k);
					//long time_end = System.currentTimeMillis();
					//System.out.println(filePath() + ", time split: " + (time_end - time_start));

					// try to write all shards
					int shards_created = 0;
					byte[] shard_content;
					while(true) {

						shard_content = original_file.iterateShardContent();
						if(shard_content == null)
							break;

						Path shard_path = FileNamesGenerator.getUniquePath(filePath().toString());

						try {
							writeFile(shard_path, shard_content);
							shards_created++;
						} catch ( IOException e ) {
							error( e.getMessage() );
						}
					}

					// original file's removal is a single-use task, while this task will be retried in case of error
					/*
					// if enough shards weren't created, for any reason, better recreate original file, so it's not lost
					if(shards_created < sss.k && !filePath().toFile().exists() ) {
						writeFile(filePath(), content());
					}
					 */

				} catch (IOException | NoSuchAlgorithmException e ) {
					error( e.getMessage() );
				} catch ( InvalidOriginalFileException e ) {
					// it just means it's a shard and won't be split again
					// don't log, as there could be a lot of logs, and IO is very expensive
				} catch ( OutOfMemoryError e ) {
					// maybe the task will be able to run later
					error( "Out of memory, when splitting with SSS (try " + retries_counter + "/" + retries_max + "): " + filePath() );

					// retry
					if (new_n <= 2)
						break;

					retries_counter++;
					new_n = (int) Math.ceil(new_n / 2.0);
					new_k = Math.min(new_n, sss.k);	// keep same k, or lower it if it's larger than n
					try {
						Thread.sleep( retries_timeout_ms );
					} catch ( InterruptedException error ) {
						error.printStackTrace();
					}
				}
			}

		};
	}

	private static void writeFile(Path path, byte[] content) throws IOException {
		FileOutputStream f = new FileOutputStream( path.toAbsolutePath().toString() );
		BufferedOutputStream bout = new BufferedOutputStream( f );
		bout.write( content );
		bout.close();
		f.close();
	}

}
