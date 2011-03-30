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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map.Entry;

import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.BufferOverflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.Demultiplexer;
import com.stainlesscode.mediapipeline.EngineConfiguration;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent.Type;
import com.stainlesscode.mediapipeline.util.MediaPlayerEventSupportedEngineThread;
import com.stainlesscode.mediapipeline.util.SeekHelper;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;

public class SimpleDemultiplexer extends MediaPlayerEventSupportedEngineThread
		implements Demultiplexer, MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory
			.getLogger(SimpleDemultiplexer.class);

	protected EngineRuntime engineRuntime;
	protected String url;
	protected SeekHelper seekHelper;

	public void init(String url, EngineRuntime engineRuntime) {
		this.seekHelper = new SeekHelper(engineRuntime);
		this.engineRuntime = engineRuntime;
	}

	@SuppressWarnings("unchecked")
	public void run() {
		while (!isMarkedForDeath()) {
//			if (engineRuntime.isPaused())
//				continue;

			if (LogUtil.isDebugEnabled())
				LogUtil.debug("In demultiplexerLoop()");

			IPacket packet = null;

			try {
				packet = getNextPacket();
			} catch (DemultiplexerException e1) {
				if (e1.getResult() < 0) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("result is " + e1.getResult());
					if (e1.getResult() == -32) {
						fireMediaPlayerEvent(new MediaPlayerEvent(this,
								Type.CLIP_END, null));
					} else {
						IError error = IError.make(e1.getResult());
						LogUtil.error("ERROR: " + error.getDescription());
					}
				} else {
					LogUtil.error(e1.getMessage());
				}
			}

			if (packet != null) {
				try {
					handlePacket(packet);
				} catch (DemultiplexerException e) {
					LogUtil.error(e.getMessage());
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

	@Override
	public IPacket getNextPacket() throws DemultiplexerException {
		int result = 0;
		IPacket packet = null;

		if (engineRuntime.getEngine().getEngineConfiguration()
				.getConfigurationValueAsBoolean(
						EngineConfiguration.USE_OBJECT_POOLS)) {
			try {
				packet = (IPacket) engineRuntime.getPacketPool().borrowObject();
			} catch (NoSuchElementException e) {
				throw new DemultiplexerException(e);
			} catch (IllegalStateException e) {
				throw new DemultiplexerException(e);
			} catch (Exception e) {
				throw new DemultiplexerException(e);
			}
		} else {
			packet = IPacket.make();
		}

		engineRuntime.getContainerLock().lock();
		try {
			result = engineRuntime.getContainer().readNextPacket(packet);
			if (result < 0) {
				throw new DemultiplexerException(result);
			}
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("read packet " + packet.getTimeStamp()
						+ " for stream " + packet.getStreamIndex());
		} finally {
			engineRuntime.getContainerLock().unlock();
		}

		return packet;
	}

	@Override
	public void handlePacket(IPacket packet) throws DemultiplexerException {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("extracting packet to buffer "
					+ packet.getStreamIndex());

		Buffer destinationBuffer = engineRuntime.getStreamToBufferMap().get(
				packet.getStreamIndex());

		if (destinationBuffer == null) {
			LogUtil.debug("No destination configured for packet stream "
					+ packet.getStreamIndex());
			returnBorrowed(packet);
		} else {
			try {
				destinationBuffer.add(packet.copyReference());
				// packet = null;
			} catch (BufferOverflowException e) {
				System.err.println(e.getMessage());
				return;
			}
		}
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		LogUtil.debug("got event "+evt);
		if (evt.getType() == MediaPlayerEvent.Type.SEEK) {
			Iterator<Entry<Integer, Buffer>> i = engineRuntime.getStreamToBufferMap().entrySet().iterator();
			while (i.hasNext()) {
				i.next().getValue().clear();
			}
		}
	}
}
