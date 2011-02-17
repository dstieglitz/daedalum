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

package com.stainlesscode.mediapipeline.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IRational;

public class SeekHelper {

	private static Logger LogUtil = LoggerFactory.getLogger(SeekHelper.class);
	private EngineRuntime engineRuntime;

	public SeekHelper(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
	}

	public int seekFrames(long frames, boolean keyFrame) {
		IRational frameRate = engineRuntime.getVideoCoder().getFrameRate();

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("seek by frames -> "
					+ frames
					+ " currently at "
					+ TimeUtil.microsecondsToFrames(frameRate.getValue(),
							engineRuntime.getSynchronizer().getStreamTime()));

		int flags = IContainer.SEEK_FLAG_FRAME;

		if (!keyFrame) {
			flags |= IContainer.SEEK_FLAG_ANY;
		}

		if (TimeUtil.microsecondsToFrames(frameRate.getValue(), engineRuntime
				.getSynchronizer().getStreamTime()) > frames)
			flags |= IContainer.SEEK_FLAG_BACKWARDS;

		int retcode = 0;

		for (int i = 0; i < engineRuntime.getContainer().getNumStreams(); i++) {
			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("STREAM " + i + " seekTime in frames is "
						+ frames);
				LogUtil.debug("flags are " + flags);
			}

			retcode = engineRuntime.getContainer().seekKeyFrame(i, frames,
					frames, frames, flags);
		}

		return retcode;
	}

	public int seekMicros(long microseconds, boolean keyFrame) {
		int retcode = 0;

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("seek by microseconds -> " + microseconds
					+ " currently at "
					+ engineRuntime.getSynchronizer().getStreamTime());
			LogUtil.debug("seek to readable -> "
					+ TimeUtil.microsecondsToReadableTime(microseconds));
		}

		int flags = 0;

		if (!keyFrame) {
			flags |= IContainer.SEEK_FLAG_ANY;
		}

		if (engineRuntime.getSynchronizer().getStreamTime() > microseconds)
			flags |= IContainer.SEEK_FLAG_BACKWARDS;

		for (int i = 0; i < engineRuntime.getContainer().getNumStreams(); i++) {
			IRational timebase = engineRuntime.getContainer().getStream(i)
					.getTimeBase();
			long seekTime = TimeUtil.microsecondsToTimeBaseUnits(timebase,
					microseconds);

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("STREAM " + i + " seekTime in TBUs is "
						+ seekTime);
				LogUtil.debug("flags are " + flags);
			}

			retcode = engineRuntime.getContainer().seekKeyFrame(i, seekTime,
					seekTime, seekTime, flags);
		}

		return retcode;
	}

	public int seek(long microseconds, boolean keyFrame) {
		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("seek by microseconds -> " + microseconds
					+ " currently at "
					+ engineRuntime.getSynchronizer().getStreamTime());
			LogUtil.debug("seek to readable -> "
					+ TimeUtil.microsecondsToReadableTime(microseconds));
		}

		int flags = 0;

		if (!keyFrame) {
			flags |= IContainer.SEEK_FLAG_ANY;
		}

		if (engineRuntime.getSynchronizer().getStreamTime() > microseconds)
			flags |= IContainer.SEEK_FLAG_BACKWARDS;

		int retcode = engineRuntime.getContainer().seekKeyFrame(-1,
				microseconds, microseconds, microseconds, flags);
		return retcode;

	}
}
