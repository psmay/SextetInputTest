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

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

public abstract class PacketWriterService extends AbstractExecutionThreadService {
	private static final Logger log = Logger.getLogger(PacketWriterService.class.getName());
	
	protected Main main;

	PacketWriterService(Main main) {
		this.main = main;
	}
	
	private LinkedBlockingQueue<Packet> waiting = new LinkedBlockingQueue<>(4);

	void sendPacket(Packet packet) {
		if (packet.isValid()) {
			// Delete an older submission if the waiting queue is full
			while (!waiting.offer(packet)) {
				log.fine("Dropping an older waiting packet to allow for a new one");
				waiting.poll();
			}
		}
	}

	// If no packets remain in waiting, add a blank packet.
	// If waiting is not empty, nothing is added since the next packet
	// out is effectively a keepalive.
	void hintSendKeepalive() {
		if (waiting.isEmpty()) {
			waiting.offer(Packet.BLANK_PACKET);
		}
	}

	private volatile boolean doneReading = false;

	@Override
	protected void triggerShutdown() {
		// Prevent the next pass of the loop in getNextPacket()
		doneReading = true;

		// Cause waiting.take() in getNextPacket() to stop.
		// (The offer doesn't have to succeed; if it doesn't,
		// this just means that waiting is already full and
		// waiting.take() should run unblocked on the next
		// pass.)
		waiting.offer(Packet.INVALID_PACKET);
	}

	// Does waiting.take() in a loop. Receiving an INVALID_PACKET in the
	// queue (which will be skipped) or an interrupt on the
	// thread will cause doneReading to be rechecked before
	// continuing to wait.
	// The return value will be null iff doneReading.
	private Packet getNextPacket() {
		while (!doneReading) {
			try {
				Packet packet = waiting.take();
				if (!packet.isValid()) {
					continue;
				}
				return doneReading ? null : packet;
			} catch (InterruptedException e) {
				continue;
			}
		}
		return null;
	}

	@Override
	protected void run() throws Exception {
		packetWriterLoopBody();
	}

	protected void packetWriterLoopBody() throws IOException {
		Packet packet;
		while ((packet = getNextPacket()) != null) {
			writingPacket(packet);
			outputPacket(packet);
			
		}
	}

	private void writingPacket(Packet packet) {
		main.writingPacket(packet);
		
	}

	protected abstract void outputPacket(Packet packet) throws IOException;

}
