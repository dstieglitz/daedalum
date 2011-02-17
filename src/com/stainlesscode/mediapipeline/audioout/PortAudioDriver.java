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

package com.stainlesscode.mediapipeline.audioout;

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

import com.stainlesscode.mediapipeline.AudioOutput;
import com.stainlesscode.mediapipeline.EngineRuntime;

/**
 * @deprecated use classes from the audioout2 package instead
 * @author Dan Stieglitz
 *
 */
public class PortAudioDriver implements AudioOutput {

	private static Logger LogUtil = LoggerFactory
			.getLogger(PortAudioDriver.class);

	private EngineRuntime engineRuntime;
	private Stream audioOut;
	private AudioFormat audioFormat;
	private int bytesPerFrame = -1;

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

			// audioOut = PortAudio.createStream(configuration, new Callback() {
			// public State callback(ByteBuffer arg0, ByteBuffer arg1) {
			// System.out.println("Callback called");
			// arg1 = arg0;
			// return State.RUNNING;
			// }
			// }, new Runnable() {
			// public void run() {
			// // TODO Auto-generated method stub
			// System.out.println("Runnable called");
			// }
			// });

			audioOut = PortAudio.createStream(configuration, null, null);

			if (LogUtil.isDebugEnabled())
				LogUtil.debug("audioStream is " + audioOut.toString());

			audioOut.start();
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
			if (audioOut!=null) {
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

}
