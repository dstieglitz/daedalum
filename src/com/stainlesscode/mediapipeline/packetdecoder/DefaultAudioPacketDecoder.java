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

package com.stainlesscode.mediapipeline.packetdecoder;

import java.util.NoSuchElementException;

import org.apache.commons.collections.BufferOverflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.PacketDecoder;
import com.stainlesscode.mediapipeline.sync.MultispeedVptsSynchronizer;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;

public class DefaultAudioPacketDecoder extends EngineThread implements
		PacketDecoder {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultAudioPacketDecoder.class);

	private EngineRuntime engineRuntime;
	private IAudioSamples samples = null;
	private boolean nextFrame = true;
	private boolean firstTimestamp = true;

	public DefaultAudioPacketDecoder() {
	}

	public void init(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
	}

	@SuppressWarnings("unchecked")
	public void decodePacket(IPacket packet) {
		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("buffer size = "
					+ engineRuntime.getAudioFrameBuffer().size());
			LogUtil.debug("decode audio packet " + packet.getTimeStamp());
		}

		try {
			if (nextFrame) {
				if (LogUtil.isDebugEnabled()) {
					LogUtil.debug("starting a new frame");
				}

				if (engineRuntime.getEngine().getEngineConfiguration()
						.getConfigurationValueAsBoolean(
								EngineConfiguration.USE_OBJECT_POOLS)) {
					samples = (IAudioSamples) engineRuntime
							.getAudioSamplePool().borrowObject();
				} else {
					samples = IAudioSamples.make(1024, engineRuntime
							.getAudioCoder().getChannels());
				}

				nextFrame = false;
			}

			int offset = 0;

			/*
			 * Keep going until we've processed all data
			 */
			while (offset < packet.getSize()) {
				int bytesDecoded = engineRuntime.getAudioCoder().decodeAudio(
						samples, packet, offset);
				if (bytesDecoded < 0)
					throw new RuntimeException("got error decoding audio");
				offset += bytesDecoded;
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("decoded " + offset
							+ " total bytes from packet");
			}

			returnBorrowed(packet);

			/*
			 * Some decoder will consume data in a packet, but will not be able
			 * to construct a full set of samples yet. Therefore you should
			 * always check if you got a complete set of samples from the
			 * decoder
			 */
			if (samples.isComplete()) {
				try {

					if (firstTimestamp) {
						LogUtil.info("First audio PTS is "
								+ samples.getTimeStamp());
						firstTimestamp = false;
					}

					// XXX audio drives sync with this code
					if (!engineRuntime.getSynchronizer().isStreamTimeZeroSet()) {

						engineRuntime.getSynchronizer().setStreamTimeZero(
								samples.getTimeStamp(), true);
					}

					engineRuntime.getAudioFrameBuffer().add(samples);
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("$$STORE AUDIO FRAME "
								+ samples.getTimeStamp());
					}
					nextFrame = true;
				} catch (BufferOverflowException e) {
					e.printStackTrace();
				}
			}
		} catch (NoSuchElementException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (!isMarkedForDeath()) {
			if (engineRuntime.getAudioPacketBuffer().isEmpty()) {
				if (clipEnded)
					setMarkedForDeath(true);
				else
					continue;
			}
			IPacket packet = (IPacket) engineRuntime.getAudioPacketBuffer()
					.remove();
			if (packet != null) {
				if (LogUtil.isDebugEnabled()) {
					LogUtil.debug("$$DECODE AUDIO PACKET "
							+ packet.getTimeStamp());
				}
				decodePacket(packet);
			} else {
				if (LogUtil.isDebugEnabled()) {
					LogUtil.debug("$$DECODE NULL AUDIO PACKET");
				}
			}
		}

		LogUtil.info("thread shutting down gracefully");
	}

	private void returnBorrowed(IPacket packet) {
		try {
			if (engineRuntime.getEngine().getEngineConfiguration()
					.getConfigurationValueAsBoolean(
							EngineConfiguration.USE_OBJECT_POOLS)) {
				engineRuntime.getPacketPool().returnObject(packet);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
