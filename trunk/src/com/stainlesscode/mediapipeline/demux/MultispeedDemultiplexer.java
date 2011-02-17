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

package com.stainlesscode.mediapipeline.demux;

import java.util.NoSuchElementException;

import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.BufferOverflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.Demultiplexer;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.SeekHelper;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;

/**
 * Drops every nth packet as per the set play speed. This produces some funky
 * effects especially because keyframes can be dropped.
 * 
 * @deprecated
 * @author Dan Stieglitz
 * 
 */
public class MultispeedDemultiplexer extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(MultispeedDemultiplexer.class);

	protected EngineRuntime engineRuntime;
	protected String url;
	protected SeekHelper seekHelper;
	protected IPacket packet = null;
	protected int multispeedPacketCounter = 0;
	protected int speed = 1;

	public void init(String url, EngineRuntime engineRuntime) {
		this.seekHelper = new SeekHelper(engineRuntime);
		this.engineRuntime = engineRuntime;
	}

	public void seek(long microseconds, boolean keyframe) {
		int retcode = seekHelper.seek(microseconds, keyframe);

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("SEEK returned " + retcode);

		if (retcode != 0) {
			IError error = IError.make(retcode);
			throw new RuntimeException(error.getDescription());
		} else {
			// videoPacketBuffer.clear();
			// audioPacketBuffer.clear();
			// engineRuntime.getAudioSync().reset();
			// engineRuntime.getVideoSync().reset();
			// engineRuntime.getPlayer().getVideoPanel().reset();
			// engineRuntime.setAudioSkew(-1);
			// engineRuntime.setVideoSkew(-1);
			// engineRuntime.setStreamTime(microseconds);
		}
	}

	@SuppressWarnings("unchecked")
	public void run() {
		while (!isMarkedForDeath()) {
			if (engineRuntime.isPaused())
				continue;

			if (LogUtil.isDebugEnabled())
				LogUtil.debug("In demultiplexerLoop()");

			int result = -1;

			if (packet == null) {
				try {
					packet = (IPacket) engineRuntime.getPacketPool()
							.borrowObject();
				} catch (NoSuchElementException e) {
					LogUtil.error("Unable to borrow packet for decode");
					continue;
				} catch (IllegalStateException e) {
					LogUtil.error("Unable to borrow packet for decode");
					continue;
				} catch (Exception e) {
					LogUtil.error("Unable to borrow packet for decode");
					continue;
				}

				engineRuntime.getContainerLock().lock();
				try {
					result = engineRuntime.getContainer()
							.readNextPacket(packet);
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("read packet " + packet.getTimeStamp()
								+ " for stream " + packet.getStreamIndex());
				} finally {
					engineRuntime.getContainerLock().unlock();
				}
			} else {
				result = 0;
			}

			if (result >= 0) {
				if (packet != null) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("extracting packet to buffer "
								+ packet.getStreamIndex());
					Buffer destinationBuffer = engineRuntime
							.getStreamToBufferMap()
							.get(packet.getStreamIndex());

					int modulo = 0;
					speed = new Double(engineRuntime.getPlaySpeed()).intValue();
					if (speed > 0) {
						modulo = (multispeedPacketCounter++) % speed;
						if (LogUtil.isDebugEnabled()) {
							LogUtil.debug("speed factor is " + speed);
							LogUtil.debug("modulo is " + modulo);
						}
					}

					if (destinationBuffer != null && modulo == 0) {
						try {
							destinationBuffer.add(packet.copyReference());
							packet = null;
						} catch (BufferOverflowException e) {
							return;
						}
					} else {
						packet = null;
						return;
					}
				}
			} else {
				LogUtil.error("result is " + result);
				if (result == -32) {
					// System.out.println("CLIP_END detected, firing event");
					// engineRuntime.getPlayer().fireMediaPlayerEvent(
					// new MediaPlayerEvent(engineRuntime.getPlayer(),
					// MediaPlayerEvent.CLIP_END));
					setMarkedForDeath(true);
				}
				IError error = IError.make(result);
				LogUtil.error("ERROR: " + error.getDescription());
			}
		}

		LogUtil.info("thread shutting down gracefully");
	}
}
