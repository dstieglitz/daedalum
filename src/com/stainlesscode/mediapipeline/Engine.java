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

package com.stainlesscode.mediapipeline;

import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.audioout2.DefaultAudioPlayer;
import com.stainlesscode.mediapipeline.buffer.CircularFifoMediaBuffer;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventListener;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent.Type;
import com.stainlesscode.mediapipeline.factory.AudioOutputFactory;
import com.stainlesscode.mediapipeline.factory.DemultiplexerFactory;
import com.stainlesscode.mediapipeline.factory.PacketDecoderFactory;
import com.stainlesscode.mediapipeline.factory.SynchronizerFactory;
import com.stainlesscode.mediapipeline.factory.VideoOutputFactory;
import com.stainlesscode.mediapipeline.util.EngineThread;
import com.stainlesscode.mediapipeline.util.MediaPlayerEventSupportedEngineThread;
import com.stainlesscode.mediapipeline.util.MemoryChecker;
import com.stainlesscode.mediapipeline.util.MetadataUtil;
import com.stainlesscode.mediapipeline.util.SeekHelper;
import com.stainlesscode.mediapipeline.util.ThreadWatchdog;
import com.stainlesscode.mediapipeline.videoout.MediaPlayerEventAwareVideoPlayer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoResampler;

/**
 * <p>
 * The Engine class is the Facade to the media processing engine and relevant
 * objects. An engine configures the processing pipeline based on an
 * EngineConfiguration object. Each engine has an input layer, consisting of a
 * chain of one or more input plugins, a demux/decode layer, an optional
 * synchronization layer, and an output layer consisting of a chain of one or
 * more output plugins.
 * </p>
 * 
 * @author Dan Stieglitz
 * 
 */
