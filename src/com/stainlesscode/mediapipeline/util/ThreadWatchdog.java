/*
 * Copyright 2010-2011 Stainless Code
 *
 *  This file is part of Daedalum.
 *
 *  Daedalum is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Daedalum is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Daedalum.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.stainlesscode.mediapipeline.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadWatchdog extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(ThreadWatchdog.class);

	public ThreadWatchdog() {

	}

	/*
	 * Some thread debug utility methods
	 */
	public Thread[] getGroupThreads(final ThreadGroup group) {
		if (group == null)
			throw new NullPointerException("Null thread group");
		int nAlloc = group.activeCount();
		int n = 0;
		Thread[] threads;
		do {
			nAlloc *= 2;
			threads = new Thread[nAlloc];
			n = group.enumerate(threads);
		} while (n == nAlloc);
		return java.util.Arrays.copyOf(threads, n);
	}

	private ThreadGroup getRootThreadGroup() {
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		ThreadGroup ptg;
		while ((ptg = tg.getParent()) != null)
			tg = ptg;
		return tg;
	}

	public Thread[] getAllThreads() {
		final ThreadGroup root = getRootThreadGroup();
		final ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
		int nAlloc = thbean.getThreadCount();
		int n = 0;
		Thread[] threads;
		do {
			nAlloc *= 2;
			threads = new Thread[nAlloc];
			n = root.enumerate(threads, true);
		} while (n == nAlloc);
		return java.util.Arrays.copyOf(threads, n);
	}

	private void checkThreads() {
		String result = "";
		Thread[] threads = getAllThreads();
		for (int i = 0; i < threads.length; i++) {
			Thread thread = threads[i];
			if (thread.getName().equals("Audio Play Thread"))
				result += " APT";
			if (thread.getName().equals("Video Play Thread"))
				result += " VPT";
			if (thread.getName().equals("Demultiplexer Thread"))
				result += " DT";
			if (thread.getName().equals("Audio Decode Thread"))
				result += " ADT";
			if (thread.getName().equals("Video Decode Thread"))
				result += " VDT";
		}
		if (result.length() != 19) {
			System.err.println("THREAD DEATH DETECTED. STILL ALIVE ARE:" + result);
		}
	}

	@Override
	public void run() {
		while (!isMarkedForDeath()) {
			long time = System.currentTimeMillis();
			if (time % 1000 == 0) {
				checkThreads();
				try {
					Thread.sleep(700);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

}
