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

package com.stainlesscode.mediapipeline.buffer;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.event.MediaPlayerEvent;
import com.stainlesscode.mediapipeline.event.MediaPlayerEventSupport;
import com.stainlesscode.mediapipeline.event.MediaPlayerEvent.Type;
import com.stainlesscode.mediapipeline.util.TimeUtil;
import com.xuggle.xuggler.IMediaData;

/**
 * Buffer of IMediaData objects.
 * 
 * TODO remove casts since changing the underlying array type
 * 
 * ALL TIMESTAMPS IN MICROSECONDS
 * 
 * @author Dan Stieglitz
 * 
 */
public class CircularFifoMediaBuffer extends MediaPlayerEventSupport implements
		Buffer {

	private Logger LogUtil = LoggerFactory
			.getLogger(CircularFifoMediaBuffer.class);

	protected IMediaData data[];
	protected int head;
	protected int tail;
	protected int fillCount;
	protected String name = "buffer";
	protected Lock lock = new ReentrantLock(false);
	protected Condition bufferFull = lock.newCondition();
	protected Condition bufferEmpty = lock.newCondition();
	protected long startTimestamp = -1;
	protected long endTimestamp = -1;

	public CircularFifoMediaBuffer(Integer number) {
		data = new IMediaData[number];
		head = 0;
		tail = 0;
		fillCount = 0;
	}

	public CircularFifoMediaBuffer(String name, Integer number) {
		this.name = name;
		data = new IMediaData[number];
		head = 0;
		tail = 0;
		fillCount = 0;
	}

	/**
	 * Create a copy of the source object (effectively a shallow clone)
	 * 
	 * @param source
	 */
	public CircularFifoMediaBuffer(CircularFifoMediaBuffer source) {
		this.data = new IMediaData[source.getCapacity()];
		this.head = source.head;
		this.tail = source.tail;
		this.fillCount = source.fillCount;
		this.name = source.name;
		for (int i = 0; i < source.data.length; i++) {
			this.data[i] = source.data[i];
		}
	}

	public void cloneInto(CircularFifoMediaBuffer existingBuffer) {
		if (existingBuffer.getCapacity() != this.getCapacity())
			throw new RuntimeException(
					"Can't clone into a buffer with different capacity "
							+ existingBuffer.getCapacity());
		existingBuffer.head = this.head;
		existingBuffer.tail = this.tail;
		existingBuffer.fillCount = this.fillCount;
		for (int i = 0; i < data.length; i++) {
			if (this.data[i] != null) {
				existingBuffer.data[i] = this.data[i].copyReference();
			}
			// existingBuffer.data[i] = this.data[i];
		}
	}

	public void clear() {
		for (int i = 0; i < data.length; i++) {
			data[i] = null;
		}
		head = 0;
		tail = 0;
		fillCount = 0;
	}

	private int checkBounds(int index) {
		if (index < 0)
			return data.length - 1;
		else if (index >= data.length)
			return 0;
		else
			return index;
	}

	// for debugging
	public void dumpArrayContents(String msg) {
		LogUtil.debug("msg is " + msg);
		for (int i = 0; i < data.length; i++) {
			String ts = "null";
			IMediaData value1 = (IMediaData) data[i];
			if (value1 != null)
				ts = "" + TimeUtil.getTimecode(29.97f, value1.getTimeStamp());
			LogUtil.debug("(" + i + ") " + ts);
		}
	}

	public void placeInOrder(IMediaData value) {
		if (LogUtil.isDebugEnabled())
			LogUtil.debug("[" + head + "," + tail + "] " + value.getTimeStamp()
					+ " is out of order");
		int curtail = tail;

		// make sure the order of the packets is preserved
		boolean outOfOrder = true;
		boolean duplicate = false;

		while (outOfOrder && !duplicate) {
			curtail = checkBounds(curtail);

			duplicate = ((data[curtail] != null) && (((IMediaData) data[curtail])
					.getTimeStamp() == value.getTimeStamp()));

			data[curtail] = (IMediaData) get();
			curtail = checkBounds(curtail - 1);
			data[curtail] = value;

			outOfOrder = ((data[curtail] != null) && (((IMediaData) data[curtail])
					.getTimeStamp() > value.getTimeStamp()));
		}
	}

	@Override
	public boolean add(Object value) {
		if (value == null)
			return false;

		try {
			lock.lockInterruptibly();

			while (bufferFull()) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug(name + " waiting while full");
				fireMediaPlayerEvent(new MediaPlayerEvent(this,
						Type.BUFFER_FULL, null));
				bufferFull.await(10, TimeUnit.MILLISECONDS);
			}

			// make sure the order of the packets is preserved
			if (LogUtil.isDebugEnabled()) {
				LogUtil.debug("get()=" + get());
				LogUtil.debug("value=" + value);
			}

			IMediaData mediaData = (IMediaData) value;
			IMediaData tailValue = (IMediaData) get();

			if (tailValue != null
					&& tailValue.getTimeStamp() > mediaData.getTimeStamp()) {
				placeInOrder(mediaData);
			} else {
				data[tail++] = mediaData;
				this.endTimestamp = mediaData.getTimeStamp();
			}

			if (tail == data.length) {
				tail = 0;
			}

			fillCount++;
			bufferEmpty.signal();
		} catch (InterruptedException e1) {
			if (((ReentrantLock) lock).isHeldByCurrentThread()) {
				bufferFull.signalAll();
				bufferEmpty.signalAll();
			}
			clear();

			// keep interrupted status alive
			// @see
			// http://www.ibm.com/developerworks/java/library/j-jtp05236.html
			// this is commented out as seeking works much better without it
			// Thread.currentThread().interrupt();

			return false;
		} finally {
			if (((ReentrantLock) lock).isLocked()
					&& ((ReentrantLock) lock).isHeldByCurrentThread())
				lock.unlock();
		}

		return true;
	}

	@Override
	public Object remove() {
		Object result = null;

		try {
			lock.lockInterruptibly();

			while (isEmpty()) {
				if (LogUtil.isDebugEnabled())
					LogUtil.debug(name + " waiting while empty");
				fireMediaPlayerEvent(new MediaPlayerEvent(this,
						Type.BUFFER_EMPTY, null));
				bufferEmpty.await(10, TimeUnit.MILLISECONDS);
			}

			result = data[head++];

			if (head == data.length) {
				head = 0;
			}

			fillCount--;

			if (fillCount > 0)
				this.startTimestamp = ((IMediaData) peekAt(head))
						.getTimeStamp();

			bufferFull.signal();
		} catch (InterruptedException e1) {
			if (((ReentrantLock) lock).isHeldByCurrentThread()) {
				bufferFull.signalAll();
				bufferEmpty.signalAll();
			}

			// keep interrupted status alive
			// @see
			// http://www.ibm.com/developerworks/java/library/j-jtp05236.html
			// this is commented out as seeking works much better without it
			// Thread.currentThread().interrupt();

		} finally {
			if (((ReentrantLock) lock).isLocked()
					&& ((ReentrantLock) lock).isHeldByCurrentThread())
				lock.unlock();
		}

		return result;
	}

	@Override
	public Object get() {
		int idx = tail - 1;
		if (tail - 1 < 0)
			idx = data.length - 1;
		return data[idx];
	}

	public Object peekAt(int i) {
		if (i < 0)
			i = 0;
		if (i >= data.length)
			i = data.length - 1;
		return data[i];
	}

	public boolean bufferFull() {
		return (fillCount == data.length);
	}

	public boolean isEmpty() {
		return (fillCount == 0);
	}

	public int getHead() {
		return head;
	}

	public int getTail() {
		return tail;
	}

	public int getCapacity() {
		return data.length;
	}

	public int getSize() {
		return fillCount;
	}

	public void rewind() {
		head = 0;
		tail = 0;
	}

	public String toString() {
		return name;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean addAll(Collection c) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@Override
	public boolean contains(Object o) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean containsAll(Collection c) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Iterator iterator() {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean removeAll(Collection c) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean retainAll(Collection c) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	@Override
	public int size() {
		return fillCount;
	}

	@Override
	public Object[] toArray() {
		return data;
	}

	@Override
	public Object[] toArray(Object[] a) {
		throw new UnsupportedOperationException(
				"This operation is not supported.");
	}

	public long getStartTimestamp() {
		return startTimestamp;
	}

	public void setStartTimestamp(long startTimestamp) {
		this.startTimestamp = startTimestamp;
	}

	public long getEndTimestamp() {
		return endTimestamp;
	}

	public void setEndTimestamp(long endTimestamp) {
		this.endTimestamp = endTimestamp;
	}
}