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

import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

public class PacketOrderLogFilter extends Filter {

	@Override
	public int decide(LoggingEvent evt) {
		if (evt.getMessage().toString().startsWith("$$")
				|| evt.getMessage().toString().startsWith("@@"))
			return Filter.ACCEPT;
		else
			return Filter.DENY;
	}
}
