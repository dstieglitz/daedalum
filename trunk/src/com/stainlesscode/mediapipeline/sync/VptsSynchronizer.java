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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.Synchronizer;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;

/**
 * @deprecated use MultispeedVptsSynchronizer instead
 */
public class VptsSynchronizer extends MediaPlayerEventSupport implements
		Synchronizer {

	private static Logger LogUtil = LoggerFactory
			.getLogger(VptsSynchronizer.class);

	protected long audioWriteLatency;
	protected EngineRuntime engineRuntime;
	protected Thread clockThread;
	protected long streamTimeMicroseconds = -1;
	protected boolean shouldRun = true;

	public VptsSynchronizer() {
		this.clockThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (shouldRun) {
					if (engineRuntime != null && !engineRuntime.isPaused()
							&& streamTimeMicroseconds >= 0) {
						try {
							Thread.sleep(1);
							streamTimeMicroseconds += 1000;
							fireMediaPlayerEvent(new MediaPlayerEvent(this,
									MediaPlayerEvent.Type.STREAM_TIME_TICK,
									streamTimeMicroseconds));
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
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
	}

	@Override
	public void setAudioWriteLatency(long l) {
		this.audioWriteLatency = l;
	}

	public void start() {
		if (this.streamTimeMicroseconds < 0) {
			LogUtil.info("Starting synchronizer");
			this.streamTimeMicroseconds = 0;
			clockThread.start();
		}
	}

	public void stop() {
		shouldRun = false;
	}

	@Override
	public boolean syncReady() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isStreamTimeZeroSet() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setStreamTimeZero(long timeStamp, boolean b) {
		// TODO Auto-generated method stub
		
	}

}
