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
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.TimeUtil;

/**
 * FOR EXPERIMENTAL USE ONLY
 * @deprecated for testing
 * @author Dan Stieglitz
 *
 */
public class AudioWriter extends EngineThread implements
		MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory.getLogger(AudioWriter.class);

	private EngineRuntime engineRuntime;
	private volatile ByteBuffer playBuffer;
	private int bytesPerFrame;
	private AudioOutput audioDriver;
	private long tick;

	public AudioWriter(EngineRuntime engineRuntime, AudioOutput driver) {
		this.engineRuntime = engineRuntime;
		this.audioDriver = driver;
		this.bytesPerFrame = driver.getAudioFormat().getFrameSize();
		((MediaPlayerEventSupport) this.engineRuntime.getSynchronizer())
				.addMediaPlayerEventListener(this);
	}

	public void run() {
		while (!isMarkedForDeath()) {
			if (engineRuntime.isPaused()) {
				continue;
			}

			if (playBuffer == null)
				continue;
			//
			// long streamTime;
			//
			// // wait for sync
			// while ((streamTime = engineRuntime.getSynchronizer()
			// .getStreamTime()) < 0) {
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("waiting for sync");
			// Thread.yield();
			// }
			//
			int byteCount = playBuffer.position();

			int frameCount = byteCount / bytesPerFrame;
			//
			if (frameCount <= 0)
				continue;
			//
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("frameCount is " + frameCount);
			//
			long time = TimeUtil.audioBytesToMillis(audioDriver
					.getAudioFormat(), byteCount);
			//
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("buffer time is " + time);
			//
			// long awlMs =
			// engineRuntime.getSynchronizer().getAudioWriteLatency() / 1000;
			//
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("awl is " + awlMs);

			if (byteCount > 0 && tick % time == 0) {
				LogUtil.debug("PLAYING");
				try {
					audioDriver.writeByteBuffer(playBuffer, frameCount);

					// if (LogUtil.isDebugEnabled())
					// LogUtil.debug("sleeping " + time);
					// Thread.sleep(time + awlMs);
					LogUtil.debug("clearing buffer");
					//playBuffer.clear();
				} catch (AudioDriverException e) {
					LogUtil.warn("AUDIO HIT");
				}
				// } catch (InterruptedException e) {
				// if (LogUtil.isDebugEnabled())
				// LogUtil.debug("sleep interrupted");
				// }
			}
		}

		LogUtil.info("AudioWriter shutting down gracefully");
	}

	public ByteBuffer getPlayBuffer() {
		return playBuffer;
	}

	public void setPlayBuffer(ByteBuffer playBuffer) {
		this.playBuffer = playBuffer;
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (evt.getType() == MediaPlayerEvent.Type.STREAM_TIME_TICK) {
			long tickMs = ((Long) evt.getData()).longValue() / 1000;
			// LogUtil.debug("tick " + tickMs);
			this.tick = tickMs;
		}
	}

}
