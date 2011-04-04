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

import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.VideoOutput;
import com.stainlesscode.mediapipeline.buffer.CircularFifoMediaBuffer;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.xuggle.xuggler.IVideoPicture;

public class DefaultVideoPlayer extends EngineThread implements
		MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultVideoPlayer.class);

	protected EngineRuntime engineRuntime;
	protected Buffer videoFrameBuffer;
	protected VideoOutput videoOutput;
	protected boolean doSync = true;

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
//			if (LogUtil.isDebugEnabled()) {
//				LogUtil.debug("buffer start time="
//						+ ((CircularFifoMediaBuffer) videoFrameBuffer)
//								.getStartTimestamp());
//				LogUtil.debug("buffer end time="
//						+ ((CircularFifoMediaBuffer) videoFrameBuffer)
//								.getEndTimestamp());
//			}

			/*
			 * long vpts = engineRuntime.getSynchronizer().getAudioClock(); long
			 * epts = ((CircularFifoMediaBuffer) videoFrameBuffer)
			 * .getStartTimestampMillis() * 1000l;
			 * 
			 * // long epts = videoOutput.getLastPts();
			 * 
			 * if (epts > vpts) { // try { // TimeUnit.MICROSECONDS.sleep(1000);
			 * // } catch (InterruptedException e) { // // TODO Auto-generated
			 * catch block // e.printStackTrace(); // } continue; }
			 */
			picture = (IVideoPicture) videoFrameBuffer.remove();

			// if (firstTimestampInStream < 0)
			// firstTimestampInStream = picture.getTimeStamp();

			// if (((CircularFifoMediaBuffer) videoFrameBuffer)
			// .getStartTimestampMillis() < 0) {
			// ((CircularFifoMediaBuffer) videoFrameBuffer)
			// .setStartTimestampMillis(picture.getTimeStamp());
			// ((CircularFifoMediaBuffer) videoFrameBuffer)
			// .setEndTimestampMillis(picture.getTimeStamp());
			// }

			if (picture != null) {
				// if (LogUtil.isDebugEnabled())
				// LogUtil.debug("$$VIDEO REMOVE " + picture.getTimeStamp()
				// / 1000);

				// if (framesAreStale(picture)) {
				// if (LogUtil.isDebugEnabled()) {
				// LogUtil.debug("$$VIDEO DISCARD "
				// + picture.getTimeStamp());
				// LogUtil.debug("discarding a stale frame");
				// }
				// returnBorrowed(picture);
				// picture = null;
				// continue;
				// }

				if (picture.isComplete()) {
					if (LogUtil.isDebugEnabled()) {
						// long ts = picture.getTimeStamp();
						// LogUtil.debug("$$VIDEO SET " + ts);
						// LogUtil.debug("ABOUT TO SHOW FRAME "
						// + TimeUtil.getTimecode(engineRuntime
						// .getVideoCoder().getFrameRate()
						// .getValue(), picture.getTimeStamp()));
						// vpts =
						// engineRuntime.getSynchronizer().getStreamTime();
						// LogUtil.debug("epts-vpts= " + ((ts - vpts) / 1000l) +
						// "ms");
					}

					videoOutput.setCurrentFrame(picture.copyReference());

					if (doSync)
						doSync(true);
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
	 * @param sleep
	 */
	protected void doSync(boolean sleep) {
		long vpts = engineRuntime.getSynchronizer().getStreamTime();
		long epts = ((CircularFifoMediaBuffer) videoFrameBuffer)
				.getStartTimestamp();
		long lpts = videoOutput.getLastPts();
		long audioClock = engineRuntime.getSynchronizer().getAudioClock();
		long delay = epts - lpts;
		long diff = epts - audioClock;
		long threshold = 60000;
		long frameTimer = engineRuntime.getSynchronizer().getFrameTimer();

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("vpts=" + vpts);
			LogUtil.debug("epts=" + epts);
			LogUtil.debug("lpts=" + lpts);
			LogUtil.debug("audioClock=" + audioClock);
			LogUtil.debug("vpts-epts=" + (vpts - epts) + "us");
			LogUtil.debug("vpts-audioClock=" + (vpts - audioClock) + "us");
			LogUtil.debug("frameTimer=" + frameTimer + "us");
			LogUtil.debug("epts-lpts=" + delay + "us");
			LogUtil.debug("epts-audioClock (diff)=" + diff + "us");
			LogUtil.debug("threshold=" + threshold + "us");
		}

		if (audioClock > 0) {
			if (diff < -threshold) { // too far behind, catch up
				delay = 0;
			} else if (diff >= threshold) { // too far ahead, hold up frame longer
				delay = diff - threshold;
				LogUtil.debug("delay=" + delay + "us");
				// long sleepTimeMillis = 0;
				// long sleepTimeMillis = ((epts - vpts) / 1000);
				// if (LogUtil.isDebugEnabled())
				// LogUtil.debug("sleeping " + sleepTimeMillis);

				// System.out.println("video seeping "+sleepTimeMillis);
				// double sleepTimeMillisD = (1.0d /
				// engineRuntime.getVideoCoder()
				// .getFrameRate().getValue()) * 1000.0d;
				// long sleepTimeMillis = new
				// Double(sleepTimeMillisD).longValue();
			}
		}

		engineRuntime.getSynchronizer().setFrameTimer(frameTimer + delay);

		// long actualDelay = (long) (frameTimer - (audioClock / 1000l));
		long actualDelay = delay;

		if (LogUtil.isDebugEnabled()) {
//			LogUtil.debug("delay=" + delay + "us");
//			LogUtil.debug("actualDelay=" + actualDelay + "us");
		}

		if (actualDelay > 0) {
			try {
				// if (actualDelay < 0.010) {
				// return;
				// } else {
				TimeUnit.MICROSECONDS.sleep(delay);
				// }
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
