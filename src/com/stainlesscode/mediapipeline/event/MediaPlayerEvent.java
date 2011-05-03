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

package com.stainlesscode.mediapipeline.event;

import java.util.EventObject;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IVideoPicture;

@SuppressWarnings("serial")
public class MediaPlayerEvent extends EventObject {

	public enum Type {
		MEDIA_LOADED, PLAY, STOP, PAUSE, UNPAUSE, CLIP_END, ERROR, PICTURE_DECODED, 
		KEY_FRAME_DECODED, AUDIO_FRAME_DECODED, BUFFER_FULL, BUFFER_EMPTY, 
		VIDEO_FRAME_PRESENTED, AUDIO_FRAME_PRESENTED, FIRST_VIDEO_FRAME_PRESENTED,
		STREAM_TIME_TICK, SEEK, PLAY_SPEED_CHANGED
	};

	private IVideoPicture picture;
	private IAudioSamples audio;
	private Object data;
	private Type type;

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public MediaPlayerEvent(Object source) {
		super(source);
	}

	public MediaPlayerEvent(Object source, Type type, Object data) {
		super(source);
		this.type = type;
		this.data = data;
	}

	public IVideoPicture getPicture() {
		return picture;
	}

	public void setPicture(IVideoPicture picture) {
		this.picture = picture;
	}

	public IAudioSamples getAudio() {
		return audio;
	}

	public void setAudio(IAudioSamples audio) {
		this.audio = audio;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}
	
	public String toString() {
		return type + " from " +source +" (" +data +")";
	}

}
