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

import javax.sound.sampled.AudioFormat;

import com.stainlesscode.mediapipeline.AudioOutput2;
import com.stainlesscode.mediapipeline.EngineRuntime;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.IAudioSamples;

/**
 * @deprecated experimental
 * @author Dan Stieglitz
 *
 */
public class ResamplingAudioPlayer extends DefaultAudioPlayer {
	
	public ResamplingAudioPlayer(AudioOutput2 output, EngineRuntime runtime) {
		super(output, runtime);
	}

	/**
	 * @deprecated EXPERIMENTAL
	 * @param samples
	 * @return
	 */
	private IAudioSamples resample(IAudioSamples samples) {
		// resample?
		long epts = engineRuntime.getSynchronizer().getStreamTime();
		long pts = samples.getTimeStamp();
		long diff = pts - epts;
		AudioFormat fmt = audioOutput.getAudioFormat();
		float sr = fmt.getSampleRate();

		long sampleLengthMicroseconds = TimeUtil.audioBytesToMillis(audioOutput
				.getAudioFormat(), samples.getSize()) * 1000;

		long sampleCount = samples.getNumSamples();
		long totalNewSamples = (long) (sampleCount + ((float) diff / 1000000.0f)
				* sr);
		int newRate = (int) (sr * totalNewSamples / sampleCount);

		// System.out.println("total new samples " + totalNewSamples);
		// System.out.println("new rate is "+newRate);
		// System.out.println(samples.getFormat());

		IAudioResampler rs = IAudioResampler.make(2, 2, 44100, 44100);
		int num = rs.getMinimumNumSamplesRequiredInOutputSamples(1024);
		// System.out.println(num);
		IAudioSamples outputSamples = IAudioSamples.make(num, 2);
		int resampled = rs.resample(outputSamples, samples, 0);
		// System.out.println(resampled);

		// System.out.println("sample length is "
		// + sampleLengthMicroseconds);
		// System.out.println("sample rate is " + sr);
		// System.out.println("skew is " + diff);
		// System.out.println("new sample rate would be " + newRate);
		//
		// System.out.println("sample count is " + sampleCount);
		// System.out.println("sample diff is "
		// + (((float) diff / 1000000.0f) * sr));

		return outputSamples;
	}

}
