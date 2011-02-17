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

/**
 * This class provides an API-standardized way to implement safe-stop Threads in 
 * java. Subclasses should check the markedForDeath variable before executing
 * their respective looping code and exit from the run() method if the markedForDeath
 * variable is true
 * @author Dan Stieglitz
 *
 */
public abstract class EngineThread implements Runnable {
	
	protected boolean markedForDeath;
	protected boolean clipEnded;

	public boolean isMarkedForDeath() {
		return markedForDeath;
	}

	public void setMarkedForDeath(boolean markedForDeath) {
		this.markedForDeath = markedForDeath;
	}

	/**
	 * A flag to indicate the demultiplexer has finished reading all
	 * packets, and the downstream objects should stop when their
	 * respective buffers are empty.
	 * @return
	 */
	public boolean isClipEnded() {
		return clipEnded;
	}

	public void setClipEnded(boolean clipEnded) {
		this.clipEnded = clipEnded;
	}

}
