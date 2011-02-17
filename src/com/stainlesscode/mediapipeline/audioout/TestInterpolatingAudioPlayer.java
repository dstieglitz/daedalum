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

import javax.sound.sampled.AudioFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IRational;

/**
 * FOR EXPERIMENTAL USE ONLY
 * 
 * @deprecated
 * @author Dan Stieglitz
 * 
 */
public class TestInterpolatingAudioPlayer extends TestAudioPlayer implements
		MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(TestInterpolatingAudioPlayer.class);

	public TestInterpolatingAudioPlayer(AudioOutput driver,
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
				while (playBuffer.remaining() == 0)
					;

				if (engineRuntime.getPlaySpeed() > 1.0) {
					ByteBuffer compressedBuffer = compress(smoothingBuffer,
							new Double(engineRuntime.getPlaySpeed()).intValue());

					if (LogUtil.isDebugEnabled())
						LogUtil.debug("about to store "
								+ compressedBuffer.position()
								+ " into playbuffer");

					compressedBuffer.rewind();
					for (int i = 0; i < byteCount; i++)
						playBuffer.put(compressedBuffer.get(i));
					byteCount = compressedBuffer.position();
				} else {
					smoothingBuffer.rewind();
					for (int i = 0; i < byteCount; i++)
						playBuffer.put(smoothingBuffer.get(i));
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
	protected ByteBuffer compress(ByteBuffer source, int factor) {
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

	protected ByteBuffer compress(ByteBuffer source, long millis) {
		int sourceSize = source.position();
		AudioFormat format = this.driver.getAudioFormat();

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("source size is " + sourceSize);
			LogUtil.debug("compress to " + millis + " ms");
		}

		int newSize = TimeUtil.millisToAudioFrames(format, millis)
				* bytesPerFrame;

		ByteBuffer compressed = ByteBuffer.allocate(newSize);

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("original buffer is "
					+ TimeUtil.audioBytesToMillis(driver.getAudioFormat(),
							sourceSize) + " ms");
			LogUtil.debug("newSize is " + newSize);
			LogUtil.debug("new total length is "
					+ TimeUtil.audioBytesToMillis(driver.getAudioFormat(),
							newSize) + " ms");
		}

		source.rewind();

		double r = (double) sourceSize / (double) newSize;
		int factor = (int) r;

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("remove every " + factor + "th frame");
		}

		if (factor <= 1)
			return null;

		if (newSize > 0) {
			source.rewind();

			// drop every factor-th frame
			for (int i = 0; i < sourceSize; i += bytesPerFrame) {
				if ((i / bytesPerFrame) % factor == 0) {
					for (int j = i; j < (i + bytesPerFrame); j++) {
						compressed.put(source.get(j));
					}
				}
			}
		}

		compressed.rewind();
		return compressed;
	}

	/**
	 * Stretch the source buffer BY 'millis' milliseconds
	 */
	protected ByteBuffer stretch(ByteBuffer source, long millis) {
		int sourceSize = source.position();
		AudioFormat format = this.driver.getAudioFormat();

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("source size is " + sourceSize);
			LogUtil.debug("stretch by " + millis + " ms");
		}

		int bytesToAdd = TimeUtil.millisToAudioFrames(format, millis)
				* bytesPerFrame;
		int newSize = sourceSize + bytesToAdd;

		ByteBuffer stretched = ByteBuffer.allocate(newSize);

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("original buffer is "
					+ TimeUtil.audioBytesToMillis(driver.getAudioFormat(),
							sourceSize) + " ms");
			LogUtil.debug("adding " + bytesToAdd + " bytes");
			LogUtil.debug("newSize is " + newSize);
			LogUtil.debug("new total length is "
					+ TimeUtil.audioBytesToMillis(driver.getAudioFormat(),
							newSize) + " ms");
		}

		int extraFramesAdded = 0;

		if (newSize > 0 && newSize > sourceSize) {
			double factor = (double) newSize / (double) sourceSize;
			IRational rat = IRational.make();
			IRational.sReduce(rat, sourceSize, bytesToAdd, 10);
			int dupEvery = (int) factor;

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("factor is " + rat.getNumerator() + "/"
						+ rat.getDenominator());
			}

			if (dupEvery == 0) {
				return null;
			}

			source.rewind();

			int count = 0;
			// for each frame in the new buffer...
			for (int i = 0; i < sourceSize; i += bytesPerFrame) {
				// LogUtil.debug("processing frame "+frameNumber);
				if (stretched.remaining() == 0
						|| i > source.capacity() - bytesPerFrame)
					break;

				int pos = i;

				for (int j = pos; j < (pos + bytesPerFrame); j++) {
					if (stretched.remaining() == 0) {
						System.out.println("at " + j + "th byte, an overflow");
					}
					stretched.put(source.get(j));
				}

				// if we're at the next nth frame
				if (count++ == rat.getDenominator()) {
					// LogUtil.debug("duplicating some frames");
					// duplicate the last frame
					for (int z = 0; z < rat.getNumerator(); z++) {
						// LogUtil.debug("adding a frame ("+z+")");
						for (int j = pos; j < (pos + bytesPerFrame); j++) {
							if (stretched.remaining() == 0) {
								System.out.println("at " + j
										+ "th byte, an overflow");
								break;
							}
							stretched.put(source.get(j));
						}
						extraFramesAdded++;
					}
					count = 0;
				}
			}
		}

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("stretched position after stretch is "
					+ stretched.position());
			LogUtil.debug("added " + extraFramesAdded + " frames");
			LogUtil.debug("missing "+(newSize - stretched.position())+" bytes");
		}

		stretched.rewind();
		return stretched;
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
