/*******************************************************************************
 * Copyright (c) 2026 DocFetcher contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package net.sourceforge.docfetcher.model.ocr;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

/**
 * Tests that the GUI can ask the engine for problems without waiting for a
 * recognition running in the background (the preferences dialog did, and
 * opened only after the page was recognized).
 */
public final class OcrEngineTest {

	@Test
	public void peekDoesNotWaitForARunningRecognition() throws Exception {
		final OcrEngine engine = OcrEngine.getInstance();
		final CountDownLatch locked = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		// Holds the engine's lock like a recognition of a page does
		Thread recognition = new Thread() {
			public void run() {
				synchronized (engine) {
					locked.countDown();
					try {
						release.await();
					}
					catch (InterruptedException e) {
						// Done
					}
				}
			}
		};
		recognition.start();
		try {
			locked.await();
			long start = System.currentTimeMillis();
			engine.peekUnavailableReason();
			long millis = System.currentTimeMillis() - start;
			assertTrue("Waited " + millis + " ms", millis < 500);
		}
		finally {
			release.countDown();
			recognition.join();
		}
	}

}
