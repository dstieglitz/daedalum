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

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.Synchronizer;
import com.xuggle.xuggler.IAudioSamples;

/**
 * This synchronizer uses the PTS of the audio track as the stream clock
 * 
 * @author Dan Stieglitz
 * 
 */
public class SimpleSynchronizer implements Synchronizer {

	private static Logger LogUtil = LoggerFactory
			.getLogger(SimpleSynchronizer.class);

	private EngineRuntime engineRuntime;
	private long audioWriteLatency;

	public void init(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
	}

	public void setAudioWriteLatency(long audioWriteLatency) {
		this.audioWriteLatency = audioWriteLatency;
	}

	public long getAudioWriteLatency() {
		return audioWriteLatency;
	}

	// returns the current stream time, or VPTS for both
	// audio and video
	public long getStreamTime() {
		long streamTime = -1;
		Buffer buf = engineRuntime.getAudioFrameBuffer();
		if (!buf.isEmpty()) {
			IAudioSamples samples = (IAudioSamples) buf.get();
			if (samples != null)
				streamTime = samples.getTimeStamp();
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("getStreamTime called, and it's " + streamTime);
		}
		
		return streamTime;
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

}
