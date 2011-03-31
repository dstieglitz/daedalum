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

package com.stainlesscode.mediapipeline.videoout;

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.VideoOutput;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IVideoPicture;

public class DefaultVideoPlayer extends EngineThread implements
		MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultVideoPlayer.class);

	protected EngineRuntime engineRuntime;
	protected Buffer videoFrameBuffer;
	protected VideoOutput videoOutput;
	protected boolean doSync = true;
	protected long firstTimestampInStream = -1;
	protected long frameDuration = 0;

	public DefaultVideoPlayer(VideoOutput screen, EngineRuntime runtime) {
		this.engineRuntime = runtime;
		this.videoFrameBuffer = runtime.getVideoFrameBuffer();
		this.videoOutput = screen;
	}

	public void run() {
		while (!isMarkedForDeath()) {
			if (engineRuntime.isPaused()) {
				if (LogUtil.isTraceEnabled())
					LogUtil.trace("waiting while paused");
				continue;
			}

			IVideoPicture picture = null;

			if (this.clipEnded && videoFrameBuffer.isEmpty()) {
				setMarkedForDeath(true);
				continue;
			}

			if (videoFrameBuffer.isEmpty() || !syncReady())
				continue;
			
			// sync here... if we are ahead of the vpts just continue the loop
			
			long vpts = engineRuntime.getSynchronizer().getStreamTime();
			long epts = videoOutput.getLastPts();

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("vpts=" + vpts);
				LogUtil.debug("epts=" + epts);
				LogUtil.debug("diff=" + (vpts - epts));
			}

			if (epts + frameDuration > vpts) {
				continue;
			}

			picture = (IVideoPicture) videoFrameBuffer.remove();

			if (firstTimestampInStream < 0)
				firstTimestampInStream = picture.getTimeStamp();

			if (picture != null) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("$$VIDEO REMOVE " + picture.getTimeStamp());

				if (framesAreStale(picture)) {
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("$$VIDEO DISCARD "
								+ picture.getTimeStamp());
						LogUtil.debug("discarding a stale frame");
					}
					returnBorrowed(picture);
					picture = null;
					continue;
				}

				if (picture.isComplete()) {
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("$$VIDEO SET " + picture.getTimeStamp());
						LogUtil.debug("ABOUT TO SHOW FRAME "
								+ TimeUtil.getTimecode(engineRuntime
										.getVideoCoder().getFrameRate()
										.getValue(), picture.getTimeStamp()));
					}

					videoOutput.setCurrentFrame(picture.copyReference());
					
//					if (doSync)
//						doSync(true);
				}
			} else {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("$$VIDEO REMOVE NULL");
			}
		}

		videoFrameBuffer = null;
		LogUtil.info("DefaultVideoPlayThread shutting down gracefully");
	}

	protected void returnBorrowed(IVideoPicture picture) {
		try {
			if (engineRuntime.getEngine().getEngineConfiguration()
					.getConfigurationValueAsBoolean(
							EngineConfiguration.USE_OBJECT_POOLS)) {
				engineRuntime.getRawPicturePool().returnObject(picture);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected boolean framesAreStale(IVideoPicture picture) {
		return (picture.getTimeStamp() < engineRuntime.getSynchronizer()
				.getStreamTime());
	}

	// TODO refactor to AbstractSynchronizedPlayer
	private boolean syncReady() {
		return engineRuntime.getSynchronizer().syncReady();
	}

	/**
	 * @deprecated sync in main loop
	 * @param sleep
	 */
	protected void doSync(boolean sleep) {
		long vpts = engineRuntime.getSynchronizer().getStreamTime();
		long epts = videoOutput.getLastPts();

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("vpts=" + vpts);
			LogUtil.debug("epts=" + epts);
			LogUtil.debug("diff=" + (vpts - epts));
		}

		if (epts > vpts) {
			// long sleepTimeMillis = 0;
			long sleepTimeMillis = ((epts - vpts) / 1000);
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("sleeping " + sleepTimeMillis);

			// System.out.println("video seeping "+sleepTimeMillis);
			// double sleepTimeMillisD = (1.0d / engineRuntime.getVideoCoder()
			// .getFrameRate().getValue()) * 1000.0d;
			// long sleepTimeMillis = new Double(sleepTimeMillisD).longValue();

			try {
				if (sleepTimeMillis > 0)
					Thread.sleep(sleepTimeMillis);
			} catch (InterruptedException e) {
				// seeking will fire an interrupt, and it's ok
				// e.printStackTrace();
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("sleep interrupted");
			}
		}
	}

	public boolean isDoSync() {
		return doSync;
	}

	public void setDoSync(boolean doSync) {
		this.doSync = doSync;
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("got event " + evt);

		if (evt.getType() == MediaPlayerEvent.Type.SEEK) {
			videoFrameBuffer.clear();
		}
	}
}
