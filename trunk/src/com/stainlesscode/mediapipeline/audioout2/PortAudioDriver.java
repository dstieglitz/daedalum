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

package com.stainlesscode.mediapipeline.audioout2;

import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;

import org.jpab.PortAudio;
import org.jpab.PortAudioException;
import org.jpab.Stream;
import org.jpab.StreamConfiguration;
import org.jpab.StreamConfiguration.Mode;
import org.jpab.StreamConfiguration.SampleFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput2;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.audioout.AudioDriverException;
import com.xuggle.xuggler.IAudioSamples;

public class PortAudioDriver implements AudioOutput2 {

	private static Logger LogUtil = LoggerFactory
			.getLogger(PortAudioDriver.class);

	private static final int BUFFER_SIZE = 131072;
	private EngineRuntime engineRuntime;
	private Stream audioOut;
	private AudioFormat audioFormat;
	private int bytesPerFrame = -1;
	private long lastPts;

	private PortAudioBuffer buffer;
	private PortAudioWriterThread writer;

	public void init(EngineRuntime runtime, AudioFormat audioFormat) {
		this.engineRuntime = runtime;

		try {
			this.audioFormat = audioFormat;
			PortAudio.initialize();

			StreamConfiguration configuration = PortAudio
					.getDefaultStreamConfiguration(StreamConfiguration.Mode.OUTPUT_ONLY);
			configuration.setSampleRate(audioFormat.getSampleRate());
			configuration.setOutputChannels(audioFormat.getChannels());
			configuration.setMode(Mode.OUTPUT_ONLY);

			int bitDepth = (int) audioFormat.getSampleSizeInBits();
			this.bytesPerFrame = bitDepth * audioFormat.getChannels() / 8;

			// configuration.setOutputFormat(SampleFormat.SIGNED_INTEGER_16);
			configuration.setOutputFormat(getSampleFormat(audioFormat));

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug(PortAudio.getDevices().toString());
				LogUtil.debug("audio bit depth=" + bitDepth);
				LogUtil.debug("audioFormat encoding = "
						+ audioFormat.getEncoding());
				LogUtil.info("PortAudio configuration = " + configuration);
			}

			audioOut = PortAudio.createStream(configuration, null, null);

			if (LogUtil.isDebugEnabled())
				LogUtil.debug("audioStream is " + audioOut.toString());

			audioOut.start();

			buffer = new PortAudioBuffer(BUFFER_SIZE);
			writer = new PortAudioWriterThread(engineRuntime, this, buffer);

			Thread thread = new Thread(writer);
			thread.start();

		} catch (PortAudioException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	private SampleFormat getSampleFormat(AudioFormat audioFormat2) {
		switch (audioFormat.getSampleSizeInBits()) {
		case 8:
			return SampleFormat.UNSIGNED_INTEGER_8;
		case 16:
			return SampleFormat.SIGNED_INTEGER_16;
		case 32:
			return SampleFormat.SIGNED_INTEGER_32;
		default:
			return SampleFormat.SIGNED_INTEGER_16;
		}
	}

	public void close() {
		try {
			if (writer != null) {
				writer.setMarkedForDeath(true);
			}
			if (audioOut != null && !audioOut.isStopped()) {
				audioOut.stop();
				audioOut.close();
			}
			PortAudio.terminate();
		} catch (PortAudioException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void writeByteBuffer(ByteBuffer buf) throws AudioDriverException {
		buf.rewind();
		long start = System.currentTimeMillis();
		try {
			PortAudio.writeStream(audioOut.getId(), buf, buf.capacity());
		} catch (PortAudioException e) {
			throw new AudioDriverException(e);
		}
		long end = System.currentTimeMillis();
		long awl = end - start;
		LogUtil.debug(new Long(awl).toString());
		engineRuntime.getSynchronizer().setAudioWriteLatency(awl * 1000);

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("Audio write took " + (end - start) + " ms");
	}

	public void writeByteBuffer(ByteBuffer buf, int frameCount)
			throws AudioDriverException {
		long start = System.currentTimeMillis();
		buf.rewind();

		try {
			PortAudio.writeStream(audioOut.getId(), buf, frameCount);
		} catch (PortAudioException e) {
			throw new AudioDriverException(e);
		}

		long end = System.currentTimeMillis();
		long awl = end - start;
		LogUtil.debug(new Long(awl).toString());
		engineRuntime.getSynchronizer().setAudioWriteLatency(awl * 1000);

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("wrote " + frameCount + " frames in " + awl + " ms");
	}

	public AudioFormat getAudioFormat() {
		return this.audioFormat;
	}

	public int getBytesPerFrame() {
		return bytesPerFrame;
	}

	public void setBytesPerFrame(int bytesPerFrame) {
		this.bytesPerFrame = bytesPerFrame;
	}

	@Override
	public long getLastPts() {
		return lastPts;
	}

	@Override
	public void setCurrentSamples(IAudioSamples newSamples) {
		this.lastPts = newSamples.getTimeStamp();

		while (buffer.transferReadyFlag) {
			if (LogUtil.isTraceEnabled())
				LogUtil.trace("waiting for transfer to complete");
			Thread.yield();
		}

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("isOKToCache()");

		IAudioSamples samplesToCache = newSamples;

		// if (samplesToCache == null) {
		// // if (audioFrameBuffer.isEmpty()) {
		// // if (LogUtil.isDebugEnabled())
		// // LogUtil
		// // .debug("audio frame buffer seems to be empty");
		// // Thread.yield();
		// // }
		// samplesToCache = newSamples;
		// }

		while (samplesToCache == null) {
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("no audio samples!");
			Thread.yield();
		}

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("o---------> processing audio samples at "
					+ samplesToCache.getTimeStamp());
		}

		if (buffer.smoothingBuffer.remaining() >= samplesToCache.getSize()) {
			buffer.cache(samplesToCache.copyReference());
		} else {
			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("setting transfer ready flag");
				// LogUtil.debug("seekFlag is "+seekFlag);
			}

			// after a seek, we still have a sleep problem here
			// we need to notify the writer thread that we want
			// to cancel all sleeps immediately after a seek

			// if (seekFlag) {
			// actualWriterThread.interrupt();
			// seekFlag = false;
			// }

			buffer.transferReadyFlag = true;
		}
	}

}
