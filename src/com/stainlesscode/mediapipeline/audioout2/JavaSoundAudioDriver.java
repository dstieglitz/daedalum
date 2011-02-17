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
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput2;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.Ostermiller.util.CircularByteBuffer;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IAudioSamples;

/**
 * @author Dan Stieglitz
 * 
 */
public class JavaSoundAudioDriver implements AudioOutput2 {

	private static final int BUFFER_SIZE_IN_BYTES = 131072;

	private SourceDataLine line = null;
	private AudioFormat format;
	private long lastPts;
	private EngineRuntime engineRuntime;
	private CircularByteBuffer buf;
	// private long audioWriteLatency;
	private byte[] bbuf;
	private int bufferSizeInFrames;
	private int off;
	private int len;

	private static Logger LogUtil = LoggerFactory
			.getLogger(JavaSoundAudioDriver.class);

	@Override
	public void close() {
		line.close();
	}

	@Override
	public AudioFormat getAudioFormat() {
		return format;
	}

	@Override
	public long getLastPts() {
		return lastPts;
	}

	@Override
	public void init(EngineRuntime engineRuntime, AudioFormat format) {
		this.engineRuntime = engineRuntime;
		this.format = format;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		buf = new CircularByteBuffer(BUFFER_SIZE_IN_BYTES, true);
		bufferSizeInFrames = BUFFER_SIZE_IN_BYTES / format.getFrameSize();

		try {
			line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();
		} catch (LineUnavailableException e) {
			throw new RuntimeException(e);
		}

		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				// FIXME BAD
				while (true) {
					if (JavaSoundAudioDriver.this.engineRuntime.isPaused())
						continue;

					if (JavaSoundAudioDriver.this.engineRuntime
							.getSynchronizer() != null) {

						System.out.println("===> play thread");

						long streamTime = JavaSoundAudioDriver.this.engineRuntime
								.getSynchronizer().getStreamTime();

						long diff = JavaSoundAudioDriver.this.lastPts
								- streamTime;

						// FIXME calculate actual play start time
						// based on index in buffer
						long sleepTime = diff / 1000;

						// long start = System.currentTimeMillis();

						if (bbuf == null) {
							bbuf = new byte[(bufferSizeInFrames / 2)
									* JavaSoundAudioDriver.this.format
											.getFrameSize()];
						}

						// JavaSoundAudioDriver.this.bbuf = new
						// byte[line.available()];

						System.out
								.println("line available=" + line.available());
						System.out.println("available in buffer="
								+ buf.getAvailable());
						System.out.println("buf space left="
								+ buf.getSpaceLeft());

						// while (buf.getAvailable() < bbuf.length) {
						// Thread.yield();
						// }

						len = buf.getAvailable() > bbuf.length ? bbuf.length
								: buf.getAvailable();

						System.out.println("transferring to bbuf: " + len);

						try {
							System.out.println("off=" + off + " len=" + len);
							buf.getInputStream().read(bbuf, off, len);
							// if (len > 0)
							// off += (len - 1);
							// if (off == BUFFER_SIZE_IN_BYTES)
							// off = 0;
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						System.out.println("available in buffer="
								+ buf.getAvailable());
						System.out.println("buf space left="
								+ buf.getSpaceLeft());

						while (!line.isOpen()) {
							// System.out.println("Line not open");
							Thread.yield();
						}

						while (line.isActive()) {
							// System.out.println("Line is busy");
							Thread.yield();
						}

						if (len > 0) {
							long start = System.currentTimeMillis();
							// line.start();
							line.write(bbuf, 0, len);
							// line.drain();
							// line.stop();

							if (sleepTime > 0) {
								try {
									System.out.println(sleepTime);
									Thread.sleep(0);
								} catch (InterruptedException e) {

								}
							}
							long end = System.currentTimeMillis();
							long audioWriteLatency = end - start;
							System.out.println("audioWriteLatency="
									+ audioWriteLatency);
						}

						System.out.println("<====== end play segment");
					}
				}
			}
		});

		// thread.start();

	}

	@Override
	public void setCurrentSamples(IAudioSamples samples) {
		byte[] arr = new byte[samples.getSize()];
		samples.get(0, arr, 0, arr.length);
		//
		// long timeAlreadyCached = TimeUtil.audioBytesToMillis(this.format, buf
		// .getAvailable());
		// System.out.println(timeAlreadyCached + "ms waiting to be played");
		// long aboutToCachePts = samples.getTimeStamp();
		//
		// this.lastPts = samples.getTimeStamp();
		//
		// try {
		// System.out.println("caching " + samples.getSize() + " bytes");
		// System.out.println("caching " + samples.getTimeStamp() + " (pts)");
		// System.out.println("caching "
		// + TimeUtil.audioBytesToMillis(this.format, arr.length)
		// + "ms");
		// long start = System.currentTimeMillis();
		// buf.getOutputStream().write(arr);
		// long end = System.currentTimeMillis();
		// System.out.println("cache took " + (end - start) + " ms");
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		if (engineRuntime.getSynchronizer() != null
				&& engineRuntime.getSynchronizer().getStreamTime() > 0
				&& !engineRuntime.isPaused()) {

			if (samples.getTimeStamp() > engineRuntime.getSynchronizer()
					.getStreamTime()) {
				long diff = (samples.getTimeStamp() - engineRuntime
						.getSynchronizer().getStreamTime()) / 1000;

				long time = TimeUtil.audioBytesToMillis(format, arr.length);
				System.out.println("time=" + time + " diff=" + diff);

				if (diff > 500) {

					// double the size
					byte[] origArray = arr;
					byte[] interpolatedSamples = new byte[origArray.length * 2];
					samples.get(0, origArray, 0, origArray.length);
					int frameSize = format.getFrameSize();
					int frameCount = origArray.length / frameSize;
					int sampleSizeInBytes = format.getSampleSizeInBits() / 8;

					for (int frame = 0; frame < frameCount; frame++) {
						int idx = frame * frameSize * 2;
						int originalFrameIndex = frame * frameSize;
						interpolatedSamples[idx] = origArray[originalFrameIndex]; // LL
						interpolatedSamples[idx + 1] = origArray[originalFrameIndex + 1]; // LH
						interpolatedSamples[idx + 2] = origArray[originalFrameIndex + 2]; // RL
						interpolatedSamples[idx + 3] = origArray[originalFrameIndex + 3]; // RH
						interpolatedSamples[idx + 4] = origArray[originalFrameIndex];
						interpolatedSamples[idx + 5] = origArray[originalFrameIndex + 1];
						interpolatedSamples[idx + 6] = origArray[originalFrameIndex + 2];
						interpolatedSamples[idx + 7] = origArray[originalFrameIndex + 3];
					}

					// if (diff > 0) {
					// try {
					// Thread.sleep(diff);
					// } catch (InterruptedException e) {
					//
					// }
					// }

					line.write(interpolatedSamples, 0,
							interpolatedSamples.length);
				} else {
					line.write(arr, 0, arr.length);
				}
			}
		}
	}

}
