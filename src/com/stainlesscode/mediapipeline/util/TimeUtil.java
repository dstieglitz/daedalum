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

package com.stainlesscode.mediapipeline.util;

import javax.sound.sampled.AudioFormat;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IRational;

public class TimeUtil {

	private static final int MICROSECONDS_PER_SECOND = 1000000;

	private static String td(int num) {
		if (num < 10)
			return "0" + num;
		else
			return "" + num;
	}

	public static String getTimecode(double frameRate,
			long streamTimeInMicroseconds) {
		long l_seconds = streamTimeInMicroseconds / 1000000;
		int hours = (int) Math.floor(l_seconds / 3600 % 60);
		int minutes = (int) Math.floor(l_seconds / 60 % 60);
		int seconds = (int) (l_seconds % 60);
		int t_frames = (int) ((streamTimeInMicroseconds / 1000) / ((1d / frameRate) * 1000));
		int frames = (int) (t_frames % frameRate);
		return td(hours) + ":" + td(minutes) + ":" + td(seconds) + ":"
				+ td(frames);
	}

	/**
	 * Returns the number of milliseconds of audio that will play based on the
	 * number of bytes and the audio format
	 * 
	 * @param format
	 * @param bytes
	 * @return
	 */
	public static int audioBytesToMillis(AudioFormat format, int bytes) {
		double samplesPerSecond = format.getSampleRate();
		int bytesPerSample = format.getSampleSizeInBits() / 8;
		int channels = format.getChannels();
		int bytesPerSecond = (int) (bytesPerSample * samplesPerSecond)
				* channels;

		int millis = (int) ((1.0d / bytesPerSecond) * bytes * 1000.0d);

		return millis;
	}

	public static int millisToAudioFrames(AudioFormat format, long millis) {
		double samplesPerSecond = format.getSampleRate();
		int bytesPerSample = format.getSampleSizeInBits() / 8;
		int channels = format.getChannels();
		int bytesPerFrame = format.getFrameSize();

		int samples = (int) ((int) (samplesPerSecond / 1000.0d) * millis);
		int bytes = samples * bytesPerSample * channels;
		int frames = bytes / bytesPerFrame;

		return frames;
	}

	@SuppressWarnings("unused")
	public static long getMinimumAudioInterframeDelay(AudioFormat format) {
		double rate = format.getSampleRate();
		double bitDepth = format.getSampleSizeInBits();
		int channels = format.getChannels();
		throw new UnsupportedOperationException(
				"getMinimumAudioInterframeDelay is not supported");
	}

	public static String microsecondsToReadableTime(long microseconds) {
		long l_seconds = microseconds / MICROSECONDS_PER_SECOND;
		int hours = (int) Math.floor(l_seconds / 3600 % 60);
		int minutes = (int) Math.floor(l_seconds / 60 % 60);
		int seconds = (int) (l_seconds % 60);
		return td(hours) + ":" + td(minutes) + ":" + td(seconds);
	}

	public static long microsecondsToFrames(double frameRate, long microseconds) {
		return (long) ((microseconds / 1000) / ((1d / frameRate) * 1000));
	}

	public static long microsecondsToTimeBaseUnits(IRational timebase,
			long microseconds) {
		int seconds = Math.round(microseconds / MICROSECONDS_PER_SECOND);
		long timeInTimeBaseUnits = seconds * timebase.getDenominator();
		return timeInTimeBaseUnits;
	}

	public static long videoFramesToMicroseconds(double frameRate, int frames) {
		double timePerFrame = (1.0d / frameRate);
		return Math.round(timePerFrame * frames * 1000);
	}

	public static long iAudioSamplesTimeInMicroseconds(AudioFormat format,
			IAudioSamples samples) {
		return audioBytesToMillis(format, (int) ((samples.getSize()
				/ samples.getChannels() * (samples.getSampleBitDepth() / 8)))) * 1000;
	}

}
