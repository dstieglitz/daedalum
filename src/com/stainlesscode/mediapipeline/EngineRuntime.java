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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections.Buffer;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.SoftReferenceObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;

import com.stainlesscode.mediapipeline.factory.IAudioSamplesObjectPoolFactory;
import com.stainlesscode.mediapipeline.factory.IPacketObjectPoolFactory;
import com.stainlesscode.mediapipeline.factory.IVideoPictureObjectPoolFactory;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoResampler;

/**
 * This class contains shared variables that the various engine components use
 * to do their jobs
 * 
 * @author Dan Stieglitz
 * 
 */
public class EngineRuntime {

	private Engine engine;

	private ObjectPool packetPool;
	private ObjectPool rawPicturePool;
	private ObjectPool resampledPicturePool;
	private ObjectPool audioSamplePool;

	private IVideoResampler resampler;

	private IStreamCoder videoCoder;
	private IStreamCoder audioCoder;

	private Lock containerLock = new ReentrantLock(false);
	private Lock videoDecodeLock = new ReentrantLock(false);
	private Lock audioDecodeLock = new ReentrantLock(false);

	private volatile Buffer videoPacketBuffer;
	private volatile Buffer videoFrameBuffer;
	private volatile Buffer audioPacketBuffer;
	private volatile Buffer audioFrameBuffer;

	private IContainer container;

	private Synchronizer synchronizer;

	private Map<Integer, IStreamCoder> packetDecoderMap;
	private Map<Integer, Buffer> streamToBufferMap;

	private double playSpeed = 1.0;

	private volatile boolean paused;

	private Map<String, Object> userObjects = new HashMap<String, Object>();

	/**
	 * This method is to be called AFTER the coders have been setup and opened.
	 */
	public void init() {
		// FIXME support audio-only files
		if (this.videoCoder == null) {
			throw new RuntimeException(
					"No suitable video decoder could be loaded");
		}

//		if (engine.getEngineConfiguration().getConfigurationValueAsBoolean(
//				EngineConfiguration.USE_OBJECT_POOLS)) {
			if (videoCoder != null) {
				this.rawPicturePool = new StackObjectPool(
						new IVideoPictureObjectPoolFactory(this.videoCoder
								.getPixelType(), this.videoCoder.getWidth(),
								this.videoCoder.getHeight()));
			}

			// FIXME buffer size important? probably.
			if (audioCoder != null) {
				this.audioSamplePool = new StackObjectPool(
						new IAudioSamplesObjectPoolFactory(1024, audioCoder
								.getChannels()));
			}

			this.resampledPicturePool = new StackObjectPool(
					new IVideoPictureObjectPoolFactory(this.resampler
							.getOutputPixelFormat(), this.resampler
							.getOutputWidth(), this.resampler.getOutputHeight()));

			this.packetPool = new SoftReferenceObjectPool(
					new IPacketObjectPoolFactory());
//		}
	}

	public ObjectPool getPacketPool() {
		return packetPool;
	}

	public void setPacketPool(ObjectPool packetPool) {
		this.packetPool = packetPool;
	}

	public ObjectPool getRawPicturePool() {
		return rawPicturePool;
	}

	public void setRawPicturePool(ObjectPool rawPicturePool) {
		this.rawPicturePool = rawPicturePool;
	}

	public ObjectPool getResampledPicturePool() {
		return resampledPicturePool;
	}

	public void setResampledPicturePool(ObjectPool resampledPicturePool) {
		this.resampledPicturePool = resampledPicturePool;
	}

	public ObjectPool getAudioSamplePool() {
		return audioSamplePool;
	}

	public void setAudioSamplePool(ObjectPool audioSamplePool) {
		this.audioSamplePool = audioSamplePool;
	}

	public IVideoResampler getResampler() {
		return resampler;
	}

	public void setResampler(IVideoResampler resampler) {
		this.resampler = resampler;
	}

	public IStreamCoder getVideoCoder() {
		return videoCoder;
	}

	public void setVideoCoder(IStreamCoder videoCoder) {
		this.videoCoder = videoCoder;
	}

	public IStreamCoder getAudioCoder() {
		return audioCoder;
	}

	public void setAudioCoder(IStreamCoder audioCoder) {
		this.audioCoder = audioCoder;
	}

	/**
	 * The container lock is used to control access to the container, for
	 * example when opening, closing and retrieving packets. A lock on the
	 * containerLock not held by the demultiplexer will prevent demultiplexing.
	 */
	public Lock getContainerLock() {
		return containerLock;
	}

	public void setContainerLock(Lock containerLock) {
		this.containerLock = containerLock;
	}

	public Lock getVideoDecodeLock() {
		return videoDecodeLock;
	}

	public void setVideoDecodeLock(Lock videoDecodeLock) {
		this.videoDecodeLock = videoDecodeLock;
	}

	public Lock getAudioDecodeLock() {
		return audioDecodeLock;
	}

	public void setAudioDecodeLock(Lock audioDecodeLock) {
		this.audioDecodeLock = audioDecodeLock;
	}

	public void setContainer(IContainer container) {
		this.container = container;
	}

	public IContainer getContainer() {
		return container;
	}

	public Buffer getVideoPacketBuffer() {
		return videoPacketBuffer;
	}

	public void setVideoPacketBuffer(Buffer videoPacketBuffer) {
		this.videoPacketBuffer = videoPacketBuffer;
	}

	public Buffer getVideoFrameBuffer() {
		return videoFrameBuffer;
	}

	public void setVideoFrameBuffer(Buffer videoFrameBuffer) {
		this.videoFrameBuffer = videoFrameBuffer;
	}

	public Buffer getAudioPacketBuffer() {
		return audioPacketBuffer;
	}

	public void setAudioPacketBuffer(Buffer audioPacketBuffer) {
		this.audioPacketBuffer = audioPacketBuffer;
	}

	public Buffer getAudioFrameBuffer() {
		return audioFrameBuffer;
	}

	public void setAudioFrameBuffer(Buffer audioFrameBuffer) {
		this.audioFrameBuffer = audioFrameBuffer;
	}

	public void setPacketDecoderMap(Map<Integer, IStreamCoder> packetDecoderMap) {
		this.packetDecoderMap = packetDecoderMap;
	}

	public Map<Integer, IStreamCoder> getPacketDecoderMap() {
		return packetDecoderMap;
	}

	public Map<Integer, Buffer> getStreamToBufferMap() {
		return streamToBufferMap;
	}

	public void setStreamToBufferMap(Map<Integer, Buffer> streamToBufferMap) {
		this.streamToBufferMap = streamToBufferMap;
	}

	public void setSynchronizer(Synchronizer synchronizer) {
		this.synchronizer = synchronizer;
	}

	public Synchronizer getSynchronizer() {
		return synchronizer;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public boolean isPaused() {
		return paused;
	}

	public void setPlaySpeed(double playSpeed) {
		this.playSpeed = playSpeed;
	}

	public double getPlaySpeed() {
		return playSpeed;
	}

	public Engine getEngine() {
		return engine;
	}

	public void setEngine(Engine engine) {
		this.engine = engine;
	}

	public void putUserObject(String string, Object obj) {
		userObjects.put(string, obj);
	}

	public Object getUserObject(String key) {
		return userObjects.get(key);
	}
}
