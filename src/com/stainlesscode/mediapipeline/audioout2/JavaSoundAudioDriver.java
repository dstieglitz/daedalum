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
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.AudioOutput2;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IAudioSamples;

/**
 * @author Dan Stieglitz
 * 
 */
public class JavaSoundAudioDriver implements AudioOutput2,
		MediaPlayerEventListener {

	private SourceDataLine line = null;
	private AudioFormat format;
	private EngineRuntime engineRuntime;
	private AudioBuffer buf;
	private int bufferSizeInFrames = 48000;
	// private long firstTimestampInStream = -1;
	private long chunkTime = 60;
	private byte[] bbuf = null;

	private static Logger LogUtil = LoggerFactory
			.getLogger(JavaSoundAudioDriver.class);

	@Override
	public void close() {
		// firstTimestampInStream = -1;
		buf.clear();
		line.close();
	}

	@Override
	public AudioFormat getAudioFormat() {
		return format;
	}

	@Override
	public long getLastPts() {
		return buf.getStartTimestampMillis() * 1000;
	}

	@Override
	public void init(EngineRuntime engineRuntime, final AudioFormat format) {
		this.engineRuntime = engineRuntime;
		this.format = format;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		buf = new AudioBuffer(format, bufferSizeInFrames
				* format.getFrameSize(), true);

		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("frameSize=" + format.getFrameSize());
			LogUtil.debug("buf.size=" + buf.getSize());
			LogUtil.debug("bufTime=" + buf.getBufferTime());
		}

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
				while (true) {
					if (JavaSoundAudioDriver.this.engineRuntime.isPaused())
						continue;

					if (JavaSoundAudioDriver.this.engineRuntime
							.getSynchronizer() != null) {

						long epts = buf.getStartTimestampMillis() * 1000;
						long vpts = JavaSoundAudioDriver.this.engineRuntime
								.getSynchronizer().getStreamTime();

						if (LogUtil.isDebugEnabled()) {
							LogUtil.debug("vpts=" + vpts);
							LogUtil.debug("epts=" + epts);
						}

						// if diff < 0 the audio is ahead
						// else the video is ahead

						try {
							if (epts >= vpts) {
								if (LogUtil.isDebugEnabled()) {
									// LogUtil.debug("vpts=" + vpts);
									// LogUtil.debug("epts=" + epts);
									// LogUtil.debug("audio ahead by "
									// + (epts - vpts) / 1000l + "ms");
								}
								continue;
							} else if (epts < (vpts - (chunkTime * 1000l))) {
								long skipMillis = ((vpts - epts) / 1000l)
										- chunkTime;

								if (LogUtil.isDebugEnabled()) {
									LogUtil.debug("++++++++++++> SKIPPING "
											+ skipMillis + " ms");
								}

								if (skipMillis > 0) {
									int skipped = buf.skipMillis(skipMillis);

									if (LogUtil.isDebugEnabled()) {
										LogUtil.debug("actually skipped "
												+ TimeUtil.audioBytesToMillis(
														format, skipped));
									}
								}
							}

							bbuf = buf.readMillis(chunkTime);

						} catch (IOException e) {
							e.printStackTrace();
						}

						while (!line.isOpen()) {
							Thread.yield();
						}

						// while (line.isActive()) {
						// Thread.yield();
						// }

						// XXX reset the stream time to the audio's stream
						// time, and don't sleep (audio stream drives clock)
						// sleepTime = 0;
						// ((MultispeedVptsSynchronizer)
						// JavaSoundAudioDriver.this.engineRuntime
						// .getSynchronizer()).setStreamTimeZero(epts
						// - chunkTime, true);

						if (bbuf.length > 0) {
							if (LogUtil.isDebugEnabled()) {
								LogUtil.debug("writing " + bbuf.length);
							}

							// long start = System.currentTimeMillis();
							line.write(bbuf, 0, bbuf.length);
							// long end = System.currentTimeMillis();

							// audioWriteLatency = (end - start);

							// if (LogUtil.isDebugEnabled()) {
							// LogUtil.debug("audioWriteLatency="
							// + audioWriteLatency);
							// }
						}
					}
				}
			}
		});

		thread.start();
	}

	@Override
	public void setCurrentSamples(IAudioSamples samples) {
		byte[] arr = new byte[samples.getSize()];
		samples.get(0, arr, 0, arr.length);

		if (buf.getStartTimestampMillis() < 0) {
			buf.setStartTimestampMillis(samples.getTimeStamp() / 1000);
			buf.setEndTimestampMillis(samples.getTimeStamp() / 1000);
		}

		// XXX this is done in the decoder
		// if (!((MultispeedVptsSynchronizer)
		// engineRuntime.getSynchronizer())
		// .isStreamTimeZeroSet()) {
		// if (LogUtil.isDebugEnabled())
		// LogUtil.debug("setting streamTimeZero to "
		// + firstTimestampInStream);
		// ((MultispeedVptsSynchronizer) engineRuntime.getSynchronizer())
		// .setStreamTimeZero(firstTimestampInStream, false);
		// }
		// }

		// long timeAlreadyCached = buf.getCachedAudioTime();
		// long aboutToCachePts = samples.getTimeStamp();

		// this.lastPts = samples.getTimeStamp();

		try {
			// LogUtil.debug("++++++++++ CACHE FROM STREAM +++++++++++");
			// LogUtil.debug(timeAlreadyCached +
			// "ms waiting to be played");
			// LogUtil.debug("caching " + samples.getSize() + " bytes");
			// LogUtil.debug("caching " + samples.getTimeStamp() +
			// " (pts)");
			// LogUtil.debug("caching "
			// + TimeUtil.audioBytesToMillis(this.format, arr.length)
			// + "ms");
			// LogUtil.debug("cache space left=" + buf.getSpaceLeft());
			// LogUtil.debug("+++++++++++++++++++++++++++++++++++++++");

			// long start = System.currentTimeMillis();
			buf.write(arr);
			// long end = System.currentTimeMillis();
			// LogUtil.debug("cache took " + (end - start) + " ms");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (LogUtil.isDebugEnabled()) {
			LogUtil.debug("got event " + evt);
		}

		if (evt.getType() == MediaPlayerEvent.Type.SEEK) {
			buf.clear();
		}
	}

}
