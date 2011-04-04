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

package com.stainlesscode.mediapipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * A container for storing all the information that a media pipeline engine
 * needs to construct itself. This file should (eventually) be serialized into a
 * properties or XML file for easy configuration of engines.
 * 
 * @author Dan Stieglitz
 * 
 */
public class EngineConfiguration {

	public static final String DEMUX_KEY = "demultiplexer";

	public static final String VIDEO_OUTPUT_KEY = "videoOutput";

	public static final String SYNCHRONIZER_KEY = "synchronizer";

	public static final String AUDIO_OUTPUT_KEY = "audioOutput";

	public static final String VIDEO_PACKET_BUFFER_SIZE_KEY = "videoPacketFrameSize";

	public static final String AUDIO_PACKET_BUFFER_SIZE_KEY = "audioPacketFrameSize";

	public static final String VIDEO_FRAME_BUFFER_SIZE_KEY = "videoFrameBufferSize";

	public static final String AUDIO_FRAME_BUFFER_SIZE_KEY = "audioFrameBufferSize";

	public static final String CHECK_MEMORY_KEY = "checkMemory";

	public static final String CHECK_THREADS_KEY = "checkThreads";

	public static final String SHOW_FIRST_FRAME_KEY = "showFirstFrame";

	public static final String AUTO_START_KEY = "showFirstFrame";

	public static final String AUDIO_PACKET_DECODER_KEY = "audioPacketDecoder";

	public static final String VIDEO_PACKET_DECODER_KEY = "videoPacketDecoder";

	public static final String USE_OBJECT_POOLS = "useObjectPools";

	private Map<String, String> configuration;

	public EngineConfiguration() {
		configuration = new HashMap<String, String>();
		configuration.put(DEMUX_KEY,
				"com.stainlesscode.mediapipeline.demux.SimpleDemultiplexer");
		configuration.put(VIDEO_OUTPUT_KEY,
				"com.stainlesscode.mediapipeline.videoout.DefaultVideoPanel");
		configuration
				.put(SYNCHRONIZER_KEY,
						"com.stainlesscode.mediapipeline.sync.MultispeedVptsSynchronizer");
		configuration
				.put(AUDIO_OUTPUT_KEY,
						"com.stainlesscode.mediapipeline.audioout2.JavaSoundAudioDriver");
		configuration
				.put(VIDEO_PACKET_DECODER_KEY,
						"com.stainlesscode.mediapipeline.packetdecoder.DefaultVideoPacketDecoder");
		configuration
				.put(AUDIO_PACKET_DECODER_KEY,
						"com.stainlesscode.mediapipeline.packetdecoder.DefaultAudioPacketDecoder");
		configuration.put(VIDEO_PACKET_BUFFER_SIZE_KEY, "1000");
		configuration.put(AUDIO_PACKET_BUFFER_SIZE_KEY, "1000");
		configuration.put(VIDEO_FRAME_BUFFER_SIZE_KEY, "50");
		configuration.put(AUDIO_FRAME_BUFFER_SIZE_KEY, "100");
		configuration.put(USE_OBJECT_POOLS, "false");
		configuration.put(CHECK_MEMORY_KEY, "false");
		configuration.put(CHECK_THREADS_KEY, "false");
		configuration.put(SHOW_FIRST_FRAME_KEY, "true");
		configuration.put(AUTO_START_KEY, "true");
	}

	public Map<String, String> getConfiguration() {
		return configuration;
	}

	public int getConfigurationValueAsInt(String key) {
		return Integer.parseInt(configuration.get(key));
	}

	public boolean getConfigurationValueAsBoolean(String checkMemoryKey) {
		if (configuration.get(CHECK_MEMORY_KEY) == null)
			return false;
		return Boolean.parseBoolean(configuration.get(checkMemoryKey));
	}

	public void put(String key, String val) {
		configuration.put(key, val);
	}
}
