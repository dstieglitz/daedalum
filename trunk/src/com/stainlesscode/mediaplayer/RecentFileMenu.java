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
import java.io.IOException;
import java.util.Iterator;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

@SuppressWarnings("serial")
public class RecentFileMenu extends JMenu {
	
	private RecentFileManager recentFileManager;

	public RecentFileMenu(String label) throws IOException {
		super(label);
		this.recentFileManager = new RecentFileManager("history.txt");
		this.refeshLoadRecent();
	}
	
	public RecentFileMenu(String label, String historyFile) throws IOException {
		super(label);
		this.recentFileManager = new RecentFileManager(historyFile);
		this.refeshLoadRecent();
	}
	
	public RecentFileMenu(String label, RecentFileManager manager) throws IOException {
		super(label);
		this.recentFileManager = manager;
		this.refeshLoadRecent();
	}
	
	public RecentFileMenu(String label, RecentFileManager manager, ActionListener al) throws IOException {
		super(label);
		this.recentFileManager = manager;
		this.refeshLoadRecent();
		this.addActionListener(al);
	}
	
	private void refeshLoadRecent() throws IOException {
		super.removeAll();
		this.recentFileManager.getRecentItems().iterator();
		Iterator<String> recentItemIterator = recentFileManager.getRecentItems().iterator();
		while (recentItemIterator.hasNext()) {
			String item = recentItemIterator.next();
			this.add(createMenuItem(item));
		}
	}
	
	private JMenuItem createMenuItem(String str) {
		JMenuItem recentItem = new JMenuItem(str);
		recentItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				RecentFileMenu.this.actionPerformed(e);
			}
		});
		return recentItem;
	}

	protected void actionPerformed(ActionEvent e) {
		for (ActionListener l : super.getActionListeners()) {
			l.actionPerformed(e);
		}
	}

	public void addRecentItem(String url2) throws IOException {
		recentFileManager.addRecentItem(url2);
		refeshLoadRecent();
	}
}
