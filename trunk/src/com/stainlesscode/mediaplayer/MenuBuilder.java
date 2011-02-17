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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

public class MenuBuilder {

	private JFrame frame;
	private String url;
	private RecentFileMenu loadRecent;
	private RecentFileManager recentFileManager;

	public MenuBuilder(JFrame frame, String recentFilePath) throws IOException {
		this.frame = frame;
		this.recentFileManager = new RecentFileManager(recentFilePath);
	}

	public JMenuBar buildJMenuBar() throws IOException {
		JMenuBar menuBar = new JMenuBar();

		JMenu fileMenu = new JMenu("File");
		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});

		JMenuItem loadFile = new JMenuItem("Load file...");
		loadFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser();
				if (recentFileManager.getLastFolder() != null) {
					fc.setCurrentDirectory(new File(recentFileManager
							.getLastFolder()));
				}
				int returnVal = fc.showOpenDialog(frame);
				recentFileManager.setLastFolder(fc.getCurrentDirectory()
						.getAbsolutePath());
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fc.getSelectedFile();
					url = "file://" + file.getAbsolutePath();
					doLoad(url);
				}
			}
		});

		JMenuItem loadUrl = new JMenuItem("Load url...");
		loadUrl.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String requestedUrl = JOptionPane.showInputDialog("Enter URL");
				doLoad(requestedUrl);
			}
		});

		loadRecent = new RecentFileMenu("Load recent", recentFileManager);
		loadRecent.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				url = ((JMenuItem) e.getSource()).getText();
				doLoad(url);
			}
		});

		fileMenu.add(loadFile);
		fileMenu.add(loadUrl);
		fileMenu.add(loadRecent);
		fileMenu.add(exit);
		menuBar.add(fileMenu);

		return menuBar;
	}

	private void doLoad(String url) {
		addRecentItem(url);
		((MediaPlayer) frame).doLoad(url);
	}

	public String getUrl() {
		return url;
	}

	public void addRecentItem(String url2) {
		try {
			loadRecent.addRecentItem(url2);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
