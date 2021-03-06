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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
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
	private Service.Listener createMutualStopListener(final String tag) {
		Service.Listener listener = new Service.Listener() {

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
		return listener;
	}

	private abstract static class PacketWriterServiceFactory {
		abstract PacketWriterService create(Main main);
	}

	Main(long interval, PacketWriterServiceFactory writerFactory) {
		Set<Service> services = new HashSet<>();

		writer = writerFactory.create(this);
		services.add(writer);

		watchdog = createWatchdog(interval);
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

	Main(final long interval) {
		this(interval, new PacketWriterServiceFactory() {
			@Override
			PacketWriterService create(Main main) {
				return main.createPacketWriter();
			}
		});
	}

	Main(final String host, final int port, final long interval) {
		this(interval, new PacketWriterServiceFactory() {
			@Override
			PacketWriterService create(Main main) {
				return main.createPacketWriter(host, port);
			}
		});
	}

	private KeyPressWindowService createKeyPressWindow() {
		KeyPressWindowService keyPressWindow = new KeyPressWindowService(this);
		keyPressWindow.addListener(createMutualStopListener("KeyPressWindowService"), MoreExecutors.directExecutor());
		return keyPressWindow;
	}

	private WatchdogService createWatchdog(long intervalMillis) {
		WatchdogService watchdog = new WatchdogService(this, intervalMillis);
		watchdog.reset();
		watchdog.addListener(createMutualStopListener("WatchdogService"), MoreExecutors.directExecutor());
		return watchdog;
	}

	private PacketWriterService createPacketWriter(String host, int port) {
		PacketWriterService writer = new TcpPacketWriterService(this, host, port);
		writer.addListener(createMutualStopListener("TcpPacketWriterService"), MoreExecutors.directExecutor());
		return writer;
	}

	private PacketWriterService createPacketWriter() {
		PacketWriterService writer = new StdoutPacketWriterService(this);
		writer.addListener(createMutualStopListener("StdoutPacketWriterService"), MoreExecutors.directExecutor());
		return writer;
	}

	public static void main(String[] args) {
		Map<String, String> parameters = new HashMap<>();

		boolean hasMode = false, hasHost = false, hasPort = false, hasInterval = false;
		String mode = null, host = null;
		Integer port = null;
		Long interval = null;

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

				case "interval":
					ensureNotSet("interval", hasInterval);
					hasInterval = true;
					interval = parseLongParameter("interval", value);
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

		if (!hasInterval) {
			interval = 1000L;
		}

		if (mode.equals("stdout")) {
			if (hasHost || hasPort) {
				throw new IllegalArgumentException("Parameters 'host' and 'port' must be unset when in stdout mode");
			}

			new Main(interval);
		} else if (mode.equals("tcp")) {
			if (!hasPort) {
				throw new IllegalArgumentException("Parameter 'port' must be set when in tcp mode");
			}

			new Main(host, port, interval);
		}

	}

	private static int parseIntParameter(String paramName, String str) {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Parameter '" + paramName + "' must be an integer");
		}
	}

	private static long parseLongParameter(String paramName, String str) {
		try {
			return Long.parseLong(str);
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

	void keyUpdate(int keyCode, boolean b) {
		if (keysState.update(keyCode, b)) {
			Packet p = keysState.getAsPacket();

			writer.sendPacket(p);
			window.setLabelText("State: " + p.getData());
		}
	}

	void watchdogTimeout() {
		if (writer.isRunning()) {
			writer.hintSendKeepalive();
		}
	}

	void writingPacket(Packet packet) {
		watchdog.reset();
	}

	void windowClosing() {
		manager.stopAsync();
	}
	
	private static URI currentJarLocationUri() {
		URL thisResourceUrl = Main.class.getProtectionDomain().getCodeSource().getLocation();
		try {
			return thisResourceUrl.toURI().normalize();
		} catch (URISyntaxException e) {
			// We don't expect this to ever happen.
			throw new RuntimeException(e);
		}	
	}
	
	private static URI currentWorkingDirectoryUri() {
		return Paths.get(".").toAbsolutePath().toUri().normalize();
	}
	
	private static URI relativeCurrentJarLocationUri() {
		URI jar = currentJarLocationUri();
		URI wd = currentWorkingDirectoryUri();
		return wd.relativize(jar);
	}

	
	private static void usage(String message) {		
		String jarPath = relativeCurrentJarLocationUri().getPath().toString();
		String command = "COMMAND";
		if(jarPath.toLowerCase().endsWith(".jar")) {
			command = "java -jar " + jarPath;
		}
		
		String[] lines = new String[] { message,
				"Usage",
				"-----",
				"",
				"    COMMAND [mode=MODE] \\",
				"        [host=ADDRESS] [port=PORTNUMBER] \\",
				"        [interval=MILLISECONDS]",
				"",
				"Examples",
				"--------",
				"",
				"    COMMAND",
				"                    # Open in stdout mode (implied by",
				"                    # omission of PORT)",
				"",
				"    COMMAND port=6761",
				"                    # Open in tcp mode (implied by presence of",
				"                    # PORT), listen for client on port",
				"                    # 6761 on any local address",
				"",
				"    COMMAND host=localhost port=6761",
				"                    # Same, except only listen on",
				"                    # localhost",
				"",
				"Parameters",
				"----------",
				"",
				"The order of parameters is not important.",
				"",
				"mode=MODE",
				"    (stdout or tcp; default is tcp if port is present or stdout",
				"    otherwise) Determines whether output goes to standard output or to a",
				"    TCP connection accepted on port. This setting is strictly optional;",
				"    the presence or absence of port implies the mode setting.",
				"",
				"host=ADDRESS",
				"    (tcp mode only; default is all local addresses) Sets the address on",
				"    which the tcp-mode service accepts a connection.",
				"",
				"port=PORTNUMBER",
				"    (tcp mode only; 0 .. 65535; no default) Sets the port on which the",
				"    tcp-mode service accepts a connection.",
				"",
				"interval=MILLISECONDS",
				"    (default 1000, meaning 1 second) Sets the interval, in milliseconds,",
				"    of a watchdog timer that forces the output of a blank packet if the",
				"    output remains idle for that amount of time. A non-positive value",
				"    disables the timer, but doing this is discouraged: This watchdog",
				"    timer's behavior serves partly as a keepalive for the connection,",
				"    should one be necessary, partly to allow the tcp mode to detect a",
				"    disconnect (by way of a failed write) without receiving actual",
				"    input, and partly to allow the receiving end of the connection,",
				"    which unfortunately might need to be implemented based on an",
				"    uninterruptible blocking read, to allow it to recheck its loop",
				"    variables with a higher frequency, resolving some situations that",
				"    would otherwise hang the receiver.",

			};

		for (String line : lines) {
			line = line.replaceAll("\\bCOMMAND\\b", command);
			System.err.println(line);
		}
		System.exit(2);
	}


}
