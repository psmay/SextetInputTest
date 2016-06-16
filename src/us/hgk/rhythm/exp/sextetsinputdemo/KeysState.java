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

package us.hgk.rhythm.exp.sextetsinputdemo;

import java.util.HashSet;
import java.util.logging.Logger;

public class KeysState {
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(KeysState.class.getName());

	private HashSet<Integer> pressedKeyCodes;

	KeysState() {
		this.pressedKeyCodes = new HashSet<>();
	}

	// Updates the pressed state of the given keyCode.
	// Returns true iff the state actually changed.
	boolean update(int keyCode, boolean pressed) {
		synchronized (this) {
			if (pressed) {
				return pressedKeyCodes.add(keyCode);
			} else {
				return pressedKeyCodes.remove(keyCode);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private HashSet<Integer> getClone() {
		synchronized (this) {
			return (HashSet<Integer>) pressedKeyCodes.clone();
		}
	}

	Packet getAsPacket() {
		HashSet<Integer> si = getClone();

		Integer lastSetIndex0 = maxInteger(si);

		int lastSetIndex = (lastSetIndex0 == null) ? -1 : lastSetIndex0;
		int pastSetIndex = lastSetIndex + 1;

		int sextetsToEncode = ceilOfNDiv6(pastSetIndex);
		if (sextetsToEncode == 0) {
			// A packet of all 0s is encoded overlong to distinguish it from a
			// blank line.
			sextetsToEncode = 1;
		}

		char[] packetData = new char[sextetsToEncode];
		boolean[] b = new boolean[6];

		for (int i = 0; i < sextetsToEncode; ++i) {
			for (int j = 0; j < 6; ++j) {
				int fullIndex = (i * 6) + j;
				b[j] = si.contains(fullIndex);
			}

			packetData[i] = getSextet(b[0], b[1], b[2], b[3], b[4], b[5]);
		}

		return Packet.get(String.valueOf(packetData));
	}

	private char getSextet(int value) {
		// Keeps the low 6 bits, sets the bits above to form printable non-space
		// ASCII
		int n = (((value + 0x10) & 0x3F) + 0x30);
		return (char) n;
	}

	private char getSextet(boolean b0, boolean b1, boolean b2, boolean b3, boolean b4, boolean b5) {
		int sextetValue =
				(b0 ? 0x01 : 0) |
				(b1 ? 0x02 : 0) |
				(b2 ? 0x04 : 0) |
				(b3 ? 0x08 : 0) |
				(b4 ? 0x10 : 0) |
				(b5 ? 0x20 : 0);

		return getSextet(sextetValue);
	}

	// Scans a sequence for the largest non-null integer value, or returns null
	// if there are no non-null values.
	private static Integer maxInteger(Iterable<Integer> si) {
		Integer result = null;

		for (Integer n : si) {
			if (n == null) {
				// skip nulls
			} else if (result == null) {
				// anything replaces null
				result = n;
			} else {
				// n and result are both non-null
				result = max(result, n);
			}
		}

		return result;
	}

	private static int max(int a, int b) {
		return (a > b) ? a : b;
	}

	// In so many words, ceil(n/6), but without the floating-point part.
	// Returns the minimum number of sextets required to hold n bits.
	private static int ceilOfNDiv6(int n) {
		return (n + 5) / 6;
	}

}
