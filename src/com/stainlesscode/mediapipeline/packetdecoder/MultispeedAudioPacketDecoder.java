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

import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.PacketDecoder;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;

/**
 * Works like the MultispeedDemultiplexer by dropping every nth packet. This
 * also causes funky side-effects. A better approach is to interpolate the audio
 * so we have all audio data at hand.
 * 
 * @deprecated
 * @author Dan Stieglitz
 * 
 */
public class MultispeedAudioPacketDecoder extends EngineThread implements
		PacketDecoder {

	private static Logger LogUtil = LoggerFactory
			.getLogger(MultispeedAudioPacketDecoder.class);

	private EngineRuntime engineRuntime;
	private IAudioSamples samples = null;
	private boolean nextFrame = true;
	protected int multispeedPacketCounter = 0;
	protected int speed = 1;

	public MultispeedAudioPacketDecoder() {
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
				samples = (IAudioSamples) engineRuntime.getAudioSamplePool()
						.borrowObject();
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

			/*
			 * Some decoder will consume data in a packet, but will not be able
			 * to construct a full set of samples yet. Therefore you should
			 * always check if you got a complete set of samples from the
			 * decoder
			 */
			if (samples.isComplete()) {

				int modulo = 0;
				speed = new Double(engineRuntime.getPlaySpeed()).intValue();
				if (speed > 0) {
					modulo = (multispeedPacketCounter++) % speed;
					if (LogUtil.isDebugEnabled()) {
						LogUtil.debug("speed factor is " + speed);
						LogUtil.debug("modulo is " + modulo);
					}
				}

				if (modulo == 0) {
					try {
						engineRuntime.getAudioFrameBuffer().add(samples);
						if (LogUtil.isDebugEnabled())
							LogUtil.debug("---> Decoded an audio frame");
						nextFrame = true;
					} catch (BufferOverflowException e) {
						e.printStackTrace();
					}
				}
			}

			if (packet != null)
				engineRuntime.getPacketPool().returnObject(packet);

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
			if (engineRuntime.getAudioPacketBuffer().isEmpty())
				continue;
			IPacket packet = (IPacket) engineRuntime.getAudioPacketBuffer()
					.remove();
			if (packet != null) {
				decodePacket(packet);
			}
		}

		LogUtil.info("thread shutting down gracefully");
	}

}
