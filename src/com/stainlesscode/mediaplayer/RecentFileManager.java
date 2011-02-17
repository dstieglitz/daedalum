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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class RecentFileManager {

	private File recentFiles;
	private PrintWriter recentFileWriter;
	private String lastFolder;
	private List<String> recentItems = new ArrayList<String>();

	public RecentFileManager(String recentFileHistoryFile) throws IOException {
		if (recentFileHistoryFile == null)
			recentFileHistoryFile = "history.txt";
		this.recentFiles = new File(recentFileHistoryFile);
		if (!recentFiles.exists()) {
			recentFiles.createNewFile();
		}
		BufferedReader recentFileReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(recentFiles)));
		String str;
		while ((str = recentFileReader.readLine()) != null) {
			recentItems.add(str);
		}
	}

	public File getRecentFiles() {
		return recentFiles;
	}

	public void setRecentFiles(File recentFiles) {
		this.recentFiles = recentFiles;
	}

	public PrintWriter getRecentFileWriter() {
		return recentFileWriter;
	}

	public void setRecentFileWriter(PrintWriter recentFileWriter) {
		this.recentFileWriter = recentFileWriter;
	}

	public String getLastFolder() {
		return lastFolder;
	}

	public void setLastFolder(String lastFolder) {
		this.lastFolder = lastFolder;
	}

	public List<String> getRecentItems() {
		return recentItems;
	}

	public void setRecentItems(List<String> recentItems) {
		this.recentItems = recentItems;
	}

	void refreshRecentMenuItems() throws IOException {
		if (recentFiles.exists()) {
			recentFiles.delete();
		}

		// eliminate duplicates the easy way
		HashSet<String> ris = new HashSet<String>(recentItems);

		recentFiles.createNewFile();
		recentFileWriter = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(recentFiles)));

		Iterator<String> recentItemIterator = ris.iterator();
		while (recentItemIterator.hasNext()) {
			String item = (String) recentItemIterator.next();
			// System.out.println("writing " + item + " to file");
			recentFileWriter.write(item + "\n");
		}

		recentFileWriter.flush();
		recentFileWriter.close();
	}

	public void addRecentItem(String url2) throws IOException {
		recentItems.add(url2);
		refreshRecentMenuItems();
	}

}
