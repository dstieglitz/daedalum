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
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IAudioSamples;

/**
 * FOR EXPERIMENTAL USE ONLY
 * @deprecated
 * @author Dan Stieglitz
 * 
 */
public class ExperimentalAudioPlayerWithAudioWriter extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(ExperimentalAudioPlayerWithAudioWriter.class);

	protected EngineRuntime engineRuntime;
	protected AudioOutput driver;
	protected Buffer audioFrameBuffer;
	protected long firstTimestampInStream = -1;
	protected long firstTimestampInCache = -1;
	protected long lastTimestampInCache = -1;

	protected volatile long playStart = -1;
	protected volatile long playEnd = -1;

	protected ByteBuffer smoothingBuffer;
	protected volatile ByteBuffer playBuffer;

	// todo get packet size
	// private int packetSize = -1; // 4608
	// private int bufferSizeInFrames = 131072;
	// private int bufferSizeInFrames = 8192;
	protected int bufferSizeInFrames;
	protected int bufferSizeInBytes;
	protected int bytesPerFrame;
	protected int sampleSizeInBytes = -1;

	protected long streamOffset = -1;

	protected AudioWriter actualWriterThreadRunnable;
	protected Thread actualWriterThread;

	protected boolean transferReadyFlag = false;
	protected IAudioSamples lastSamples = null;

	protected boolean seekFlag = false;

	public ExperimentalAudioPlayerWithAudioWriter(AudioOutput driver, EngineRuntime runtime) {
		this.driver = driver;
		this.engineRuntime = runtime;
		this.audioFrameBuffer = runtime.getAudioFrameBuffer();
		this.bytesPerFrame = driver.getAudioFormat().getFrameSize();
		actualWriterThreadRunnable = new AudioWriter(runtime, driver);
		actualWriterThread = new Thread(actualWriterThreadRunnable,
				"Audio Writer Thread");
		actualWriterThread.setDaemon(true);
		actualWriterThread.start();
	}

	private void allocateBuffers() {
		if (bytesPerFrame > 0) {
			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("bytesPerFrame is " + bytesPerFrame);
				LogUtil.debug("Allocating audio buffers " + bufferSizeInFrames
						+ "*" + bytesPerFrame);
			}
			bufferSizeInBytes = bufferSizeInFrames * bytesPerFrame;
			smoothingBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes);
			playBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes);
			actualWriterThreadRunnable.setPlayBuffer(playBuffer);
		}
	}

	protected int transferBuffer(int byteCount) {
		try {
			if (byteCount > 0) {
				while (playBuffer.remaining() < playBuffer.capacity()) ;
				
				smoothingBuffer.rewind();
				playBuffer.put(smoothingBuffer);

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

	private void cache(IAudioSamples samples) {
		// FIXME BufferOverflows can still happen here... need to
		// do better locking or thread sync

		int bytesToCache = 0;

		try {
			// engineRuntime.getAudioDecodeLock().lockInterruptibly();

			bytesToCache = samples.getSize();

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug(bytesToCache + " bytes to cache");
				LogUtil.debug(smoothingBuffer.remaining()
						+ " bytes remaining smoothingBuffer");
			}

			if (smoothingBuffer.remaining() >= bytesToCache) {
				smoothingBuffer.put(samples.getByteBuffer());
			}

		} catch (java.nio.BufferOverflowException boe) {
			LogUtil.error("Buffer overflow during audio cache operation "
					+ smoothingBuffer.remaining() + "," + bytesToCache);
			// } catch (InterruptedException e) {
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("sleep interrupted during cache");
		} finally {
			if (((ReentrantLock) engineRuntime.getAudioDecodeLock()).isLocked()
					&& ((ReentrantLock) engineRuntime.getAudioDecodeLock())
							.isHeldByCurrentThread()) {
				engineRuntime.getAudioDecodeLock().unlock();
			}
		}

		lastTimestampInCache = samples.getTimeStamp();
	}

	public void run() {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("DefaultAudioPlayer starting...");

		while (!isMarkedForDeath()) {
			if (engineRuntime.isPaused()) {
				// if (LogUtil.isDebugEnabled())
				// LogUtil.debug("DefaultAudioPlayer waiting while paused");
				continue;
			}

			if (isOKToCache()) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("isOKToCache()");

				IAudioSamples samples = lastSamples;

				if (samples == null) {
					// if (audioFrameBuffer.isEmpty()) {
					// if (LogUtil.isDebugEnabled())
					// LogUtil
					// .debug("audio frame buffer seems to be empty");
					// Thread.yield();
					// }
					samples = (IAudioSamples) audioFrameBuffer.remove();
				}

				if (samples == null) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("no audio samples!");
					continue;
				}

				if (playBuffer == null) {
					this.bufferSizeInFrames = (samples.getSize() / this.bytesPerFrame) * 1;
					allocateBuffers();
				}

				if (firstTimestampInStream < 0)
					firstTimestampInStream = samples.getTimeStamp();

				if (firstTimestampInCache < 0)
					firstTimestampInCache = samples.getTimeStamp();

				if (sampleSizeInBytes < 0)
					sampleSizeInBytes = samples.getSize();

				if (LogUtil.isDebugEnabled())
					LogUtil.debug("o---------> processing audio samples at "
							+ samples.getTimeStamp());

				// if these samples are old, discard them
				if (samples.getTimeStamp() < engineRuntime.getSynchronizer()
						.getStreamTime()) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("discarding a stale sample");
					lastSamples = null;
					continue;
				}

				if (smoothingBuffer.remaining() >= samples.getSize()) {
					cache(samples);
					lastSamples = null;
				} else {
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("setting transfer ready flag");
						// LogUtil.debug("seekFlag is "+seekFlag);
					}

					// after a seek, we still have a sleep problem here
					// we need to notify the writer thread that we want
					// to cancel all sleeps immediately after a seek

					// if (seekFlag) {
					// actualWriterThread.interrupt();
					// seekFlag = false;
					// }

					lastSamples = samples;
					transferBuffer(smoothingBuffer.position());
				}
			}
		}

		lastSamples = null;
		audioFrameBuffer = null;
		LogUtil.info("DefaultAudioPlayThread shutting down gracefully");
	}

	private boolean isOKToCache() {
		return !transferReadyFlag
				|| (smoothingBuffer != null && smoothingBuffer.remaining() > 0);
	}

	public void setMarkedForDeath(boolean yesOrNo) {
		super.setMarkedForDeath(yesOrNo);
		actualWriterThreadRunnable.setMarkedForDeath(true);
	}
}
