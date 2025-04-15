/******************************************************************************
 * Copyright 2021 (C) by Saverio Giallorenzo <saverio.giallorenzo@gmail.com>  *
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

package org.ranflood.filechecker.runtime;

import com.republicate.json.Json;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.ranflood.filechecker.runtime.Utils.getFileSignature;

public class Check {

  public static void run( File checksum, File folder, File report, Boolean deep ) throws IOException {
    if ( !Files.exists( checksum.toPath().toAbsolutePath().getParent() ) )
      throw new IOException( "could not file checksum file " + checksum.toPath() );
    if ( !Files.exists( folder.toPath() ) )
      throw new IOException( "folder " + folder + " does not exist" );
    if ( !Files.isDirectory( folder.toPath() ) )
      throw new IOException( folder + " is not a directory" );
    Json jsonChecksum = Json.parse( Files.readString( checksum.toPath() ) );
    Map< String, String > checksumMap = jsonChecksum.asArray().
        stream()
        .map( e -> ( Json.Object ) e )
        .collect( Collectors.toMap(
            e -> e.get( "path" ).toString(),
            e -> e.get( "checksum" ).toString() )
        );
//		Map< String, String > checksumMap =
//						new HashMap<>( Files.readAllLines( checksum.toPath() ).stream()
//										.map( l -> l.split( "," ) )
//										.collect( Collectors.toUnmodifiableMap( s -> s[ 0 ], s -> s[ 1 ] ) ) );
    // first we check if we can find all files

    Map< String, String > reportContent = check( folder, checksumMap, null, null, deep, false );

    Json.Array a = new Json.Array();
    reportContent.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key );
      o.put( "checksum", value );
      a.add( o );
    } );
    Files.writeString( report.toPath(), a.toString() );
  }

  /**
   *
   * @param folder
   * @param checksumMap
   * @param deep
   * @param debug_log if specified, print debug lines there instead of stdout
   * @param debug if true, print more debug logs
   * @param exclude_set don't search files inside it. Set to `null` to ignore
   * @return
   */
  protected static Map< String, String > check(File folder, Map<String, String> checksumMap, Set<Path> exclude_set,
                                               Path debug_log, boolean deep, boolean debug ) {
    Map< String, String > reportContent = new HashMap<>();
    for ( Map.Entry< String, String > entry : new HashMap<>( checksumMap ).entrySet() ) {
      try {
        String signature = getFileSignature( folder.toPath().resolve( Path.of( entry.getKey() ) ) );
        if ( signature.equals( entry.getValue() ) ) {
          checksumMap.remove( entry.getKey() );
          reportContent.put( entry.getKey(), signature );
          log( "Removed from checksum: " + entry.getKey(), debug, debug_log );
        } else {
          log( "Found different signature from checksum: " + entry.getKey(), debug, debug_log );
        }
      } catch ( Exception e ) {
        System.err.println( "Error '" + e.getMessage() + "' with file " + folder.toPath().resolve( Path.of( entry.getKey() ) ).toAbsolutePath() + ", skipping it." );
      }
    }
    // if needed, and we did not find some files, we check if we can find them with the deep search
    try {
      if ( deep && !checksumMap.isEmpty() ) {
        HashSet< String > missingSignatures = new HashSet<>( checksumMap.values() );

        List< Path > files = walkFiles( folder.toPath(), reportContent, exclude_set, debug_log, debug );

        for ( Path f : files ) {
          log( "Checking: " + f.toString(), debug, debug_log );
          try {
            String signature = getFileSignature( f );
            if ( missingSignatures.contains( signature ) ) {
              missingSignatures.remove( signature );
              reportContent.put( folder.toPath().toAbsolutePath().relativize( f ).toString(), signature );
            }
          } catch ( Exception e ) {
            System.err.println( "Error '" + e.getMessage() + "' with file " + f.toAbsolutePath() + ", skipping it." );
          }
          if ( checksumMap.isEmpty() )
            break;
        }
      }

      //			Files.walk( folder.toPath().toAbsolutePath() )
      //							.filter( f -> Files.isRegularFile( f, LinkOption.NOFOLLOW_LINKS ) )
      //							.filter( f -> ! found.contains( f.toString() ) )
      //							.forEach( f -> {
      //								try {
      //									String signature = getFileSignature( f );
      //									if( missingSignatures.contains( signature ) ){
      //										missingSignatures.remove( signature );
      //										found.add( folder.toPath().toAbsolutePath().relativize( f ).toString() + ","  );
      //									}
      //								} catch ( IOException | NoSuchAlgorithmException ignored ) {}
      //							} );
      //		}
      //		}
      //  String reportContentString = reportContent.entrySet().stream()
      //    .map( e -> e.getKey() + "," + e.getValue() )
      //    .collect( Collectors.joining( "\n" ) );
      //  Files.writeString( report.toPath(), reportContentString );

    } catch ( IOException e ) {
      e.printStackTrace();
    }

    return reportContent;
  }

  private static List<Path> walkFiles( Path folder,  Map< String, String > reportContent,
                                      Set<Path> exclude_dirs,
                                       Path debug_log, boolean debug
  ) throws IOException {
    List< Path > files = new LinkedList<>();
    log ("Walking: " + folder.toString(), debug, debug_log);

    try ( DirectoryStream<Path> stream = Files.newDirectoryStream(folder.toAbsolutePath()) ) {
      for (Path file : stream) {

        if ( Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) && !exclude_dirs.contains(file.toAbsolutePath()) ) {
          walkFiles( file, reportContent, exclude_dirs, debug_log, debug );
        } else {
          try {
            if ( Files.isRegularFile( file, LinkOption.NOFOLLOW_LINKS ) && !reportContent.containsKey( file.toString() ) ) {
              if (debug) System.out.println("Adding file: " + file);
              files.add(file);
            }
          } catch ( Exception e ) {
            System.err.println( "Problem processing file: " + file + ", " + e.getMessage() );
          }
        }

      }
    }
    return files;
  }

  private static void log(String message, boolean debug, Path debug_log) {
    if (debug) {
      if (debug_log != null && Files.exists(debug_log)) {
        try {
          Files.writeString( debug_log,
                  message + System.lineSeparator(),
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND
          );
        } catch (IOException e) {
          System.err.println(message);
        }
      } else {
        System.out.println(message);
      }
    }
  }

}
