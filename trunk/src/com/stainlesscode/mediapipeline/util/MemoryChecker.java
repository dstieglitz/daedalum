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

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stainlesscode.mediapipeline.EngineRuntime;

public class MemoryChecker extends EngineThread {

	private static Logger LogUtil = LoggerFactory
			.getLogger(MemoryChecker.class);

	private EngineRuntime engineRuntime;

	public MemoryChecker(EngineRuntime engineRuntime) {
		this.engineRuntime = engineRuntime;
	}

	@Override
	public void run() {
		while (!isMarkedForDeath()) {
			long time = System.currentTimeMillis();
			if (time % 1000 == 0) {
				long mem = Runtime.getRuntime().freeMemory();
				long total = Runtime.getRuntime().totalMemory();
				double pct = ((double) mem / (double) total) * 100.0d;
				LogUtil.info("freeMemory="
						+ (mem / 1024 / 1024)
						+ "MB ("
						+ new BigDecimal(pct)
								.setScale(2, BigDecimal.ROUND_DOWN) + "%)");

				if (engineRuntime.getPacketPool() != null) {
					int packetPoolActive = engineRuntime.getPacketPool()
							.getNumActive();
					int packetPoolIdle = engineRuntime.getPacketPool()
							.getNumIdle();
					int audioSamplePoolActive = engineRuntime
							.getAudioSamplePool().getNumActive();
					int audioSamplePoolIdle = engineRuntime
							.getAudioSamplePool().getNumIdle();
					int rawPicturePoolActive = engineRuntime
							.getRawPicturePool().getNumActive();
					int rawPicturePoolIdle = engineRuntime.getRawPicturePool()
							.getNumIdle();
					int resampledPicturePoolActive = engineRuntime
							.getResampledPicturePool().getNumActive();
					int resampledPicturePoolIdle = engineRuntime
							.getResampledPicturePool().getNumIdle();

					LogUtil.info("packetPoolUsage=" + packetPoolActive + "/"
							+ packetPoolIdle);
					LogUtil
							.info("audioSamplePoolUsage="
									+ audioSamplePoolActive + "/"
									+ audioSamplePoolIdle);
					LogUtil.info("rawPicturePoolUsage=" + rawPicturePoolActive
							+ "/" + rawPicturePoolIdle);
					LogUtil.info("resampledPicturePoolUsage="
							+ resampledPicturePoolActive + "/"
							+ resampledPicturePoolIdle);
				}

				try {
					Thread.sleep(700);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
