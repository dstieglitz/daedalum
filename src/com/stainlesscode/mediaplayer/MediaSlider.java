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

package com.stainlesscode.mediaplayer;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Map;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.Engine;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.util.TimeUtil;

@SuppressWarnings("serial")
public class MediaSlider extends JSlider implements MediaPlayerEventListener,
		MouseListener, ChangeListener {

	private Logger LogUtil = LoggerFactory.getLogger(MediaSlider.class);
	private Engine engine;
	private boolean respondToTicksEnabled = true;
	private long firstTimestampInStream = -1;

	public MediaSlider() {
		setEnabled(false);
		addMouseListener(this);
		addChangeListener(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (evt.getType() == MediaPlayerEvent.Type.MEDIA_LOADED) {
			Map metadata = (Map) evt.getData();
			long duration = ((Long) metadata.get("duration")).longValue();
			int durationSeconds = (int) duration / 1000000;
			setMaximum(durationSeconds);
			setMinimum(0);
			setEnabled(true);
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("duration is " + durationSeconds + " seconds");
		}

		if (evt.getType() == MediaPlayerEvent.Type.STREAM_TIME_TICK) {
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("tick " + evt.getData());
			if (firstTimestampInStream < 0)
				firstTimestampInStream = ((Long) evt.getData()).longValue();

			int relTimeMillis = (int) (firstTimestampInStream - ((Long) evt
					.getData()).intValue());

			if (respondToTicksEnabled)
				setValue(relTimeMillis / 1000000);
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {

	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("slider got mouse click event");
		respondToTicksEnabled = false;
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		respondToTicksEnabled = true;
	}

	public Engine getEngine() {
		return engine;
	}

	public void setEngine(Engine engine) {
		this.engine = engine;
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (!respondToTicksEnabled) {
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("slider got mouse change event");
			long newVal = (long) getValue() * 1000000L;
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("slider seeking to "
						+ TimeUtil.microsecondsToReadableTime(newVal));
			engine.seek(newVal);
		}
	}

}
