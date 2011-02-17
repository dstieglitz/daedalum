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
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput;
import com.stainlesscode.mediapipeline.EngineRuntime;

/**
 * @deprecated use classes from the audioout2 package instead
 * @author Dan Stieglitz
 *
 */
public class JavaSoundAudioDriver implements AudioOutput {

	private static Logger LogUtil = LoggerFactory
			.getLogger(JavaSoundAudioDriver.class);

	private byte[] buffer;
	private SourceDataLine mLine;
	private int bytesPerFrame;
	private AudioFormat audioFormat;

	public void init(EngineRuntime runtime, AudioFormat audioFormat) {
		this.audioFormat = audioFormat;
		System.out.println("-------------------> Audio initialized with "
				+ this.audioFormat);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class,
				audioFormat);
		this.bytesPerFrame = audioFormat.getFrameSize();
		try {
			mLine = (SourceDataLine) AudioSystem.getLine(info);
			mLine.open(audioFormat);
			mLine.start();
		} catch (LineUnavailableException e) {
			throw new RuntimeException("could not open audio line");
		}
	}

	public void writeByteBuffer(ByteBuffer buf) throws AudioDriverException {
		try {
			System.out.println(Thread.currentThread().getName() + " writing "
					+ buf.capacity() + " bytes to audio...");
			if (buffer == null) {
				buffer = new byte[1];
				mLine.open(audioFormat);
				mLine.start();
			}
			// buf.get(buffer);
			int bytes = mLine.write(buf.array(), 0, buffer.length);
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("wrote " + bytes + " bytes");
		} catch (Throwable t) {
			throw new AudioDriverException(t);
		}
	}

	public void writeByteBuffer(ByteBuffer buf, int frameCount)
			throws AudioDriverException {
		try {
			buf.rewind();

			int bytes = frameCount * bytesPerFrame;

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug(mLine.available() + " available");
				// LogUtil.debug("Calling SourceDataLine.write with buffer " +
				// buf
				// + " to stream " + mLine + " for " + frameCount
				// + " frames");
				// }

				if (bytes > mLine.available()) {
					LogUtil
							.warn("WARNING: Buffer to write is larger than available native audio buffer");
					return;
				}

				if (buffer == null) {
					buffer = new byte[bytes];
					buf.get(buffer);
				}

				// TODO might need to thread this
				int bytesWritten = 0;
				while (bytesWritten < bytes) {
					if (mLine.available() > 0) {
						int bytesToWrite = mLine.available();

						if (bytesToWrite > (bytes - bytesWritten))
							bytesToWrite = bytes - bytesWritten;

						if (LogUtil.isDebugEnabled())
							LogUtil.debug("---> "
									+ Thread.currentThread().getName()
									+ " writing " + bytesToWrite
									+ " bytes to audio");

						bytesWritten += mLine.write(buffer, bytesWritten,
								bytesToWrite);

						if (LogUtil.isDebugEnabled())
							LogUtil.debug("wrote " + bytesWritten + " bytes");
					} else {
						// System.out.println("Can't write to audio; no space available in buffer");
					}
				}

				// mLine.drain();
			}
		} catch (Throwable t) {
			throw new AudioDriverException(t);
		}
	}

	public AudioFormat getAudioFormat() {
		return this.audioFormat;
	}

	protected void finalize() {
		mLine.drain();
		mLine.close();
	}

	public int getBytesPerFrame() {
		return bytesPerFrame;
	}

	public void close() {
		// TODO Auto-generated method stub

	}

}
