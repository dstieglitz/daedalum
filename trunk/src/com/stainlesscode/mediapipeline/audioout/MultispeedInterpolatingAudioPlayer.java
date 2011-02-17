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

package com.stainlesscode.mediapipeline.audioout;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.util.TimeUtil;

/**
 * @deprecated use classes from the audioout2 package instead
 * @author Dan Stieglitz
 * 
 */
public class MultispeedInterpolatingAudioPlayer extends DefaultAudioPlayer
		implements MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(MultispeedInterpolatingAudioPlayer.class);

//	private volatile ByteBuffer stretched = ByteBuffer
//			.allocateDirect(1024 * 1000);

	public MultispeedInterpolatingAudioPlayer(AudioOutput driver,
			EngineRuntime runtime) {
		super(driver, runtime);
		runtime.getEngine().addMediaPlayerEventListener(this);
	}

	/**
	 * This method is modified to interpolate the play buffer into containing
	 * the appropriate audio data for the requested play speed. For example, if
	 * the play speed is 2.0, the play buffer should contain two
	 * smoothingBuffers worth of data, compressed with some interpolation (in
	 * this case by dropping every nth sample).
	 * 
	 * Returns the actual number of bytes transferred (for example, if
	 * compression occurred).
	 * 
	 * @param byteCount
	 */
	@Override
	protected int transferBuffer(int byteCount) {
		try {
			if (byteCount > 0) {
				playBuffer.clear();

				if (engineRuntime.getPlaySpeed() > 1.0) {
					ByteBuffer compressedBuffer = compress(smoothingBuffer,
							new Double(engineRuntime.getPlaySpeed()).intValue());

					if (LogUtil.isDebugEnabled())
						LogUtil.debug("about to store "
								+ compressedBuffer.position()
								+ " into playbuffer");

					compressedBuffer.rewind();
					playBuffer.put(compressedBuffer);
					byteCount = compressedBuffer.position();
				} else {
					smoothingBuffer.rewind();
					playBuffer.put(smoothingBuffer);
				}

				long sampleTimeMicroseconds = TimeUtil.audioBytesToMillis(
						driver.getAudioFormat(), sampleSizeInBytes) * 1000;

				// ready to startCaching
				playStart = firstTimestampInCache;
				playEnd = lastTimestampInCache + sampleTimeMicroseconds;

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

	/**
	 * Compress the specified byte buffer by dropping every nth frame as
	 * specified by the factor parameter.
	 * 
	 * @param source
	 * @param factor
	 * @return
	 */
	private ByteBuffer compress(ByteBuffer source, int factor) {
		int sourceSize = source.position();

		// how many frames in the buffer?
		int frameCount = sourceSize / bytesPerFrame;

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("frameCount= " + frameCount);
			LogUtil.debug("factor= " + factor);
			LogUtil.debug("frameSize = " + bytesPerFrame);
		}

		int newSize = (int) (frameCount / factor) * bytesPerFrame;

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("newSize is " + newSize);
		}

		ByteBuffer compressed = ByteBuffer.allocate(newSize);

		if (newSize > 0) {
			source.rewind();

			// drop every factor-th frame
			for (int i = 0; i < sourceSize; i += bytesPerFrame) {
				if (i / bytesPerFrame % factor == 0) {
					for (int j = i; j < (i + bytesPerFrame); j++) {
						compressed.put(source.get(j));
					}
				}
			}
		}

		return compressed;
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (evt.getType() == MediaPlayerEvent.Type.SEEK) {
			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("about to interrupt the actual writer thread");
				LogUtil
						.debug("*---------------------------------------------> SEEK "
								+ evt.getData());
				playBuffer.clear();
				smoothingBuffer.clear();
			}
			// seekFlag = true;
			actualWriterThread.interrupt();
		}
	}
}
