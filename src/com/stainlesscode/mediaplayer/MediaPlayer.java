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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.Engine;
import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent.Type;
import com.stainlesscode.mediapipeline.util.MetadataUtil;
import com.stainlesscode.mediapipeline.videoout.DefaultVideoPanel;

@SuppressWarnings("serial")
public class MediaPlayer extends JFrame implements MediaPlayerEventListener {

	@SuppressWarnings("unused")
	private static final Logger LogUtil = LoggerFactory
			.getLogger(MediaPlayer.class);

	protected static DefaultVideoPanel panel = new DefaultVideoPanel();
	protected Engine engine;
	protected MediaSlider slider;
	protected JDialog controls, metadataDialog;
	protected JButton playButton, pauseButton, stopButton;
	protected JToggleButton showOSDButton, ptOneX, ptFiveX, oneX, twoX, fourX;
	protected JPanel pushbuttonPanel;

	public MediaPlayer(String title) {
		super(title);
		initComponents();
	}
	
	protected void initComponents() {
		final MediaPlayer frame = this;

		try {
			final MenuBuilder menuBuilder = new MenuBuilder(this, "history.txt");
			this.setJMenuBar(menuBuilder.buildJMenuBar());

			JMenu windowMenu = new JMenu("Window");
			JCheckBoxMenuItem showMetadata = new JCheckBoxMenuItem("Metadata");
			showMetadata.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (((JCheckBoxMenuItem) e.getSource()).isSelected()) {
						metadataDialog.pack();
						metadataDialog.setVisible(true);
					} else {
						metadataDialog.setVisible(false);
					}
				}
			});

			windowMenu.add(showMetadata);

			this.getJMenuBar().add(windowMenu);

			this.add(panel, BorderLayout.CENTER);

			controls = new JDialog(this, false);
			controls.setTitle("Controls");

			metadataDialog = new JDialog(this, false);
			metadataDialog.setTitle("Metadata");
			metadataDialog.setPreferredSize(new Dimension(320, 240));

			pushbuttonPanel = new JPanel();

			this.pauseButton = new JButton("Pause");
			this.pauseButton.setEnabled(false);
			this.playButton = new JButton("Play");
			this.playButton.setEnabled(false);
			
			playButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					engine.start();
					playButton.setEnabled(false);
					pauseButton.setEnabled(true);
					stopButton.setEnabled(true);
				}
			});

			pauseButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (engine.isPaused()) {
						pauseButton.setText("Pause");
						engine.unpause();
					} else {
						pauseButton.setText("Unpause");
						engine.pause();
					}
				}
			});

			this.stopButton = new JButton("Stop");
			this.stopButton.setEnabled(false);
			stopButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					doStop();
				}
			});

			addControl(playButton);
			addControl(pauseButton);
			addControl(stopButton);

			ButtonGroup speedGroup = new ButtonGroup();
			JPanel togglebuttonPanel = new JPanel();
			ptOneX = new JToggleButton("0.1x");
			ptFiveX = new JToggleButton("0.5x");
			oneX = new JToggleButton("1.0x");
			twoX = new JToggleButton("2.0x");
			fourX = new JToggleButton("4.0x");
			speedGroup.add(ptOneX);
			speedGroup.add(ptFiveX);
			speedGroup.add(oneX);
			speedGroup.add(twoX);
			speedGroup.add(fourX);

			ptOneX.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					engine.setPlaySpeed(0.1);
				}
			});

			ptFiveX.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					engine.setPlaySpeed(0.5);
				}
			});

			oneX.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					engine.setPlaySpeed(1.0);
				}
			});

			twoX.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					engine.setPlaySpeed(2.0);
				}
			});

			fourX.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					engine.setPlaySpeed(4.0);
				}
			});

			slider = new MediaSlider();

			oneX.setSelected(true);
			togglebuttonPanel.add(ptOneX);
			togglebuttonPanel.add(ptFiveX);
			togglebuttonPanel.add(oneX);
			togglebuttonPanel.add(twoX);
			togglebuttonPanel.add(fourX);

			JPanel miscControlsPanel = new JPanel();
			showOSDButton = new JToggleButton("OSD");
			showOSDButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					((DefaultVideoPanel) engine.getVideoOutput())
							.setShowOSD(showOSDButton.isSelected());
				}
			});
			
			miscControlsPanel.add(showOSDButton);

			frame.add(slider, BorderLayout.SOUTH);
			controls.getContentPane().add(pushbuttonPanel, BorderLayout.NORTH);
			controls.getContentPane().add(miscControlsPanel,
					BorderLayout.CENTER);
			controls.getContentPane()
					.add(togglebuttonPanel, BorderLayout.SOUTH);
			controls.pack();
			controls.setVisible(true);

			ptOneX.setEnabled(false);
			ptFiveX.setEnabled(false);
			oneX.setEnabled(false);
			twoX.setEnabled(false);
			fourX.setEnabled(false);
			showOSDButton.setEnabled(false);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void doStop() {
		playButton.setEnabled(true);
		pauseButton.setEnabled(false);
		stopButton.setEnabled(false);
		ptOneX.setEnabled(false);
		ptFiveX.setEnabled(false);
		oneX.setEnabled(false);
		twoX.setEnabled(false);
		fourX.setEnabled(false);
		showOSDButton.setEnabled(false);
		engine.stop();
	}

	private void populateMetadataTree(Map<String, Object> metaData) {
		JTree tree = new JTree();
		tree.setModel(new MetadataTreeModel(metaData));
		JScrollPane metadataScrollPane = new JScrollPane(tree);
		metadataDialog.getContentPane().setLayout(new BorderLayout());
		metadataDialog.getContentPane().add(metadataScrollPane,
				BorderLayout.CENTER);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MediaPlayer frame;

		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty("com.apple.mrj.application.apple.menu.about.name",
				"Daedalum");

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedLookAndFeelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		frame = new MediaPlayer("Daedalum Test Rig");
		// Add a window listner for close button
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		// frame.addComponentListener(new java.awt.event.ComponentAdapter() {
		// public void componentResized(ComponentEvent e) {
		// MediaPlayer.panel.setVideoSize(new Dimension(e.getComponent()
		// .getWidth(), e.getComponent().getHeight()));
		// MediaPlayer.panel.setPreferredSize(new Dimension(e
		// .getComponent().getWidth(), e.getComponent()
		// .getHeight()));
		// }
		// });
		frame.setPreferredSize(new Dimension(320, 240));
		frame.pack();
		frame.setVisible(true);
		frame.controls.setLocation(new Point(frame.getX(), frame.getY()
				+ frame.getHeight()));
	}
	
	protected void addControl(JComponent control) {
		pushbuttonPanel.add(control);
	}

	public void doLoad(String url) {
		try {
			if (engine!=null) engine.stop();
			engine = createEngine();
			engine.addMediaPlayerEventListener(this);
			engine.setVideoOutput(panel);
			engine.addMediaPlayerEventListener(slider);
			engine.loadUrl(url);
			slider.setEngine(engine);
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	protected Engine createEngine() {
		return Engine.createEngine(new EngineConfiguration());
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (evt.getType() == Type.STOP) {
			doStop();
		}
		
		if (evt.getType() == Type.MEDIA_LOADED) {
			playButton.setEnabled(true);
			pauseButton.setEnabled(false);
			stopButton.setEnabled(false);
			ptOneX.setEnabled(true);
			ptFiveX.setEnabled(true);
			oneX.setEnabled(true);
			twoX.setEnabled(true);
			fourX.setEnabled(true);
			showOSDButton.setEnabled(true);

			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {

					((MediaPlayerEventSupport) engine.getEngineRuntime()
							.getSynchronizer())
							.addMediaPlayerEventListener(slider);

					populateMetadataTree(MetadataUtil.getMetaData(engine
							.getEngineRuntime().getContainer()));
					int videoWidth = Integer.parseInt(engine.getMetadata().get(
							"width").toString());
					int videoHeight = Integer.parseInt(engine.getMetadata()
							.get("height").toString());
					System.out.println("" + videoWidth + "," + videoHeight);

					Dimension dim;

					dim = new Dimension(videoWidth, videoHeight
							+ slider.getHeight());

					MediaPlayer.this.setSize(dim);
					MediaPlayer.this.invalidate();
					MediaPlayer.this.repaint();

					MediaPlayer.this.controls.setLocation(new Point(
							MediaPlayer.this.getX(), MediaPlayer.this.getY()
									+ MediaPlayer.this.getHeight()));
				}

			});

		}
	}
}
