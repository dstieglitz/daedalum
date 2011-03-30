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

import java.io.IOException;

import javax.sound.sampled.AudioFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.Ostermiller.util.CircularByteBuffer;
import com.stainlesscode.mediapipeline.util.TimeUtil;

/**
 * A decorated byte buffer wrapping audio data, with methods included that allow
 * the retrieval or skipping of audio data by time to facilitate A/V sync.
 * 
 * @author Dan Stieglitz
 * 
 */
public class AudioBuffer {

	private CircularByteBuffer underlyingBuffer;
	private long bufferTime;
	private AudioFormat format;
	private long startTimestampMillis = -1;
	private long endTimestampMillis = -1;

	private static Logger LogUtil = LoggerFactory.getLogger(AudioBuffer.class);

	public AudioBuffer(AudioFormat format, int size, boolean blockingWrite) {
		this.format = format;
		this.underlyingBuffer = new CircularByteBuffer(size, blockingWrite);
		this.setBufferTime(TimeUtil.audioBytesToMillis(format, underlyingBuffer
				.getSpaceLeft()));
	}

	public void clear() {
		underlyingBuffer.clear();
		startTimestampMillis = 0;
		endTimestampMillis = 0;
		bufferTime = 0;
	}

	public boolean equals(Object obj) {
		return underlyingBuffer.equals(obj);
	}

	public int getAvailable() {
		return underlyingBuffer.getAvailable();
	}

	// public InputStream getInputStream() {
	// return underlyingBuffer.getInputStream();
	// }
	//
	// public OutputStream getOutputStream() {
	// return underlyingBuffer.getOutputStream();
	// }

	public void write(byte[] b) throws IOException {
		long audioTime = TimeUtil.audioBytesToMillis(format, b.length);
		endTimestampMillis += audioTime;
		underlyingBuffer.getOutputStream().write(b);
	}

	/**
	 * Returns the next available audio data up to the specified length. If
	 * millis > the amount of audio data available, all of the audio data is
	 * returned. This method will always return an integral number of frames.
	 * 
	 * @param millis
	 * @return
	 * @throws IOException
	 */
	public byte[] readMillis(long millis) throws IOException {
		int len = TimeUtil.millisToAudioFrames(format, millis)
				* format.getFrameSize();
		
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("len=" + len);
		
		if (len > underlyingBuffer.getAvailable()
				|| len % format.getFrameSize() != 0) {
			// grab an integral number of frames from the buffer
			if (underlyingBuffer.getAvailable() % format.getFrameSize() == 0) {
				len = underlyingBuffer.getAvailable();
			} else {
				len = underlyingBuffer.getAvailable()
						- (underlyingBuffer.getAvailable() % format
								.getFrameSize());
			}
		}
		byte[] data = new byte[len];
		int read = underlyingBuffer.getInputStream().read(data);
		
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("read from underlyingBuffer=" + read + " (len=" + len
					+ ")");
		
		startTimestampMillis += TimeUtil.audioBytesToMillis(format, len);
		return data;
	}

	/**
	 * Discard a segment from the audio buffer of the specified time duration in
	 * milliseconds. If the time duration is greater than the size of the buffer
	 * this method effectively functions identically to the clear() method. This
	 * method returns the actual number of millis skipped.
	 * 
	 * @param millis
	 * @throws IOException
	 */
	public int skipMillis(long millis) throws IOException {
		return readMillis(millis).length;
	}

	public int getSize() {
		return underlyingBuffer.getSize();
	}

	public int getSpaceLeft() {
		return underlyingBuffer.getSpaceLeft();
	}

	public int hashCode() {
		return underlyingBuffer.hashCode();
	}

	public String toString() {
		return underlyingBuffer.toString();
	}

	private void setBufferTime(long bufferTime) {
		this.bufferTime = bufferTime;
	}

	public long getBufferTime() {
		return bufferTime;
	}

	public long getCachedAudioTime() {
		return TimeUtil.audioBytesToMillis(format, underlyingBuffer
				.getAvailable());
	}

	public void setStartTimestampMillis(long startTimestamp) {
		this.startTimestampMillis = startTimestamp;
	}

	public long getStartTimestampMillis() {
		return startTimestampMillis;
	}

	public void setEndTimestampMillis(long endTimestamp) {
		this.endTimestampMillis = endTimestamp;
	}

	public long getEndTimestampMillis() {
		return endTimestampMillis;
	}

}
