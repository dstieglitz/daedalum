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

package com.stainlesscode.mediapipeline.logfilter;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

public class DuplicateLogMessageFilter extends Filter {

	private Map<Object, Integer> duplicateMessages = new HashMap<Object, Integer>();
	private int reminderInterval = 10;
	private Object lastMessage;

	@Override
	public int decide(LoggingEvent evt) {
		if (lastMessage != null
				&& evt.getMessage().toString().equals(lastMessage.toString())) {
			if (duplicateMessages.containsKey(evt.getMessage())) {
				int count = duplicateMessages.get(evt.getMessage()).intValue();
				if (count++ == reminderInterval) {
					evt.getLogger().info(
							evt.getMessage().toString() + " logged "
									+ reminderInterval + " times");
				}
				duplicateMessages.put(evt.getMessage(), count++);
				return Filter.DENY;
			} else {
				duplicateMessages.put(lastMessage, 1);
				lastMessage = evt.getMessage();
				return Filter.NEUTRAL;
			}
		}
		lastMessage = evt.getMessage();
		return Filter.NEUTRAL;
	}
}
