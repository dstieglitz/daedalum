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

package com.stainlesscode.mediapipeline.factory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.pool.BasePoolableObjectFactory;

import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IPixelFormat.Type;

public class IVideoPictureObjectPoolFactory extends BasePoolableObjectFactory {

	private Type pixelType;
	private int width;
	private int height;
	private Lock nativeCodeLock = new ReentrantLock(false);

	public IVideoPictureObjectPoolFactory(Type pixelType, int width, int height) {
		this.pixelType = pixelType;
		this.width = width;
		this.height = height;
	}

	@Override
	public Object makeObject() throws Exception {
		nativeCodeLock.lock();
		try {
			return IVideoPicture.make(pixelType, width, height);
		} finally {
			nativeCodeLock.unlock();
		}
	}

}
