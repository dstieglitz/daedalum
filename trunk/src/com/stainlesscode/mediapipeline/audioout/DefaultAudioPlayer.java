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

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IAudioSamples;

/**
 * @deprecated use classes from the audioout2 package instead
 * @author Dan Stieglitz
 *
 */
public class DefaultAudioPlayer extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultAudioPlayer.class);

	protected EngineRuntime engineRuntime;
	protected AudioOutput driver;
	protected Buffer audioFrameBuffer;
	protected long firstTimestampInStream = -1;
	protected long firstTimestampInCache = -1;
	protected long lastTimestampInCache = -1;

	protected volatile long playStart = -1;
	protected volatile long playEnd = -1;

	protected ByteBuffer smoothingBuffer;
	protected ByteBuffer playBuffer;

	// todo get packet size
	// private int packetSize = -1; // 4608
	// private int bufferSizeInFrames = 131072;
	// private int bufferSizeInFrames = 8192;
	protected int bufferSizeInMilliseconds = 250;
	protected int bufferSizeInFrames;
	protected int bufferSizeInBytes;
	protected int bytesPerFrame;
	protected int sampleSizeInBytes = -1;

	protected long streamOffset = -1;

	protected ActualWriterThread actualWriterThreadRunnable;
	protected Thread actualWriterThread;

	protected boolean transferReadyFlag = false;
	protected IAudioSamples lastSamples = null;

	protected boolean seekFlag = false;

	public DefaultAudioPlayer(AudioOutput driver, EngineRuntime runtime) {
		this.driver = driver;
		this.engineRuntime = runtime;
		this.audioFrameBuffer = runtime.getAudioFrameBuffer();
		this.bytesPerFrame = driver.getAudioFormat().getFrameSize();
		actualWriterThreadRunnable = new ActualWriterThread();
		actualWriterThread = new Thread(actualWriterThreadRunnable,
				"Audio Writer Thread");
		actualWriterThread.setDaemon(true);
		actualWriterThread.start();
		actualWriterThread.setPriority(Thread.MAX_PRIORITY);
	}

	private void allocateBuffers() {
		if (bytesPerFrame > 0) {
			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("bytesPerFrame is " + bytesPerFrame);
				LogUtil.debug("Allocating audio buffers " + bufferSizeInFrames
						+ "*" + bytesPerFrame);
			}
			bufferSizeInBytes = bufferSizeInFrames * bytesPerFrame;
			smoothingBuffer = ByteBuffer.allocate(bufferSizeInBytes);
			playBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes);
		}
	}

	public class ActualWriterThread extends EngineThread {
		public void run() {
			while (!isMarkedForDeath()) {
				if (engineRuntime.isPaused()) {
					continue;
				}

				long streamTime;

				// wait for sync
				while (engineRuntime.getSynchronizer() == null
						|| (streamTime = engineRuntime.getSynchronizer()
								.getStreamTime()) < 0) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("waiting for sync");
					Thread.yield();
				}

				if (transferReadyFlag) {
					int byteCount = smoothingBuffer.position();
					byteCount = transferBuffer(byteCount);

					int frameCount = byteCount / bytesPerFrame;

					if (frameCount <= 0)
						continue;

					streamOffset = playStart - streamTime;

					long time = TimeUtil.audioBytesToMillis(driver
							.getAudioFormat(), byteCount);

					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("About to write " + byteCount
								+ " bytes or " + frameCount
								+ " frames, range: " + playStart + "-"
								+ playEnd + " us (" + time + " ms)");

						LogUtil.debug("streamTime is " + streamTime);
						LogUtil.debug("FTIC is " + firstTimestampInCache);
						LogUtil.debug("FTIS is " + firstTimestampInStream);
						LogUtil.debug("buffer time is " + time + "ms");
						LogUtil.debug("VPTS_OFF=" + streamOffset);
					}

					long awlMs = engineRuntime.getSynchronizer()
							.getAudioWriteLatency() / 1000;

					if (playBuffer.position() > 0) {
						try {
							if (streamOffset > 0) {
								long sleep = (streamOffset / 1000);
								if (LogUtil.isDebugEnabled())
									LogUtil.debug("SLEEP " + sleep + " ms");
								if (sleep > awlMs)
									Thread.sleep(sleep);
							}

							if (LogUtil.isDebugEnabled())
								LogUtil.debug("$$AUDIO PLAY "
										+ firstTimestampInCache);

							driver.writeByteBuffer(playBuffer, frameCount);
						} catch (AudioDriverException e) {
							if (LogUtil.isDebugEnabled()) {
								LogUtil.debug("AUDIO HIT");
							}
						} catch (InterruptedException e) {
							if (LogUtil.isDebugEnabled())
								LogUtil.debug("sleep interrupted");
						}
					}
				}
			}
			LogUtil.info("ActualWriterThread shutting down gracefully");
		}
	}

	protected ByteBuffer stretch(ByteBuffer playBuffer, long sleep) {
		return playBuffer;
	}

	protected int transferBuffer(int byteCount) {
		try {
			if (byteCount > 0) {
				smoothingBuffer.rewind();
				playBuffer.clear();
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
		} finally {
			returnBorrowed(samples);
		}
	}

	protected void returnBorrowed(IAudioSamples samples) {
		try {
			engineRuntime.getAudioSamplePool().returnObject(samples);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
					this.bufferSizeInFrames = TimeUtil.millisToAudioFrames(
							driver.getAudioFormat(),
							this.bufferSizeInMilliseconds);
					allocateBuffers();
				}

				if (firstTimestampInStream < 0)
					firstTimestampInStream = samples.getTimeStamp();

				if (firstTimestampInCache < 0)
					firstTimestampInCache = samples.getTimeStamp();

				if (sampleSizeInBytes < 0)
					sampleSizeInBytes = samples.getSize();

				if (LogUtil.isDebugEnabled()) {
					LogUtil.debug("o---------> processing audio samples at "
							+ samples.getTimeStamp());
				}

				// if these samples are old, discard them
				if (samplesAreStale(samples)) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("discarding a stale sample");
					lastSamples = null;
					continue;
				}

				if (smoothingBuffer.remaining() >= samples.getSize()) {
					cache(samples.copyReference());
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
					transferReadyFlag = true;
				}
			}
		}

		lastSamples = null;
		audioFrameBuffer = null;
		LogUtil.info("DefaultAudioPlayThread shutting down gracefully");
	}

	protected boolean samplesAreStale(IAudioSamples samples) {
		return (samples.getTimeStamp() < engineRuntime.getSynchronizer()
				.getStreamTime());
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
