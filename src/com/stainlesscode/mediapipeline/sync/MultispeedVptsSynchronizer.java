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
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;

/**
 * 
 * @author dstieglitz
 * 
 */
public class MultispeedVptsSynchronizer extends MediaPlayerEventSupport
		implements Synchronizer, MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(MultispeedVptsSynchronizer.class);

	protected long audioWriteLatency;
	protected EngineRuntime engineRuntime;
	protected Thread clockThread;
	protected long streamTimeMicroseconds = -1;
	protected boolean shouldRun = true;
	protected long streamTimeZero;
	protected boolean streamTimeZeroSet;

	public MultispeedVptsSynchronizer() {
		this.clockThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (shouldRun) {
					if (engineRuntime != null && !engineRuntime.isPaused()
							&& streamTimeMicroseconds >= 0) {
						try {
							TimeUnit.MILLISECONDS.sleep(1);
							streamTimeMicroseconds += new Double(
									1000.0 * engineRuntime.getPlaySpeed())
									.longValue();
							if (LogUtil.isDebugEnabled())
								LogUtil.debug("streamTime updated to "
										+ streamTimeMicroseconds);
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
	}

	@Override
	public long getAudioWriteLatency() {
		return this.audioWriteLatency;
	}

	@Override
	public long getStreamTime() {
		return this.streamTimeMicroseconds;
	}

	@Override
	public void init(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
		this.engineRuntime.getEngine().addMediaPlayerEventListener(this);
	}

	@Override
	public void setAudioWriteLatency(long l) {
		this.audioWriteLatency = l;
	}

	public void start() {
		LogUtil.info("Starting synchronizer");

		if (!clockThread.isAlive())
			clockThread.start();
	}

	public void stop() {
		shouldRun = false;
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (evt.getType() == MediaPlayerEvent.Type.SEEK) {
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("updated streamTimeMicroseconds to "
						+ evt.getData());
			this.streamTimeMicroseconds = this.streamTimeZero
					+ ((Long) evt.getData()).longValue();
		}
	}

	public void setStreamTimeZero(long streamTimeZero, boolean reset) {
		LogUtil.info("streamZero set to " + streamTimeZero);
		this.streamTimeZero = streamTimeZero;
		this.streamTimeZeroSet = true;
		if (reset) {
			this.streamTimeMicroseconds = streamTimeZero;
		} else {
			this.streamTimeMicroseconds = streamTimeZero
					+ this.streamTimeMicroseconds;
		}
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

	public boolean isStreamTimeZeroSet() {
		return streamTimeZeroSet;
	}

	public void setStreamTimeZeroSet(boolean streamTimeZeroSet) {
		this.streamTimeZeroSet = streamTimeZeroSet;
	}

	@Override
	public boolean syncReady() {
		return streamTimeZeroSet;
	}

}
