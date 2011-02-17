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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.audioout.AudioDriverException;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.TimeUtil;

public class AudioWriterThread extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(AudioWriterThread.class);

	private EngineRuntime engineRuntime;
	private PortAudioDriver driver;
	private PortAudioBuffer buffer;

	public AudioWriterThread(EngineRuntime engineRuntime,
			PortAudioDriver driver, PortAudioBuffer buffer) {
		this.engineRuntime = engineRuntime;
		this.driver = driver;
		this.buffer = buffer;
	}

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
				if (LogUtil.isTraceEnabled())
					LogUtil.trace("waiting for sync");
				Thread.yield();
			}

			if (buffer.transferReadyFlag) {
				int byteCount = buffer.smoothingBuffer.position();
				byteCount = buffer.transferBuffer(byteCount);

				int frameCount = byteCount
						/ driver.getAudioFormat().getFrameSize();

				if (frameCount <= 0)
					continue;

				long streamOffset = buffer.playStart - streamTime;

				long time = TimeUtil.audioBytesToMillis(
						driver.getAudioFormat(), byteCount);

				if (LogUtil.isDebugEnabled()) {
					LogUtil.debug("About to write " + byteCount + " bytes or "
							+ frameCount + " frames, range: "
							+ buffer.playStart + "-" + buffer.playEnd + " us ("
							+ time + " ms)");

					LogUtil.debug("streamTime is " + streamTime);
					LogUtil.debug("FTIC is " + buffer.firstTimestampInCache);
					// LogUtil.debug("FTIS is " +
					// buffer.firstTimestampInStream);
					LogUtil.debug("buffer time is " + time + "ms");
					LogUtil.debug("VPTS_OFF=" + streamOffset);
				}

				long awlMs = engineRuntime.getSynchronizer()
						.getAudioWriteLatency() / 1000;

				if (buffer.playBuffer.position() > 0) {
					try {
						if (streamOffset > 0) {
							long sleep = (streamOffset / 1000);
							if (LogUtil.isDebugEnabled())
								LogUtil.debug("SLEEP " + sleep + " ms");
							
							if ((sleep-time) > awlMs) {
								System.out.println(sleep-time+"");
								Thread.sleep(sleep-time);
							}
						}

						if (LogUtil.isDebugEnabled())
							LogUtil.debug("$$AUDIO PLAY "
									+ buffer.firstTimestampInCache);

						driver.writeByteBuffer(buffer.playBuffer, frameCount);
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
