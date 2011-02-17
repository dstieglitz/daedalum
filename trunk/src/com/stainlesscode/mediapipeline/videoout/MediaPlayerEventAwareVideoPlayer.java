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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.VideoOutput;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;

/**
 * Currently doesn't really do anything...
 * @author Dan Stieglitz
 *
 */
public class MediaPlayerEventAwareVideoPlayer extends DefaultVideoPlayer
		implements MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(MediaPlayerEventAwareVideoPlayer.class);

	public MediaPlayerEventAwareVideoPlayer(VideoOutput screen,
			EngineRuntime runtime) {
		super(screen, runtime);
		runtime.getEngine().addMediaPlayerEventListener(this);
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (evt.getType() == MediaPlayerEvent.Type.SEEK) {
//			if (LogUtil.isDebugEnabled()) {
//				LogUtil.debug("*---------------------------------------------> SEEK "+evt.getData());
//			}
		}
	}

}