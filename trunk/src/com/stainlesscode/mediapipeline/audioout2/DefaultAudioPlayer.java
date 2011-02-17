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

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput2;
import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.xuggle.xuggler.IAudioSamples;

public class DefaultAudioPlayer extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultAudioPlayer.class);

	protected EngineRuntime engineRuntime;
	protected Buffer audioFrameBuffer;
	protected AudioOutput2 audioOutput;

	public DefaultAudioPlayer(AudioOutput2 output, EngineRuntime runtime) {
		this.engineRuntime = runtime;
		this.audioFrameBuffer = runtime.getAudioFrameBuffer();
		this.audioOutput = output;
	}

	public void run() {
		while (!isMarkedForDeath()) {
			if (engineRuntime.isPaused()) {
				if (LogUtil.isTraceEnabled())
					LogUtil.trace("waiting while paused");
				continue;
			}

			IAudioSamples samples = null;

			if (this.clipEnded && audioFrameBuffer.isEmpty()) {
				setMarkedForDeath(true);
				continue;
			}
			
			if (audioFrameBuffer.isEmpty() || !syncReady())
				continue;

			samples = (IAudioSamples) audioFrameBuffer.remove();

			if (samples != null) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("$$AUDIO REMOVE " + samples.getTimeStamp());

				if (framesAreStale(samples)) {
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("$$AUDIO DISCARD "
								+ samples.getTimeStamp());
						LogUtil.debug("discarding a stale frame");
					}
					returnBorrowed(samples);
					samples = null;
					continue;
				}

				if (samples.isComplete()) {
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("$$AUDIO SET " + samples.getTimeStamp());
					}

					audioOutput.setCurrentSamples(samples.copyReference());

					// doSync(true);
				}
			} else {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("$$AUDIO REMOVE NULL");
			}
		}

		audioFrameBuffer = null;
		LogUtil.info("DefaultVideoPlayThread shutting down gracefully");
	}

	protected void returnBorrowed(IAudioSamples picture) {
		try {
			if (engineRuntime.getEngine().getEngineConfiguration()
					.getConfigurationValueAsBoolean(
							EngineConfiguration.USE_OBJECT_POOLS)) {
				engineRuntime.getAudioSamplePool().returnObject(picture);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected boolean framesAreStale(IAudioSamples samples) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("stale? " + samples.getTimeStamp() + " < "
					+ engineRuntime.getSynchronizer().getStreamTime());
		return (samples.getTimeStamp() < engineRuntime.getSynchronizer()
				.getStreamTime());
	}

	// TODO refactor to AbstractSynchronizedPlayer
	private boolean syncReady() {
		return engineRuntime.getSynchronizer().getStreamTime() > 0;
	}

	/**
	 * @deprecated this is the video sync algorithm
	 * @param sleep
	 */
	protected void doSync(boolean sleep) {
		long epts = engineRuntime.getSynchronizer().getStreamTime();
		long pts = audioOutput.getLastPts();

		if (pts > epts) {
			// long sleepTimeMillis = 0;
			long sleepTimeMillis = ((pts - epts) / 1000);
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
}
