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

package playground;

import org.daemon.RanFloodDaemon;
import org.daemon.flooders.TaskNotFoundException;
import org.daemon.flooders.random.RandomFlooder;

import java.nio.file.Path;
import java.util.UUID;

public class TestTaskExecutor {

	public static void main( String[] args ) {
//		FileTaskExecutor te = FileTaskExecutor.getInstance();
//		WriteFileTask w = new WriteFileTask(
//						Path.of( "'/users/thesave/Desktop/attackedFolder'"),
//						new byte[1024],
//						FloodMethod.RANDOM,
//						UUID.randomUUID()
//		);
//		IntStream.range( 0, 2 ).forEach( ( i ) -> {
//			System.out.println( "Adding the task in 2 seconds and stopping after 100ms" );
//			try {
//				Thread.sleep( 2000 );
//			} catch ( InterruptedException e ) {
//				e.printStackTrace();
//			}
//			te.addTask( w );
//			try {
//				Thread.sleep( 100 );
//			} catch ( InterruptedException e ) {
//				e.printStackTrace();
//			}
//		} );
//		te.shutdown();
		UUID id = RandomFlooder.flood( Path.of( "/users/thesave/Desktop/attackedFolder" ) );
		try {
			Thread.sleep( 100 );
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
//		System.out.println("Running flood");
//		try {
//			RandomFlooder.stopFlood( id );
//		} catch ( TaskNotFoundException e ) {
//			e.printStackTrace();
//		}
		RanFloodDaemon.shutdown();
	}



}


