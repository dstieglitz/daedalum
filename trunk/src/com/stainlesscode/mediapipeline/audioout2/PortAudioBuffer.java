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

package com.stainlesscode.mediapipeline.audioout2;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.xuggler.IAudioSamples;

public class PortAudioBuffer {

	private static Logger LogUtil = LoggerFactory
			.getLogger(PortAudioBuffer.class);

	ByteBuffer smoothingBuffer;
	ByteBuffer playBuffer;

	volatile long playStart = -1;
	volatile long playEnd = -1;
	volatile long firstTimestampInCache = -1;
	volatile long lastTimestampInCache = -1;
	volatile long firstTimestampInStream = -1;
	
	volatile boolean transferReadyFlag = false;

	public PortAudioBuffer(int bufferSizeInBytes) {
		allocateBuffers(bufferSizeInBytes);
	}

	private void allocateBuffers(int size) {
		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("Allocating audio buffers " + size);
		}
		smoothingBuffer = ByteBuffer.allocate(size);
		playBuffer = ByteBuffer.allocateDirect(size);
	}

	protected int transferBuffer(int byteCount) {
		try {
			if (byteCount > 0) {
				smoothingBuffer.rewind();
				playBuffer.clear();
				playBuffer.put(smoothingBuffer);

				// long sampleTimeMicroseconds = TimeUtil.audioBytesToMillis(
				// driver.getAudioFormat(), sampleSizeInBytes) * 1000;

				// ready to startCaching
				playStart = firstTimestampInCache;
				playEnd = lastTimestampInCache;// + sampleTimeMicroseconds;

				if (LogUtil.isDebugEnabled())
					LogUtil.debug("transferred " + byteCount
							+ " bytes into play buffer");
			}
		} finally {
			smoothingBuffer.clear();
			firstTimestampInCache = -1;
			transferReadyFlag = false;
		}

		return byteCount;
	}

	public boolean isOKToCache(int sampleCount) {
		return (smoothingBuffer!=null && smoothingBuffer.remaining() >= sampleCount);
	}

	public void cache(IAudioSamples samples) {
		// FIXME BufferOverflows can still happen here... need to
		// do better locking or thread sync
		
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("caching "+samples.getTimeStamp());

		if (firstTimestampInStream < 0)
			firstTimestampInStream = samples.getTimeStamp();

		if (firstTimestampInCache < 0)
			firstTimestampInCache = samples.getTimeStamp();

		try {
			int bytesToCache = samples.getByteBuffer().remaining();

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug(bytesToCache + " bytes to cache");
				LogUtil.debug(smoothingBuffer.remaining()
						+ " bytes remaining smoothingBuffer");
			}

			if (smoothingBuffer.remaining() >= bytesToCache) {
				try {
					smoothingBuffer.put(samples.getDataCached().getByteBuffer(
							0, bytesToCache));
				} catch (Throwable t) {
					return;
				}
			}

			lastTimestampInCache = samples.getTimeStamp();
		} catch (java.nio.BufferOverflowException boe) {
			LogUtil.error("Buffer overflow during audio cache operation rem:"
					+ smoothingBuffer.remaining() + ", size:"
					+ samples.getByteBuffer().remaining() + ", lim:"
					+ smoothingBuffer.limit());
		}
	}
}
