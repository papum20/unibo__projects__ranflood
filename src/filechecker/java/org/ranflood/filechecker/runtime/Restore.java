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

package org.ranflood.filechecker.runtime;

import com.republicate.json.Json;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ranflood.common.RanfloodLogger;
import org.ranflood.common.utils.Pair;
import org.sssfile.SSSRestorer;
import org.sssfile.exceptions.InvalidOriginalFileException;
import org.sssfile.exceptions.UnrecoverableOriginalFileException;
import org.sssfile.files.FileNamesGenerator;
import org.sssfile.files.OriginalFile;
import org.sssfile.util.LoggerResult;

import static org.ranflood.filechecker.runtime.Check.check;


public class Restore {

  public static void run( File checksum, File folder, File report_shards, File report_restored,
                          File[] exclude_dirs,
                          Boolean remove_shards, Boolean dry_run, String windows_mount, String windows_letter,
                          File log, Boolean debug
  ) throws IOException {

    /* check params */
    if ( !Files.exists( checksum.toPath().toAbsolutePath().getParent() ) )
      throw new IOException( "could not find checksum file " + checksum.toPath() );
    if ( !Files.exists( folder.toPath() ) )
      throw new IOException( "folder " + folder + " does not exist" );
    if ( !Files.isDirectory( folder.toPath() ) )
      throw new IOException( folder + " is not a directory" );

    if (exclude_dirs == null) exclude_dirs = new File[0];
    Set<Path> exclude_set = Arrays.stream(exclude_dirs)
            .map(dir -> Path.of(dir.getAbsolutePath()) )
            .collect(Collectors.toSet());

    Json checksum_json = Json.parse( Files.readString( checksum.toPath() ) );
    Map< String, String > checksum_filtered_map = checksum_json.asArray().
        stream()
        .map( e -> ( Json.Object ) e )
        .filter( f -> !isFileIn(
                exclude_set,
                folder.toPath().resolve( Path.of(f.get( "path" ).toString()) )
        ))
        .collect( Collectors.toMap(
            e -> e.get( "path" ).toString(),
            e -> e.get( "checksum" ).toString() )
        );


    /* run sss search */
    Path file_log = ( log != null ) ? log.toPath() : null;
    SSSRestorer sss = new SSSRestorer( folder.toPath(), file_log, file_log != null, debug );

    if ( Files.exists(report_shards.toPath().toAbsolutePath()) ) {
      Json report_shards_json = Json.parse( Files.readString( report_shards.toPath() ) );
      sss.loadShardsReportJson(report_shards_json);
    } else {
      sss.findShards(exclude_set);
      Json.Array shards_json = sss.getShardsReportJson();
      Files.writeString( report_shards.toPath(), shards_json.toString() );
    }

    LoggerResult stats = sss.getStats();


    /* write original files */
    LinkedList< Pair< Path, Path > >  recovered_path_conflict   = new LinkedList<>(), // old/new path
                                      recovered_wrong_snapshot  = new LinkedList<>();
    // map of path - signature
    LinkedHashMap< Path, String >
                        // paths of files already existing, with correct signature (any related shards will thus be ignored)
                        recovered_already_exist   = new LinkedHashMap<>(),
                        // paths of all files present in the checksum, still existing and which didn't need any recover
                        files_already_exist       = new LinkedHashMap<>(),
                        recovered                 = new LinkedHashMap<>(),
                        // paths of recovered files which weren't saved in the checksum (also including recovered_wrong_snapshot)
                        recovered_new             = new LinkedHashMap<>(),
                        error_io                  = new LinkedHashMap<>(),
                        error_delete_shard        = new LinkedHashMap<>();
    int shards_tot = 0,
        original_files_error_get = 0;
    int iterator_percentage = 0;

    Pair< OriginalFile, byte[] > original_file;
    while ( true ) {
      try {
        original_file = sss.iterateOriginalFile();  // also checks with original hash (saved in shards)
      } catch ( InvalidOriginalFileException | UnrecoverableOriginalFileException e ) {
        // errors: logged in LoggerRestore
        RanfloodLogger.error( e.getMessage() );
        continue;
      } catch ( NoSuchAlgorithmException e ) {
        RanfloodLogger.error( e.getMessage() );
        original_files_error_get++;
        continue;
      }
      if ( original_file == null )
        break;

      // display completion percentage
      if ( sss.getIteratorPercentage() > iterator_percentage ) {
        iterator_percentage = sss.getIteratorPercentage();
        System.out.println( "Writing original files: " + iterator_percentage + "%" );
      }

      Path file_path = original_file.left().path;
      Path file_path_converted = convert(file_path, windows_mount != null, windows_mount);
      Path file_path_relativized = folder.toPath().relativize(file_path_converted);
      String signature_checksum = checksum_filtered_map.get( file_path_relativized.toString() );
      String signature_computed = null;

      // if a file with the same name already exists: if it has the same checksum skip, otherwise write with a new name
      boolean file_exists = Files.exists( file_path );
      if ( file_exists ) {
        try {
          signature_computed = Utils.getFileSignature( file_path );
        } catch ( NoSuchAlgorithmException | Utils.OutOfMemoryException e ) {
          System.err.println( "Error computing the signature of file " + file_path + " : " + e.getMessage() );
        }
      }

      String signature_header = original_file.left().getHashBase64();
      System.out.println( "Writing " + file_path + " (relativized: " + file_path_relativized
              + " " + file_path_converted
        + "); file exists: " + file_exists
        + "; signature from snapshot: " + signature_checksum + "; signature found in file header: " + signature_header
        + "; signature computed: " + signature_computed
      );

      if ( signature_computed == null || !signature_computed.equals( signature_header ) ) {

        /*	if snapshot doesn't contain the checksum, just continue writing the file (shards contain original hash);
            if snapshot doesn't match with checksum, use another name (since we already checked with shard's hash,
            snapshot was not up-to-date)
         */
        try {
          // change name if path or snapshot conflict
          if ((signature_checksum != null && !signature_checksum.equals(signature_header))) {
            file_path = FileNamesGenerator.getUniquePath(file_path_converted.toString());  // also avoid other name conflicts for already existing files
            if (!dry_run)
              Files.write(file_path, original_file.right());

            // register for report after writing, so that we only register an error in case an exception occurs
            recovered_wrong_snapshot.add(new Pair<>(original_file.left().path, file_path));
            recovered_new.put(file_path, signature_header);
            System.out.println("File exists with wrong snapshot, written to " + file_path);
          } else if (file_exists) {
            file_path = FileNamesGenerator.getUniquePath(file_path.toString());
            if (!dry_run)
              Files.write(file_path, original_file.right());

            recovered_path_conflict.add(new Pair<>(original_file.left().path, file_path));
            recovered.put(file_path, signature_header);
            System.out.println("Conflict, written to " + file_path);
          } else {
            if (!dry_run)
              Files.write(file_path, original_file.right());

            recovered.put(file_path, signature_header);
            System.out.println("Written to " + file_path);
          }
        } catch (IOException e) {
          error_io.put(original_file.left().path, signature_header);
          System.err.println("IO Error with file " + file_path + " : " + e.getMessage());
          // don't delete shards if original file is missing and couldn't be written
          continue;
        }
      } else {
        // don't write only if file with same checksum was found
        recovered_already_exist.put(file_path, signature_checksum);
      }


      if ( remove_shards ) {
        for ( Path shard_path : original_file.left().getShardsPaths() ) {
          shards_tot++;
          try {
            Files.delete( shard_path );
            sss.logDelete(shard_path, file_path, true);
          } catch ( IOException e ) {
            error_delete_shard.put( shard_path, signature_header );
          }
        }
      }
    }

    sss.logRestoreCompleted();

    /*  complement recovered_already_exist with those files present in the checksum and which are still there
        (although weren't restored)
    */
    LinkedHashMap<String, Path> recovered_inverted = new LinkedHashMap<>();
    for (Map.Entry<Path, String> file : recovered.entrySet()) {
      recovered_inverted.put(file.getValue(), file.getKey());
    }

    for (Map.Entry<String, String> entry : checksum_filtered_map.entrySet()) {
      Path path = Path.of(entry.getKey());
      String signature_computed = null;
      if ( Files.exists(path) ) {
        try {
          signature_computed = Utils.getFileSignature(path);
        } catch ( NoSuchAlgorithmException | Utils.OutOfMemoryException e ) {
          System.err.println( e.getMessage() );
        }

        if ( signature_computed != null && signature_computed.equals(entry.getValue())
                && !recovered_inverted.containsKey(signature_computed)
        ) {
          files_already_exist.put(path, signature_computed);
        }
      }
    }


    /* check all files from checksum */
    Map< String, String > report_check_content = check( folder, new HashMap<>(checksum_filtered_map), exclude_set, file_log, true, true );


    /* collect and report logs */

    // maps
    Json.Array json_files_recovered = new Json.Array();
    recovered.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key.toString() );
      o.put( "checksum", value );
      json_files_recovered.add( o );
    } );
    Json.Array json_files_recovered_new = new Json.Array();
    recovered_new.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key.toString() );
      o.put( "checksum", value );
      json_files_recovered_new.add( o );
    } );
    Json.Array json_recovered_already_exist = new Json.Array();
    recovered_already_exist.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key.toString() );
      o.put( "checksum", value );
      json_recovered_already_exist.add( o );
    } );
    Json.Array json_already_exist = new Json.Array();
    files_already_exist.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key.toString() );
      o.put( "checksum", value );
      json_already_exist.add( o );
    } );

    // lists
    Json.Object json_recovered_path_conflict = new Json.Object();
    recovered_path_conflict.forEach( ( file_info ) ->
        json_recovered_path_conflict.put( file_info.right().toString(), "Original path was: " + file_info.right() )
    );
    Json.Object json_recovered_wrong_snapshot = new Json.Object();
    recovered_wrong_snapshot.forEach( ( file_info ) ->
        json_recovered_wrong_snapshot.put( file_info.right().toString(), "Original path was: " + file_info.right() )
    );

    // check
    Json.Array json_checksum_filtered = new Json.Array();
    checksum_filtered_map.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key );
      o.put( "checksum", value );
      json_checksum_filtered.add( o );
    } );

    Json.Array json_report_check = new Json.Array();
    report_check_content.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key );
      o.put( "checksum", value );
      json_report_check.add( o );
    } );

    // errors
    Json.Object json_files_error_checksum = new Json.Object();
    stats.files_error_checksum.forEach( ( file_info ) ->
        json_files_error_checksum.put( file_info.getAbsolutePath(), file_info.getInfo() )
    );
    Json.Array json_files_error_io = new Json.Array();
    error_io.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key.toString() );
      o.put( "checksum", value );
      json_files_error_io.add( o );
    } );
    Json.Array json_shards_error_delete = new Json.Array();
    error_delete_shard.forEach( ( key, value ) -> {
      Json.Object o = new Json.Object();
      o.put( "path", key.toString() );
      o.put( "checksum", value );
      json_shards_error_delete.add( o );
    } );

    // stats
    Json.Object json_files_error_insufficient = new Json.Object();
    stats.files_error_insufficient.forEach( ( file_info ) ->
        json_files_error_insufficient.put( file_info.getAbsolutePath(), file_info.getInfo() )
    );
    Json.Object json_files_error_other = new Json.Object();
    stats.files_error_other.forEach( ( file_info ) ->
        json_files_error_other.put( file_info.getAbsolutePath(), file_info.getInfo() )
    );
    Json.Object json_stats = new Json.Object();
    json_stats.put( "Shards total", shards_tot );
    json_stats.put( "Shards deleted", shards_tot - error_delete_shard.size() );
    json_stats.put( "Restored files not written for other errors", original_files_error_get );

    Json.Object report_content = new Json.Object();
    report_content.put( reportFilesKey("Tot: saved in checksum (filtered)",                                                                           checksum_filtered_map.size()),          json_checksum_filtered );
    report_content.put( reportFilesKey("Tot: saved in checksum and still present, with correct signature",                                            report_check_content.size()),           json_report_check );
    //report_content.put( reportFilesKey("Total valid files (including recovered and those not corrupted)",                                                  report_check_content.size()),           json_report_check );
    report_content.put( reportFilesKey("Recovered: present in checksum and file not existing, but now recovered (including path conflict)",           recovered.size()),                      json_files_recovered );
    report_content.put( reportFilesKey("Recovered, path conflict: recovered but changed name because a different file with the same name was found",  recovered_path_conflict),               json_recovered_path_conflict );
    report_content.put( reportFilesKey("Recovered, wrong checksum: recovered but changed name because snapshot has a different checksum",             recovered_wrong_snapshot),              json_recovered_wrong_snapshot );
    report_content.put( reportFilesKey("Recovered: already existing, with correct signature",                                                         recovered_already_exist.size()),        json_recovered_already_exist );
    report_content.put( reportFilesKey("Recovered: new files, not present in the checksum (including wrong checksum)",                                recovered_new.size()),                  json_files_recovered_new );
    report_content.put( reportFilesKey("Already existing, with correct signature (including recovered but already existing)",                         files_already_exist.size()),            json_already_exist );
    report_content.put( reportFilesKey("Couldn't write these files, retry.",                                                                          error_io.size()),                       json_files_error_io );
    report_content.put( reportFilesKey("Couldn't delete these shards, but they can be removed safely as they were already recovered",                 error_delete_shard.size()),             json_shards_error_delete );
    report_content.put( reportFilesKey("Error: checksum (scan)",                                                                                      stats.files_error_checksum),            json_files_error_checksum );
    report_content.put( reportFilesKey("Error: insufficient shards (scan)",                                                                           stats.files_error_insufficient),        json_files_error_insufficient );
    report_content.put( reportFilesKey("Error: other (scan)",                                                                                         stats.files_error_other),               json_files_error_other );
    report_content.put( "Stats (scan)",                                                                                                                                                            json_stats );

    Files.writeString( report_restored.toPath(), report_content.toString() );

  }


  /**
   *
   * @param path absolute path in windows
   * @param is_windows whether `path` is in windows
   * @param windows_mount where windows partition is mounted on linux
   * @return
   */
  private static Path convert(Path path, Boolean is_windows, String windows_mount) {

    if (is_windows) {
      return Path.of(
              path.toString()
                      .replaceFirst("[A-Z]:", windows_mount)
                      .replaceAll(Pattern.quote("\\"), "/")
      ).toAbsolutePath();
    } else {
      return path;
    }
  }


  private static String reportFilesKey(String key, int files) {
    return key + " (" + files + ")";
  }
  private static <T> String reportFilesKey(String key, Collection<T> files) {
    return reportFilesKey(key, files.size());
  }


  private static boolean isFileIn(Collection<Path> files, Path file_absolute) {
    String filePath = file_absolute.toAbsolutePath().toString();

    for (Path f : files) {
      //System.out.println("isFileIn: " + file_absolute + " in... " + f + ": " + filePath.startsWith(f.toAbsolutePath().toString()) );
      if ( filePath.startsWith(f.toAbsolutePath().toString()) )
        return true;
    }
    return false;
  }


}
