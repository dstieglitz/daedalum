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

import java.util.HashMap;
import java.util.Map;

import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IConfigurable;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IProperty;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

public class MetadataUtil {
	
	@SuppressWarnings("unused")
	public static Map<String, Object> getMetaData(IContainer container) {
		String file = container.getURL();
		if (container.queryStreamMetaData() < 0)
			throw new IllegalStateException(
					"couldn't query stream meta data for some reason...");

		Map<String, Object> meta = new HashMap<String,Object>();

		for (int i = 0; i < container.getNumProperties(); i++) {
			IProperty prop = container.getPropertyMetaData(i);
			String val = container.getPropertyAsString(prop.getName());
//			System.out.println(prop.getHelp() + " (" + prop.getName() + "): \t"
//					+ val);
			meta.put("container." + prop.getName(), val);
		}

		// query how many streams the call to open found
		int numStreams = container.getNumStreams();
		// log.info("file \"" + file + "\": " + numStreams + " stream" +
		// (numStreams == 1 ? "" : "s"));
//		System.out.printf("file \"%s\": %d stream%s; ", file, numStreams,
//				numStreams == 1 ? "" : "s");
		meta.put("numStreams", numStreams);

//		System.out.printf("duration (ms): %s; ",
//				container.getDuration() == Global.NO_PTS ? "unknown" : ""
//						+ container.getDuration() / 1000);
		meta.put("duration", container.getDuration());

//		System.out.printf("start time (ms): %s; ",
//				container.getStartTime() == Global.NO_PTS ? "unknown" : ""
//						+ container.getStartTime() / 1000);
		meta.put("startTime", container.getStartTime());

//		System.out.printf("file size (bytes): %d; ", container.getFileSize());
		meta.put("fileSize", container.getFileSize());
//
//		System.out.printf("bit rate: %d; ", container.getBitRate());
		meta.put("bitRate", container.getBitRate());

//		System.out.printf("\n");

		// and iterate through the streams to print their meta data
		for (int i = 0; i < numStreams; i++) {
			// Find the stream object
			IStream stream = container.getStream(i);
			// Get the pre-configured decoder that can decode this stream;
			IStreamCoder coder = stream.getStreamCoder();

			// and now print out the meta data.
//			System.out.printf("stream %d: ", i);
//			System.out.printf("type: %s; ", coder.getCodecType());
			meta.put("stream." + i + ".type", coder.getCodecType());

//			System.out.printf("codec: %s; ", coder.getCodecID());
//			System.out.printf("duration: %s; ",
//					stream.getDuration() == Global.NO_PTS ? "unknown" : ""
//							+ stream.getDuration());
			meta.put("stream." + i + ".duration", stream.getDuration());

//			System.out.printf("start time: %s; ",
//					container.getStartTime() == Global.NO_PTS ? "unknown" : ""
//							+ stream.getStartTime());
//			System.out.printf("language: %s; ",
//					stream.getLanguage() == null ? "unknown" : stream
//							.getLanguage());
//			System.out.printf("timebase: %d/%d; ", stream.getTimeBase()
//					.getNumerator(), stream.getTimeBase().getDenominator());
			meta.put("stream." + i + ".timeBase", stream.getTimeBase()
					.getNumerator()
					+ "/" + stream.getTimeBase().getDenominator());

//			System.out.printf("coder tb: %d/%d; ", coder.getTimeBase()
//					.getNumerator(), coder.getTimeBase().getDenominator());

			if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
//				System.out.printf("sample rate: %d; ", coder.getSampleRate());
//				System.out.printf("channels: %d; ", coder.getChannels());
//				System.out.printf("format: %s", coder.getSampleFormat());
			} else if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
//				System.out.printf("width: %d; ", coder.getWidth());
//				System.out.printf("height: %d; ", coder.getHeight());
				meta.put("width", coder.getWidth());
				meta.put("height", coder.getHeight());
//				System.out.printf("format: %s; ", coder.getPixelType());
//				System.out.printf("frame-rate: %5.2f; ", coder.getFrameRate()
//						.getDouble());
			}
//			System.out.printf("\n");
		}

		// If the user passes -Dxuggle.options, then we print
		// out all possible options as well.
//		String optionString = System.getProperty("xuggle.options");
//		if (optionString != null)
//			printOptions(container);
		
		return meta;
	}

	/**
	 * This method iterates through the container, and prints out available
	 * options for the container, and each stream.
	 * 
	 * @param aContainer
	 *            Container to print options for.
	 */
	@SuppressWarnings("unused")
	private static void printOptions(IContainer aContainer) {

		System.out.printf("\n");
		System.out.printf("IContainer Options:\n");
		int numOptions = aContainer.getNumProperties();

		for (int i = 0; i < numOptions; i++) {
			IProperty prop = aContainer.getPropertyMetaData(i);
			printOption(aContainer, prop);
		}
		System.out.printf("\n");

		int numStreams = aContainer.getNumStreams();
		for (int i = 0; i < numStreams; i++) {
			IStreamCoder coder = aContainer.getStream(i).getStreamCoder();
			System.out.printf(
					"IStreamCoder options for Stream %d of type %s:\n", i,
					coder.getCodecType());
			numOptions = coder.getNumProperties();
			for (int j = 0; j < numOptions; j++) {
				IProperty prop = coder.getPropertyMetaData(j);
				printOption(coder, prop);
			}
		}
	}

	/**
	 * @param configObj
	 * @param aProp
	 */
	private static void printOption(IConfigurable configObj, IProperty aProp) {
		if (aProp.getType() != IProperty.Type.PROPERTY_FLAGS) {
			System.out.printf("  %s: %s\n", aProp.getName(), configObj
					.getPropertyAsString(aProp.getName()));
		} else {
			// it's a flag
			System.out.printf("  %s: %d (", aProp.getName(), configObj
					.getPropertyAsLong(aProp.getName()));
			int numSettings = aProp.getNumFlagSettings();
			long value = configObj.getPropertyAsLong(aProp.getName());
			for (int i = 0; i < numSettings; i++) {
				IProperty prop = aProp.getFlagConstant(i);
				long flagMask = prop.getDefault();
				boolean isSet = (value & flagMask) > 0;
				System.out.printf("%s%s; ", isSet ? "+" : "-", prop.getName());
			}
			System.out.printf(")\n");
		}
	}

}
