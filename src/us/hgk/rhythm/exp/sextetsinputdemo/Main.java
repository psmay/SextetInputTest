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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.Service.State;

public class Main {
	private static final Logger log = Logger.getLogger(Main.class.getName());

	private KeysState keysState = new KeysState();
	
	private ServiceManager manager;

	private PacketWriterService writer;
	private WatchdogService watchdog;
	private KeyPressWindowService window;

	// Gets a Service.Listener that requests the ServiceManager to stop on
	// receipt of a failed, stopping, or terminated event from any of the
	// services it manages.
	private Service.Listener getMutualStoppingListener(final String tag) {
		Service.Listener stoppingListener = new Service.Listener() {

			@Override
			public void running() {
				log.finer("Running event received from " + tag);
			}

			@Override
			public void starting() {
				log.finer("Starting event received from " + tag);
			}

			@Override
			public void terminated(State from) {
				log.finer("Terminated event received from " + tag + " at " + from);
				manager.stopAsync();
			}

			@Override
			public void failed(State from, Throwable failure) {
				log.finer("Failed event received from " + tag + " at " + from);
				manager.stopAsync();
			}

			@Override
			public void stopping(State from) {
				log.finer("Stopping event received from " + tag + " at " + from);
				manager.stopAsync();
			}
		};
		return stoppingListener;
	}

	private abstract static class PacketWriterServiceFactory {
		abstract PacketWriterService create(Main main);
	}

	Main(PacketWriterServiceFactory writerFactory) {

		Set<Service> services = new HashSet<>();

		writer = writerFactory.create(this);
		services.add(writer);

		watchdog = createWatchdog();
		services.add(watchdog);

		window = createKeyPressWindow();
		services.add(window);

		manager = new ServiceManager(services);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					manager.stopAsync().awaitStopped(5, TimeUnit.SECONDS);
				} catch (TimeoutException e) {
					// Stopping nicely timed out; allow the runtime to pull the
					// plug
				}
			}
		});

		manager.startAsync();
	}

	Main() {
		this(new PacketWriterServiceFactory() {
			@Override
			PacketWriterService create(Main main) {
				return main.createPacketWriter();
			}
		});
	}

	Main(final String host, final int port) {
		this(new PacketWriterServiceFactory() {
			@Override
			PacketWriterService create(Main main) {
				return main.createPacketWriter(host, port);
			}
		});
	}

	private KeyPressWindowService createKeyPressWindow() {
		KeyPressWindowService keyPressWindow = new KeyPressWindowService(this);
		keyPressWindow.addListener(getMutualStoppingListener("KeyPressWindowService"), MoreExecutors.directExecutor());
		return keyPressWindow;
	}

	private WatchdogService createWatchdog() {
		WatchdogService watchdog = new WatchdogService(this);
		watchdog.reset();
		watchdog.addListener(getMutualStoppingListener("WatchdogService"), MoreExecutors.directExecutor());
		return watchdog;
	}

	private PacketWriterService createPacketWriter(String host, int port) {
		PacketWriterService writer = new TcpPacketWriterService(this, host, port);
		writer.addListener(getMutualStoppingListener("TcpPacketWriterService"), MoreExecutors.directExecutor());
		return writer;
	}

	private PacketWriterService createPacketWriter() {
		PacketWriterService writer = new StdoutPacketWriterService(this);
		writer.addListener(getMutualStoppingListener("StdoutPacketWriterService"), MoreExecutors.directExecutor());
		return writer;
	}

	public static void main(String[] args) {
		Map<String, String> parameters = new HashMap<>();

		boolean hasMode = false, hasHost = false, hasPort = false;
		String mode = null, host = null;
		Integer port = null;

		try {
			for (String arg : args) {

				String key = arg;
				String value = null;

				String[] parts = splitKeyEqualsValue(arg);
				key = parts[0];
				value = parts[1];

				switch (key) {
				case "mode":
					ensureNotSet("mode", hasMode);
					hasMode = true;
					mode = value;
					break;

				case "host":
					ensureNotSet("host", hasHost);
					hasHost = true;
					host = value;
					break;

				case "port":
					ensureNotSet("port", hasPort);
					hasPort = true;
					port = parseIntParameter("port", value);
					break;

				default:
					throw new IllegalArgumentException("Unrecognized parameter name '" + key + "'");
				}

				if (parameters.containsKey(key)) {
					throw new IllegalArgumentException("The parameter '" + key + "' was set more than once");
				}

				parameters.put(key, value);
			}
		} catch (IllegalArgumentException e) {
			usage("Parameter error: " + e.getMessage());
		}

		if (hasMode) {
			switch (mode) {
			case "tcp":
			case "stdout":
				break;
			default:
				throw new IllegalArgumentException("Parameter 'mode' must be set to 'tcp' or 'stdout' or be omitted");
			}
		} else {
			mode = hasPort ? "tcp" : "stdout";
		}

		if (mode.equals("stdout")) {
			if (hasHost || hasPort) {
				throw new IllegalArgumentException("Parameters 'host' and 'port' must be unset when in stdout mode");
			}

			new Main();
		} else if (mode.equals("tcp")) {
			if (!hasPort) {
				throw new IllegalArgumentException("Parameter 'port' must be set when in tcp mode");
			}
			
			new Main(host, port);
		}

	}

	private static int parseIntParameter(String paramName, String str) {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Parameter '" + paramName + "' must be an integer");
		}
	}

	private static void ensureNotSet(String paramName, boolean hasParam) {
		if (hasParam) {
			throw new IllegalArgumentException("'" + paramName + "' was set more than once");
		}
	}

	private static String[] splitKeyEqualsValue(String arg) {
		String[] parts = arg.split("=", 2);
		if (parts.length != 2) {
			throw new IllegalArgumentException(
					"Could not understand parameter '" + arg + "' (parameters must be in 'key=value' format)");
		}
		return parts;
	}

	private static void usage(String message) {
		String[] lines = new String[] { message, "Usage:", "	thisprogram [mode=tcp] [host=HOSTNAME] port=PORTNUMBER",
				"	thisprogram [mode=stdout]", "Examples:", "	thisprogram mode=stdout		# Open in stdout mode",
				"	thisprogram			# Same (mode=stdout is default if", "					# port is missing)", "",
				"	thisprogram mode=tcp port=6761	# Open in tcp mode, listen for",
				"					# clients on port 6761 of any local", "					# address", "",
				"	thisprogram port=6761		# Same (mode=tcp is default if port",
				"					# is specified)", "", "	thisprogram host=localhost port=6761",
				"					# Same, except only listen on", "					# address localhost", };

		for (String line : lines) {
			System.err.println(line);
		}
		System.exit(2);
	}

	void keyUpdate(int keyCode, boolean b) {
		if(keysState.update(keyCode, b)) {
			Packet p = keysState.getAsPacket();
			
			writer.sendPacket(p);
			window.setLabelText("State: " + p.getData());
		}
	}

	public void watchdogTimeout() {
		if (writer.isRunning()) {
			writer.hintSendKeepalive();
		}
	}

	public void writingPacket(Packet packet) {
		watchdog.reset();
	}

	void windowClosing() {
		manager.stopAsync();
	}
}
