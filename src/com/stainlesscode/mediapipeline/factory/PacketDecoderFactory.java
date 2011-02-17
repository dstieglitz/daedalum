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

package com.stainlesscode.mediapipeline.factory;

import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.PacketDecoder;

public class PacketDecoderFactory {

	public static enum Type {
		AUDIO, VIDEO
	};

	public static PacketDecoder createPacketDecoder(Type type,
			EngineConfiguration engineConfiguration)
			throws InstantiationException, IllegalAccessException,
			ClassNotFoundException {
		String className = null;
		if (type == Type.AUDIO) {
			className = engineConfiguration.getConfiguration().get(
					EngineConfiguration.AUDIO_PACKET_DECODER_KEY);
		} else if (type == Type.VIDEO) {
			className = engineConfiguration.getConfiguration().get(
					EngineConfiguration.VIDEO_PACKET_DECODER_KEY);
		}
		if (className != null) {
			PacketDecoder pd = (PacketDecoder) Class.forName(className)
					.newInstance();
			return pd;
		} else {
			return null;
		}
	}
}