public class Engine extends MediaPlayerEventSupport implements
		MediaPlayerEventListener {

	private static Logger LogUtil = LoggerFactory.getLogger(Engine.class);

	protected EngineRuntime engineRuntime;
	protected EngineConfiguration engineConfiguration;
	protected Map<Integer, IStreamCoder> packetDecoderMap = new HashMap<Integer, IStreamCoder>();
	protected VideoOutput videoOutput;

	// XXX EXPERIMENTAL
	protected AudioOutput2 audioOutput;

	protected DataOutput dataOutput;
	protected EngineThread demultiplexer, audioDecoder, videoDecoder,
			audioPlayer, videoPlayer;
	protected Synchronizer synchronizer;
	protected Thread demuxThread, audioDecodeThread, videoDecodeThread,
			audioPlayThread, videoPlayThread;

	protected SeekHelper seekHelper;
	protected boolean started;
	protected boolean stopAfterFirstFrame;
	protected String url;

	// singleton
	protected Engine(EngineConfiguration config) {
		Engine engine = this;
		engine.engineConfiguration = config;
		engine.engineRuntime = new EngineRuntime();
		engine.engineRuntime.setEngine(engine);
		engine.seekHelper = new SeekHelper(engine.engineRuntime);

		engine.engineRuntime
				.setVideoPacketBuffer(new CircularFifoMediaBuffer(
						"video packet",
						engine.engineConfiguration
								.getConfigurationValueAsInt(EngineConfiguration.VIDEO_PACKET_BUFFER_SIZE_KEY)));
		engine.engineRuntime
				.setAudioPacketBuffer(new CircularFifoMediaBuffer(
						"audio packet",
						engine.engineConfiguration
								.getConfigurationValueAsInt(EngineConfiguration.AUDIO_PACKET_BUFFER_SIZE_KEY)));
		engine.engineRuntime
				.setAudioFrameBuffer(new CircularFifoMediaBuffer(
						"audio frame",
						engine.engineConfiguration
								.getConfigurationValueAsInt(EngineConfiguration.AUDIO_FRAME_BUFFER_SIZE_KEY)));
		engine.engineRuntime
				.setVideoFrameBuffer(new CircularFifoMediaBuffer(
						"video frame",
						engine.engineConfiguration
								.getConfigurationValueAsInt(EngineConfiguration.VIDEO_FRAME_BUFFER_SIZE_KEY)));

		engine.engineRuntime
				.setStreamToBufferMap(new HashMap<Integer, Buffer>());

		engine.engineRuntime.setPacketDecoderMap(packetDecoderMap);
	}

	/**
	 * Construct an engine using the specified configuration
	 * 
	 * @param url
	 * @return
	 */
	public static Engine createEngine(EngineConfiguration config) {
		checkCanConvertVideoPixelFormat();
		Engine engine = new Engine(config);
		return engine;
	}

	/**
	 * Attempt to load the specified URL as a media containter
	 * 
	 * @param url
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	public void loadUrl(String url) throws InstantiationException,
			IllegalAccessException, ClassNotFoundException {

		this.url = url;

		if (this.started) {
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("stopping and closing engine on a loadUrl call");
			this.stop();
		}

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("loadUrl " + url);

		if (engineConfiguration
				.getConfigurationValueAsBoolean(EngineConfiguration.CHECK_MEMORY_KEY)) {
			MemoryChecker checker = new MemoryChecker(engineRuntime);
			new Thread(checker).start();
		}

		if (engineConfiguration
				.getConfigurationValueAsBoolean(EngineConfiguration.CHECK_THREADS_KEY)) {
			ThreadWatchdog watchdog = new ThreadWatchdog();
			new Thread(watchdog).start();
		}

		if (engineConfiguration
				.getConfigurationValueAsBoolean(EngineConfiguration.AUTO_START_KEY)) {
			if (engineConfiguration
					.getConfigurationValueAsBoolean(EngineConfiguration.SHOW_FIRST_FRAME_KEY)) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("will stop after first frame is presented");
				this.stopAfterFirstFrame = true;
				this.start();
			}
		}
	}

	protected void initializeSynchronizer() throws InstantiationException,
			IllegalAccessException, ClassNotFoundException {
		synchronizer = SynchronizerFactory
				.createSynchronizer(engineConfiguration);

		if (synchronizer != null) {
			synchronizer.init(engineRuntime);
			engineRuntime.setSynchronizer(synchronizer);
		}

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("leaving initializeSynchronizer");
	}

	protected Demultiplexer initializeDemux() throws InstantiationException,
			IllegalAccessException, ClassNotFoundException {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("entering initializeDemux");
		// setup demux layer
		// create demultiplexer
		Demultiplexer demux = DemultiplexerFactory
				.createDemultiplexer(engineConfiguration);

		demux.init(url, engineRuntime);
		demultiplexer = (EngineThread) demux;

		engineRuntime.init();

		audioDecoder = (EngineThread) PacketDecoderFactory.createPacketDecoder(
				PacketDecoderFactory.Type.AUDIO, engineConfiguration);
		videoDecoder = (EngineThread) PacketDecoderFactory.createPacketDecoder(
				PacketDecoderFactory.Type.VIDEO, engineConfiguration);

		((PacketDecoder) audioDecoder).init(engineRuntime);
		((PacketDecoder) videoDecoder).init(engineRuntime);

		((MediaPlayerEventSupportedEngineThread) demux)
				.addMediaPlayerEventListener(this);

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("leaving initializeDemux");

		return demux;
	}

	protected void initializeContainer() {
		initializeContainer(this.url);
	}

	protected void initializeContainer(String url) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("initializing new container for " + url);

		IContainer container = IContainer.make();
		engineRuntime.setContainer(container);

		engineRuntime.getContainerLock().lock();
		try {
			// Open up the container
			int result = container.open(url, IContainer.Type.READ, null);

			if (result < 0) {
				IError error = IError.make(result);
				LogUtil.error("ERROR: " + error.getDescription());
				throw new IllegalArgumentException("could not open url " + url
						+ ": " + error.getDescription());
			}

			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("new container created: " + container);
				LogUtil.debug("Found "
						+ engineRuntime.getContainer().getNumStreams()
						+ " streams");
			}

			// TODO need more robust way to store coders and streams to handle
			// data, subtitles, etc.
			// is this a DemultiplexerStrategy? defined in config?
			for (int i = 0; i < engineRuntime.getContainer().getNumStreams(); i++) {
				Integer streamId = new Integer(i);
				IStream stream = engineRuntime.getContainer().getStream(i);
				IStreamCoder coder = stream.getStreamCoder();
				packetDecoderMap.put(streamId, coder);

				if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
					IVideoResampler resampler = getResampler(coder);
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
		} finally {
			engineRuntime.getContainerLock().unlock();
		}

		fireMediaPlayerEvent(new MediaPlayerEvent(this,
				MediaPlayerEvent.Type.MEDIA_LOADED, MetadataUtil
						.getMetaData(engineRuntime.getContainer())));

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("leaving initializeContainer");
	}

	protected void initializeOutputLayer() throws InstantiationException,
			IllegalAccessException, ClassNotFoundException {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("entering initializeOutputLayer");

		if (videoOutput == null) {
			LogUtil
					.warn("No VideoOutput specified, creating a default (but it's probably not visible anywhere)");
			videoOutput = VideoOutputFactory
					.createVideoOutput(engineConfiguration);
		}

		videoOutput.init(engineRuntime);

		if (audioOutput == null) {
			audioOutput = AudioOutputFactory
					.createAudioOutput(engineConfiguration);
			// XXX EXPERIMENTAL
			// audioOutput = new PortAudioDriver();
		}

		AudioFormat format = new AudioFormat(engineRuntime.getAudioCoder()
				.getSampleRate(), (int) IAudioSamples
				.findSampleBitDepth(engineRuntime.getAudioCoder()
						.getSampleFormat()), engineRuntime.getAudioCoder()
				.getChannels(), true, false);

		audioOutput.init(engineRuntime, format);

		audioPlayer = new DefaultAudioPlayer(audioOutput, engineRuntime);

		videoPlayer = new MediaPlayerEventAwareVideoPlayer(videoOutput,
				engineRuntime);

		if (LogUtil.isDebugEnabled())
			LogUtil.debug("leaving initializeOutputLayer");
	}

	@SuppressWarnings("unchecked")
	public Map getMetaData() {
		if (engineRuntime.getContainer() != null) {
			return MetadataUtil.getMetaData(engineRuntime.getContainer());
		}

		throw new RuntimeException("getMetaData() called, but no media loaded");
	}

	/**
	 * Start the engine from the beginning, re-open all containers streams and
	 * re-start all threads
	 */
	public void start() {
		engineRuntime.setPaused(false);

		if (!this.started) { // don't start twice!
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("Engine--> START");

			if (engineRuntime.getContainer() == null && this.url != null) {
				try {
					initializeContainer();
					initializeDemux();
					initializeOutputLayer();
					initializeSynchronizer();
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
			}

			demuxThread = new Thread(demultiplexer, "Demultiplexer Thread");
			demuxThread.start();
			audioDecodeThread = new Thread(audioDecoder, "Audio Decode Thread");
			audioDecodeThread.start();
			videoDecodeThread = new Thread(videoDecoder, "Video Decode Thread");
			videoDecodeThread.start();
			audioPlayThread = new Thread(audioPlayer, "Audio Play Thread");
			audioPlayThread.start();
			videoPlayThread = new Thread(videoPlayer, "Video Play Thread");
			videoPlayThread.start();

			engineRuntime.getSynchronizer().start();
		}

		this.started = true;
	}

	/**
	 * Stops all engine threads and the synchronizer
	 */
	public void stop() {
		if (this.started) {
			if (LogUtil.isDebugEnabled())
				LogUtil.debug("Engine--> STOP");

			if (videoPlayer != null) {
				videoPlayer.setMarkedForDeath(true);
				videoPlayThread.interrupt();
			}

			if (audioPlayer != null) {
				audioPlayer.setMarkedForDeath(true);
				audioPlayThread.interrupt();
			}

			if (videoDecoder != null) {
				videoDecoder.setMarkedForDeath(true);
				videoDecodeThread.interrupt();
			}

			if (audioDecoder != null) {
				audioDecoder.setMarkedForDeath(true);
				audioDecodeThread.interrupt();
			}

			if (demultiplexer != null) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug("attempting demultiplexer shutdown");
				demuxThread.interrupt();
				demultiplexer.setMarkedForDeath(true);
			}

			if (synchronizer != null) {
				synchronizer.stop();
			}

			if (audioOutput != null) {
				try {
					audioOutput.close();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}

			if (videoOutput != null)
				videoOutput.close();

			if (engineRuntime != null) {
				IContainer container = engineRuntime.getContainer();
				if (container != null) {
					if (LogUtil.isDebugEnabled())
						LogUtil.debug("closing container " + container);
					container.close();
				}
				engineRuntime.setContainer(null);
			}

			clearBuffers();

			this.started = false;

			fireMediaPlayerEvent(new MediaPlayerEvent(this, Type.STOP, null));
		}
	}

	public void close() {
		throw new UnsupportedOperationException(
				"This operation is not supported. Use stop() instead.");
	}

	/**
	 * Pause the engine, leaving everything active but idling
	 */
	public void pause() {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("Engine--> PAUSE");
		engineRuntime.setPaused(true);
	}

	/**
	 * Unpause the engine
	 */
	public void unpause() {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("Engine--> UNPAUSE");
		engineRuntime.setPaused(false);
		if (!this.started)
			this.start();
	}

	/**
	 * This call is effectively identical to seek(microseconds,true)
	 * 
	 * @param microseconds
	 */
	public void seek(long microseconds) {
		seek(microseconds, true);
	}

	/**
	 * Seek into the stream by the specified number of microseconds. If
	 * clearBuffers is true, all of the buffers (packet and frame, for both
	 * audio and video) will be cleared.
	 * 
	 * @param microseconds
	 */
	public void seek(long microseconds, boolean clearBuffers) {
		int retcode = 0;
		engineRuntime.getContainerLock().lock();

		if ((retcode = seekHelper.seek(microseconds, false)) < 0) {
			IError err = IError.make(retcode);
			LogUtil.error(err.getDescription());
		}

		if (clearBuffers)
			clearBuffers();

		videoPlayThread.interrupt();
		audioPlayThread.interrupt();
		demuxThread.interrupt();

		fireMediaPlayerEvent(new MediaPlayerEvent(this,
				MediaPlayerEvent.Type.SEEK, microseconds));

		engineRuntime.getContainerLock().unlock();
	}

	public void clearBuffers() {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("clearing buffers...");
		engineRuntime.getAudioFrameBuffer().clear();
		engineRuntime.getVideoFrameBuffer().clear();
		engineRuntime.getAudioPacketBuffer().clear();
		engineRuntime.getVideoPacketBuffer().clear();
	}

	public static void checkCanConvertVideoPixelFormat() {
		// Let's make sure that we can actually convert video pixel formats.
		if (!IVideoResampler
				.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION))
			throw new RuntimeException("you must install the GPL version"
					+ " of Xuggler (with IVideoResampler support) for "
					+ "this demo to work");
	}

	protected IVideoResampler getResampler(IStreamCoder videoCoder) {
		IVideoResampler resampler = null;

		if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24) {
			// if this stream is not in BGR24, we're going to need to
			// convert it. The VideoResampler does that for us.
			resampler = IVideoResampler.make(videoCoder.getWidth(), videoCoder
					.getHeight(), IPixelFormat.Type.BGR24, videoCoder
					.getWidth(), videoCoder.getHeight(), videoCoder
					.getPixelType());
		}

		return resampler;
	}

	@Override
	public void mediaPlayerEventReceived(MediaPlayerEvent evt) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("mediaPlayerEventReceived= " + evt);

		if (evt.getType() == Type.CLIP_END) {
			this.audioDecoder.setClipEnded(true);
			this.videoDecoder.setClipEnded(true);
			this.videoPlayer.setClipEnded(true);
			this.audioPlayer.setClipEnded(true);

			// wait for threads to complete
			while (this.audioDecodeThread.isAlive()
					&& this.videoDecodeThread.isAlive()
					&& this.audioPlayThread.isAlive()
					&& this.videoPlayThread.isAlive())
				;

			this.stop();
		}

		// TODO move this behavior to player
		if (evt.getType() == Type.FIRST_VIDEO_FRAME_PRESENTED
				&& this.stopAfterFirstFrame) {
			this.pause();
			this.stopAfterFirstFrame = false;
		}
	}

	public VideoOutput getVideoOutput() {
		return videoOutput;
	}

	public void setVideoOutput(VideoOutput videoOutput) {
		this.videoOutput = videoOutput;
	}

	public Map<String, Object> getMetadata() {
		return MetadataUtil.getMetaData(engineRuntime.getContainer());
	}

	public boolean isPaused() {
		return engineRuntime.isPaused();
	}

	public void setPlaySpeed(double d) {
		engineRuntime.setPlaySpeed(d);
	}

	public EngineRuntime getEngineRuntime() {
		return engineRuntime;
	}

	public boolean isStarted() {
		return started;
	}

	public AudioOutput2 getAudioOutput() {
		return audioOutput;
	}

	public void setAudioOutput(AudioOutput2 audioOutput) {
		this.audioOutput = audioOutput;
	}

	public void setDataOutput(DataOutput dataOutput) {
		this.dataOutput = dataOutput;
	}

	public EngineConfiguration getEngineConfiguration() {
		return engineConfiguration;
	}
}
