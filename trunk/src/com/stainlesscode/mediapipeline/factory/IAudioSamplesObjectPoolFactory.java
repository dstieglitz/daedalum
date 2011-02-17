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

package com.stainlesscode.mediapipeline.factory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.pool.BasePoolableObjectFactory;

import com.xuggle.xuggler.IAudioSamples;

public class IAudioSamplesObjectPoolFactory extends BasePoolableObjectFactory {

	private int bufferSize;
	private int channels;
	private Lock nativeLock = new ReentrantLock(false);

	public IAudioSamplesObjectPoolFactory(int bufferSize, int channels) {
		this.bufferSize = bufferSize;
		this.channels = channels;
	}

	@Override
	public Object makeObject() throws Exception {
		nativeLock.lock();
		try {
			return IAudioSamples.make(this.bufferSize, this.channels);
		} finally {
			nativeLock.unlock();
		}
	}

}
