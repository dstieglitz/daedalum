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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.VideoOutput;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.util.MediaPlayerEventSupportedJComponent;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * TODO need a better way to return pictures to the pool, esp. if we are going
 * to chain VideoOutput together (i.e., picture can only be returned after all
 * outputs have processed it). Maybe make RoutingVideoOutput the default and
 * return the picture there.
 * 
 * @author Dan Stieglitz
 * 
 */
@SuppressWarnings("serial")
public class DefaultVideoPanel extends MediaPlayerEventSupportedJComponent
		implements VideoOutput {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultVideoPanel.class);

	protected EngineRuntime engineRuntime;

	protected Image currentFrame;
	// protected IVideoPicture currentPicture;
	protected Dimension size;
	protected Dimension videoSize;

	protected boolean resizeVideo;
	protected boolean showTimecode;
	protected boolean showOnScreenDisplay;
	protected IConverter converter;

	private boolean firstFrame = true;

	private long lastPts;

	public DefaultVideoPanel() {
		setPreferredSize(new Dimension(320, 240));
		setBackground(Color.black);
	}

	public void init(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
		this.addMediaPlayerEventListener(this.engineRuntime.getEngine());
	}

	public void setCurrentFrame(final IVideoPicture picture) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("setting picture " + picture.getTimeStamp());

		// this.currentPicture = picture;
		this.lastPts = picture.getPts();
		// returnBorrowed(picture);

		if (converter == null) {
			converter = (IConverter) engineRuntime.getUserObject("converter");
			if (converter == null) {
				this.converter = ConverterFactory.createConverter(
						ConverterFactory.XUGGLER_BGR_24, picture);
				engineRuntime.putUserObject("converter", converter);
			}
		}

		this.currentFrame = converter.toImage(picture);

		if (currentFrame != null && this.videoSize == null) {
			this.videoSize = new Dimension(currentFrame.getWidth(null),
					currentFrame.getHeight(null));
			this.setPreferredSize(this.videoSize);

			// final Dimension newSize = new Dimension(
			// currentFrame.getWidth(null), currentFrame.getHeight(null));
			// if (!newSize.equals(getSize())) {
			// Dimension dim = new Dimension(currentFrame.getWidth(null),
			// currentFrame.getHeight(null));
			//
			// if (LogUtil.isDebugEnabled())
			// LogUtil.debug("setting video panel size to " + dim +
			// " from "+getSize());
			//
			// setMaximumSize(dim);
			// if (!isVisible()) setVisible(true);
			// if (videoSize == null)
			// videoSize = dim;
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				revalidate();
				repaint();
			}
		});
	}

	private void returnBorrowed(IVideoPicture picture) {
		try {
			if (engineRuntime.getEngine().getEngineConfiguration()
					.getConfigurationValueAsBoolean(
							EngineConfiguration.USE_OBJECT_POOLS)) {
				engineRuntime.getResampledPicturePool().returnObject(picture);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getTimecode(long streamTimeInMicroseconds) {
		try {
			double fr = engineRuntime.getVideoCoder().getFrameRate().getValue();
			return TimeUtil.getTimecode(fr, streamTimeInMicroseconds);
		} catch (Throwable t) {
			return "";
		}
	}

	BufferedImage createResizedCopy(Image originalImage, int scaledWidth,
			int scaledHeight, boolean preserveAlpha) {

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("resizing to " + scaledWidth + "," + scaledHeight);

		int imageType = preserveAlpha ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		BufferedImage scaledBI = new BufferedImage(scaledWidth, scaledHeight,
				imageType);
		Graphics2D g = scaledBI.createGraphics();

		if (preserveAlpha) {
			g.setComposite(AlphaComposite.Src);
		}

		g.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
		g.dispose();
		return scaledBI;
	}

	public void paintComponent(Graphics g) {
		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, // Anti-alias!
				RenderingHints.VALUE_ANTIALIAS_ON);

		if (currentFrame == null) {
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
		} else {
			if (resizeVideo && !size.equals(videoSize)) {
				currentFrame = createResizedCopy(currentFrame, videoSize.width,
						videoSize.height, true);
			}

			if (LogUtil.isDebugEnabled())
				LogUtil.debug("$$VIDEO PLAY " + lastPts);
			g.drawImage(currentFrame, 0, 0, this);

			if (showTimecode) {
				String str = getTimecode(engineRuntime.getSynchronizer()
						.getStreamTime());
				g.setColor(Color.white);
				Font f = new Font("Sans", Font.BOLD, 60);
				g.setFont(f);
				FontMetrics fm = g.getFontMetrics(f);
				int xp = currentFrame.getWidth(this) / 2
						- fm.charsWidth(str.toCharArray(), 0, str.length()) / 2;
				int yp = currentFrame.getHeight(this) - fm.getHeight() - 20;
				g.drawString(str, xp, yp);
			}

			if (showOnScreenDisplay) {
				Font f = new Font("Sans", Font.BOLD, 20);
				g.setFont(f);
				String str = engineRuntime.getPlaySpeed() + "x";
				g.setColor(Color.white);
				g.drawString(str, 20, 20);
			}

			if (this.firstFrame) {
				fireMediaPlayerEvent(new MediaPlayerEvent(this,
						MediaPlayerEvent.Type.FIRST_VIDEO_FRAME_PRESENTED,
						this.currentFrame));
				this.firstFrame = false;
			}
		}
	}

	// public void addMediaPlayerEventListener(MediaPlayerEventListener l) {
	// listeners.add(l);
	// }
	//
	// public void removeMediaPlayerEventListener(MediaPlayerEventListener l) {
	// listeners.remove(l);
	// }
	//
	// public void fireMediaPlayerEvent(MediaPlayerEvent e) {
	// for (int i = 0; i < listeners.size(); i++) {
	// if (LogUtil.isDebugEnabled())
	// LogUtil.debug("*** FIRING " + e + " TO "
	// + listeners.get(i));
	// listeners.get(i).mediaPlayerEventReceived(e);
	// }
	// }

	public void setPlayerRuntime(EngineRuntime arg0) {
		this.engineRuntime = arg0;
	}

	// public FrameSync getFrameSync() {
	// return frameSync;
	// }
	//
	// public void setFrameSync(FrameSync frameSync) {
	// this.frameSync = frameSync;
	// }

	// public IVideoPicture getCurrentFrame() {
	// return currentPicture;
	// }

	public void setShowTimecode(boolean showTimecode) {
		this.showTimecode = showTimecode;
	}

	public boolean isShowTimecode() {
		return showTimecode;
	}

	// public IVideoPicture getCurrentPicture() {
	// return this.currentPicture;
	// }

	public Dimension getVideoSize() {
		return videoSize;
	}

	public void setVideoSize(Dimension videoSize) {
		this.videoSize = videoSize;
	}

	public boolean isShowOnScreenDisplay() {
		return showOnScreenDisplay;
	}

	public void setShowOnScreenDisplay(boolean showOnScreenDisplay) {
		this.showOnScreenDisplay = showOnScreenDisplay;
	}

	@Override
	public void close() {
		currentFrame = null;
		this.firstFrame = true;
		this.removeMediaPlayerEventListener(engineRuntime.getEngine());
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				revalidate();
				repaint();
			}
		});
	}

	public boolean isResizeVideo() {
		return resizeVideo;
	}

	public void setResizeVideo(boolean resizeVideo) {
		this.resizeVideo = resizeVideo;
	}

	public void setShowOSD(boolean b) {
		showTimecode = b;
		showOnScreenDisplay = b;
	}

	public void setLastPts(long lastPts) {
		this.lastPts = lastPts;
	}

	public long getLastPts() {
		return lastPts;
	}

}