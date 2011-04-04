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

package com.stainlesscode.mediapipeline.sync;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.Synchronizer;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;

/**
 * This synchronizer uses the system clock as a master clock for timing. Each
 * stream will compare its epts to the result of stream time to see when it
 * should present that media to theuser.
 * 
 * @author Dan Stieglitz
 * 
 */
public class SimpleSynchronizer extends MediaPlayerEventSupport implements
		Synchronizer {

	private static Logger LogUtil = LoggerFactory
			.getLogger(SimpleSynchronizer.class);

	private EngineRuntime engineRuntime;
	private long clapTime;
	private long streamTimeMicroseconds;
	private Thread clockThread;
	private boolean shouldRun = true;
	private long streamTimeZero;
	private boolean streamTimeZeroSet = false;
	private boolean syncReady = true;

	public void init(final EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;

		this.clockThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (shouldRun) {
					if (engineRuntime != null && !engineRuntime.isPaused()) {
						try {
							TimeUnit.MILLISECONDS.sleep(1);

							long offset = System.currentTimeMillis() - clapTime;

							if (LogUtil.isDebugEnabled()) {
								LogUtil.debug("streamTimeZero="
										+ streamTimeZero);
								LogUtil.debug("clapTime=" + clapTime);
								LogUtil.debug("offset=" + offset);
							}

							streamTimeMicroseconds = (long) (streamTimeZero + (offset * 1000)
									* engineRuntime.getPlaySpeed());

							fireMediaPlayerEvent(new MediaPlayerEvent(this,
									MediaPlayerEvent.Type.STREAM_TIME_TICK,
									streamTimeMicroseconds));
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("thread shutting down gracefully");
			}

		});

		this.clockThread.setPriority(Thread.MAX_PRIORITY);
		this.clockThread.start();
	}

	public long getStreamTime() {
		return streamTimeMicroseconds;
	}

	@Override
	public void start() {
		clap();
	}

	@Override
	public void stop() {
		shouldRun = false;
	}

	public void clap() {
		long newClapTime = System.currentTimeMillis();

		// adjust stream zero to new clap time
		if (this.clapTime > 0) {
			long diff = newClapTime - this.clapTime;
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("updating streamTimeZero by " + diff);
			this.streamTimeZero += diff * 1000;
		}

		this.clapTime = newClapTime;
	}

	@Override
	public boolean syncReady() {
		return syncReady;
	}

	public void setStreamTimeZero(long streamTimeZero, boolean reset) {
		LogUtil.info("streamZero set to " + streamTimeZero);
		this.streamTimeZero = streamTimeZero;
		this.streamTimeZeroSet = true;
	}

	/**
	 * Returns the first PTS in the stream, to which streamTime is added as a
	 * offset to get the stream time relative to this value
	 * 
	 * @return
	 */
	public long getStreamTimeZero() {
		return this.streamTimeZero;
	}

	@Override
	public long getAudioWriteLatency() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setAudioWriteLatency(long l) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isStreamTimeZeroSet() {
		return streamTimeZeroSet;
	}

	@Override
	public long getAudioClock() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getFrameTimer() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getVideoClock() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setAudioClock(long audioClock) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setFrameTimer(long frameTimer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setVideoClock(long videoClock) {
		// TODO Auto-generated method stub
		
	}

}
