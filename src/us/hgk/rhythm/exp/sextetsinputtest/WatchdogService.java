/* * * * *
 * Copyright © 2016 Peter S. May
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 * * * * */

package us.hgk.rhythm.exp.sextetsinputtest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

public class WatchdogService extends AbstractExecutionThreadService {

	private final Main main;

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition shouldRecheckCondition = lock.newCondition();

	private AtomicLong expiration = new AtomicLong();
	private AtomicBoolean active = new AtomicBoolean(false);
	private AtomicBoolean continuing = new AtomicBoolean(true);

	private final long intervalNanos;

	public WatchdogService(Main main, long intervalMillis) {
		this.main = main;
		if (intervalMillis > 0) {
			intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
		} else {
			intervalNanos = 0;
		}
	}

	@Override
	protected void run() {
		while (continuing.get()) {
			long now = System.nanoTime();
			long remaining = expiration.get() - now;

			if (remaining <= 0) {
				timeExpired(now, remaining);
				continue;
			}

			lock.lock();
			try {
				shouldRecheckCondition.awaitNanos(remaining);
				continue;
			} catch (InterruptedException e) {
				// Check continuing condition, then resume
				continue;
			} finally {
				lock.unlock();
			}
		}
	}

	private void timeExpired(long now, long remaining) {
		if (active.get()) {
			main.watchdogTimeout();
			expiration.set(now + intervalNanos);
		} else {
			expiration.set(Long.MAX_VALUE);
		}
	}

	@Override
	protected void triggerShutdown() {
		continuing.set(false);
		triggerRecheck();
	}

	private void triggerRecheck() {
		lock.lock();
		try {
			shouldRecheckCondition.signalAll();
		} finally {
			lock.unlock();
		}
	}

	private void set(long newExpiration) {
		expiration.set(newExpiration);
		active.set(true);
		triggerRecheck();
	}

	void reset() {
		if (intervalNanos > 0) {
			set(System.nanoTime() + intervalNanos);
		} else {
			unset();
		}
	}

	void unset() {
		active.set(false);
		expiration.set(Long.MAX_VALUE);
		triggerRecheck();
	}
}
