package com.stainlesscode.mediapipeline;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoResampler;

public class TrackConfiguration {

	private static Logger LogUtil = LoggerFactory
			.getLogger(TrackConfiguration.class);

	public enum Type {
		STEREO_AUDIO, VIDEO, SUBTITLE, LTC, MONO_AUDIO
	};

	private Map<Integer, Type> configurationMap;
	private Map<Integer, String> metadataMap;

	public TrackConfiguration() {
		configurationMap = new HashMap<Integer, Type>();
	}

	public Type getTrackType(int trackId) {
		return configurationMap.get(trackId);
	}

	public void setTrackType(int trackId, Type type) {
		configurationMap.put(trackId, type);
	}

	public void init(Engine engine) {
		EngineRuntime engineRuntime = engine.getEngineRuntime();

		if (configurationMap.size() > 0) {
			configureWithConfigurationMap(engine);
		} else { // try to figure out what's what
			for (int i = 0; i < engineRuntime.getContainer().getNumStreams(); i++) {
				Integer streamId = new Integer(i);
				IStream stream = engineRuntime.getContainer().getStream(i);
				IStreamCoder coder = stream.getStreamCoder();
				engine.packetDecoderMap.put(streamId, coder);

				if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
					IVideoResampler resampler = engine.getResampler(coder);
					if (resampler != null) {
						engineRuntime.setResampler(resampler);
					}
					engineRuntime.getStreamToBufferMap().put(streamId,
							engineRuntime.getVideoPacketBuffer());
					engineRuntime.setVideoCoder(coder);
				} else if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
					engineRuntime.getStreamToBufferMap().put(streamId,
							engineRuntime.getAudioPacketBuffer());
					engineRuntime.setAudioCoder(coder);
				}

				if (coder.open() < 0) {
					// throw new RuntimeException("Could not open stream " + i);
					LogUtil.warn("Could not open codec for stream " + i);
				}
			}
		}
	}

	private void configureWithConfigurationMap(Engine engine) {
		EngineRuntime engineRuntime = engine.getEngineRuntime();

		if (!configurationMap.entrySet().contains(Type.VIDEO)) {
			LogUtil.warn("No video track is configured in the engine");
		}

		if (!(configurationMap.entrySet().contains(Type.MONO_AUDIO) || (configurationMap
				.entrySet().contains(Type.STEREO_AUDIO)))) {
			LogUtil.warn("No audio track is configured in the engine");
		}

		for (int i = 0; i < engineRuntime.getContainer().getNumStreams(); i++) {
			Integer streamId = new Integer(i);
			IStream stream = engineRuntime.getContainer().getStream(i);
			IStreamCoder coder = stream.getStreamCoder();
			engine.packetDecoderMap.put(streamId, coder);

			if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO
					&& getTrackType(i) == Type.VIDEO) {
				IVideoResampler resampler = engine.getResampler(coder);
				if (resampler != null) {
					engineRuntime.setResampler(resampler);
				}
				engineRuntime.getStreamToBufferMap().put(streamId,
						engineRuntime.getVideoPacketBuffer());
				engineRuntime.setVideoCoder(coder);
			} else if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO
					&& (getTrackType(i) == Type.MONO_AUDIO || getTrackType(i) == Type.STEREO_AUDIO)) {
				engineRuntime.getStreamToBufferMap().put(streamId,
						engineRuntime.getAudioPacketBuffer());
				engineRuntime.setAudioCoder(coder);
			}

			if (coder.open() < 0) {
				// throw new RuntimeException("Could not open stream " + i);
				LogUtil.warn("Could not open codec for stream " + i);
			}
		}
	}
}
