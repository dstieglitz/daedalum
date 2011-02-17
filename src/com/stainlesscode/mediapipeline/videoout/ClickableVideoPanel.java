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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;

@SuppressWarnings("serial")
public class ClickableVideoPanel extends DefaultVideoPanel {

	private static Logger LogUtil = LoggerFactory.getLogger(ClickableVideoPanel.class);
	
	private int imageType;
	private Point clickedPoint;
	private Rectangle selectedRegion;
	private Image clipboard;

	// private Lock paintLock = new ReentrantLock(true);

	public class ClickableVideoPanelMouseAdapter extends MouseInputAdapter {
		@Override
		public void mouseClicked(MouseEvent e) {
			super.mouseClicked(e);
			ClickableVideoPanel.this.clickedPoint = e.getPoint();
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("clickedPoint is "
						+ ClickableVideoPanel.this.clickedPoint);
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			super.mouseDragged(e);
			int x = ClickableVideoPanel.this.clickedPoint.x;
			int y = ClickableVideoPanel.this.clickedPoint.y;
			int w = e.getX() - x;
			int h = e.getY() - y;
			if (w < 0) {
				x = x + w;
				w = -w;
			}
			if (h < 0) {
				y = y + h;
				h = -h;
			}
			ClickableVideoPanel.this.selectedRegion = new Rectangle(x, y, w, h);

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("clickedPoint is "
						+ ClickableVideoPanel.this.clickedPoint);
				LogUtil.debug("Updated selected region to "
						+ ClickableVideoPanel.this.selectedRegion);
			}

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					ClickableVideoPanel.this.revalidate();
					ClickableVideoPanel.this.repaint();
				}
			});
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			// TODO Auto-generated method stub
			super.mouseEntered(e);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			// TODO Auto-generated method stub
			super.mouseExited(e);
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			// TODO Auto-generated method stub
			super.mouseMoved(e);
			if (LogUtil.isDebugEnabled()) LogUtil.debug("mouse moved " + e.getPoint());
		}

		@Override
		public void mousePressed(MouseEvent e) {
			super.mousePressed(e);
			ClickableVideoPanel.this.clickedPoint = e.getPoint();
			ClickableVideoPanel.this.clearSelection();
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			// grab selected region of current frame
			super.mouseReleased(e);
			if (currentFrame != null
					&& ClickableVideoPanel.this.selectedRegion != null) {
				BufferedImage clip = new BufferedImage(selectedRegion.width,
						selectedRegion.height, imageType);
				Graphics g = clip.getGraphics();

				// XXX important, notice that we can't use getSubimage directly
				// with OpenCV stuff to due raster crap
				g.drawImage(((BufferedImage) currentFrame).getSubimage(
						ClickableVideoPanel.this.selectedRegion.x,
						ClickableVideoPanel.this.selectedRegion.y,
						ClickableVideoPanel.this.selectedRegion.width,
						ClickableVideoPanel.this.selectedRegion.height), 0, 0,
						clip.getWidth(), clip.getHeight(), null);
				setClipboard(clip);
				g.dispose();
			}
		}
	}

	public ClickableVideoPanel() {
		addMouseListener(new ClickableVideoPanelMouseAdapter());
		addMouseMotionListener(new ClickableVideoPanelMouseAdapter());
	}

	public ClickableVideoPanel(EngineRuntime runtime) {
		this();
		this.engineRuntime = runtime;
	}

	public void clearSelection() {
		this.selectedRegion = null;

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				revalidate();
				repaint();
			}
		});
	}

	public void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		super.paintComponent(g2d);

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("selectedRegion is " + selectedRegion);

		if (selectedRegion != null) {
			g2d.setColor(new java.awt.Color(1, 1, 255, 128));
			g.fillRect(selectedRegion.x, selectedRegion.y,
					selectedRegion.width, selectedRegion.height);
		}
	}

	public void setClipboard(Image clipboard) {
		this.clipboard = clipboard;
	}

	public Image getClipboard() {
		return clipboard;
	}
}
