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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

@SuppressWarnings("serial")
public class MetadataTreeModel extends DefaultTreeModel {

	@SuppressWarnings("unchecked")
	private Map nameNodeMap = new HashMap<String, DefaultMutableTreeNode>();

	@SuppressWarnings("unchecked")
	public MetadataTreeModel(Map metadata) {
		super(new DefaultMutableTreeNode("Root"));
		parseMetadataMap(metadata);
		System.out.println(nameNodeMap);
	}

	@SuppressWarnings("unchecked")
	private void parseMetadataMap(Map metadata) {
		Iterator<String> keys = metadata.keySet().iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			DefaultMutableTreeNode node = getTreeNodeForKey(key);
			node.setUserObject(key+"="+metadata.get(key));
		}
	}

	private DefaultMutableTreeNode getTreeNodeForKey(String key) {
		StringTokenizer tok = new StringTokenizer(key, ".");
		DefaultMutableTreeNode lastNodeInPath = null;
		DefaultMutableTreeNode previousNodeInPath = null;
		
		while (tok.hasMoreTokens()) {
			String nt = tok.nextToken();
			lastNodeInPath = getOrCreateNode(nt);
			if (lastNodeInPath.getParent() == null) {
				if (previousNodeInPath == null)
					((DefaultMutableTreeNode)super.root).add(lastNodeInPath);
				else
					previousNodeInPath.add(lastNodeInPath);
			}
			previousNodeInPath = lastNodeInPath;
		}

		return lastNodeInPath;
	}

	@SuppressWarnings("unchecked")
	private DefaultMutableTreeNode getOrCreateNode(String nt) {
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) nameNodeMap
				.get(nt);
		if (node == null) {
			node = new DefaultMutableTreeNode(nt);
			nameNodeMap.put(nt, node);
		}
		return node;
	}

}
