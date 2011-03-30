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
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IVideoPicture;

/**
 * This thread runs on the decode layer. It removes packets from the video
 * packet buffer and decodes whole frames. The decoded frames are placed on a
 * frame buffer for display or output processing.
 * 
 * @author Dan Stieglitz
 * 
 */
public class DefaultVideoPacketDecoder extends EngineThread implements
		PacketDecoder {

	private static Logger LogUtil = LoggerFactory
			.getLogger(DefaultVideoPacketDecoder.class);
	private EngineRuntime engineRuntime;
	private IVideoPicture picture = null;
	private boolean firstTimestamp = true;

	public DefaultVideoPacketDecoder() {
	}

	public void init(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
	}

	public void decodePacket(IPacket packet) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("decode video packet " + packet.getTimeStamp());

		try {
			if (picture == null) {
				if (engineRuntime.getEngine().getEngineConfiguration()
						.getConfigurationValueAsBoolean(
								EngineConfiguration.USE_OBJECT_POOLS)) {
					picture = (IVideoPicture) engineRuntime.getRawPicturePool()
							.borrowObject();
				} else {
					picture = IVideoPicture.make(engineRuntime.getVideoCoder()
							.getPixelType(), engineRuntime.getVideoCoder()
							.getWidth(), engineRuntime.getVideoCoder()
							.getHeight());
				}
			}

			int offset = 0;
			while (offset < packet.getSize()) {
				int bytesDecoded = engineRuntime.getVideoCoder().decodeVideo(
						picture, packet, offset);

				if (bytesDecoded < 0)
					throw new RuntimeException("got error decoding video "
							+ bytesDecoded);
				offset += bytesDecoded;
			}

			returnBorrowed(packet);

			if (picture.isComplete()) {
				try {

					if (firstTimestamp) {
						LogUtil.info("First video PTS is "
								+ picture.getTimeStamp());
						firstTimestamp = false;
					}

					// XXX video drives sync with this code.
//					if (!((MultispeedVptsSynchronizer) engineRuntime
//							.getSynchronizer()).isStreamTimeZeroSet()) {
//
//						((MultispeedVptsSynchronizer) engineRuntime
//								.getSynchronizer()).setStreamTimeZero(picture
//								.getTimeStamp(), true);
//					}

					resampleAndCache(picture);
					if (engineRuntime.getEngine().getEngineConfiguration()
							.getConfigurationValueAsBoolean(
									EngineConfiguration.USE_OBJECT_POOLS)) {
						engineRuntime.getRawPicturePool().returnObject(picture);
					}
					picture = null;
					return;
				} catch (BufferOverflowException e) {
					return;
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

	@SuppressWarnings("unchecked")
	private void resampleAndCache(IVideoPicture picture2)
			throws NoSuchElementException, IllegalStateException, Exception {
		IVideoPicture newPic = null;
		/*
		 * If the resampler is not null, that means we didn't get the video in
		 * BGR24 format and need to convert it into BGR24 format.
		 */
		if (engineRuntime.getResampler() != null) {
			// we must resample

			if (engineRuntime.getEngine().getEngineConfiguration()
					.getConfigurationValueAsBoolean(
							EngineConfiguration.USE_OBJECT_POOLS)) {
				newPic = (IVideoPicture) engineRuntime
						.getResampledPicturePool().borrowObject();
			} else {
				newPic = IVideoPicture.make(engineRuntime.getResampler()
						.getOutputPixelFormat(), engineRuntime.getResampler()
						.getOutputWidth(), engineRuntime.getResampler()
						.getOutputHeight());
			}

			int errno = engineRuntime.getResampler().resample(newPic, picture);
			if (errno < 0) {
				IError err = IError.make(errno);
				throw new RuntimeException("could not resample video: "
						+ err.getDescription());
			}
		}

		if (newPic.getPixelType() != IPixelFormat.Type.BGR24)
			throw new RuntimeException("could not decode video"
					+ " as BGR 24 bit data");

		try {
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("$$DECODE VIDEO " + picture.getTimeStamp());
			engineRuntime.getVideoFrameBuffer().add(newPic.copyReference());
		} catch (BufferOverflowException e) {
			throw e;
		}

		// borrowed newPic is returned from the VideoOutput implementation
	}

	@Override
	public void run() {
		while (!isMarkedForDeath()) {
			if (engineRuntime.getVideoPacketBuffer().isEmpty()) {
				if (clipEnded)
					setMarkedForDeath(true);
				else
					continue;
			}

			IPacket packet = (IPacket) engineRuntime.getVideoPacketBuffer()
					.remove();
			if (packet != null) {
				decodePacket(packet);
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
