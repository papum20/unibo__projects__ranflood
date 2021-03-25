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

package org.ranflood.daemon.flooders;

import static org.ranflood.daemon.RanFloodDaemon.log;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Emitter;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.ranflood.daemon.RanFloodDaemon;
import org.ranflood.daemon.flooders.tasks.FloodTask;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class FloodTaskExecutor {

	private final HashSet< FloodTask > floodTaskList = new HashSet<>();
	private final ReentrantLock taskListLock = new ReentrantLock();
	static private final FloodTaskExecutor INSTANCE = new FloodTaskExecutor();

	public static FloodTaskExecutor getInstance(){
		return INSTANCE;
	}

	public void addTask( FloodTask t ) {
		taskListLock.lock();
		floodTaskList.add( t );
		taskListLock.unlock();
		launchRecursiveCallable( t );
	}

	private void launchRecursiveCallable( FloodTask t ){
		RanFloodDaemon.executeIORunnable( () -> {
			if ( hasTask( t ) ){
				launchRecursiveCallable( t );
			}
			RanFloodDaemon.executeIORunnable( t.getRunnableTask() );
		});
	}

	public boolean hasTask( FloodTask t ){
		boolean present;
		taskListLock.lock();
		present = floodTaskList.contains( t );
		taskListLock.unlock();
		return present;
	}

	public void removeTask( FloodTask t ){
		taskListLock.lock();
		floodTaskList.remove( t );
		taskListLock.unlock();
	}

	public void shutdown() {
		log( "Shutting down the FloodTaskExecutor" );
//		emitter.onComplete();
//		scheduler.shutdown();
	}

}
