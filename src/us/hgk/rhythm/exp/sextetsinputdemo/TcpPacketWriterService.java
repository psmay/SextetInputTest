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

import static com.google.common.base.Preconditions.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

public class TcpPacketWriterService extends PacketWriterService {
	private static final Logger log = Logger.getLogger(TcpPacketWriterService.class.getName());
	private String host;
	private int port;

	private BufferedWriter out;

	TcpPacketWriterService(Main main, String host, int port) {
		super(main);
		this.host = host;
		checkArgument(isValidPort(port), "%d is not a valid port number", port);
		this.port = port;
	}

	private static boolean isValidPort(int port) {
		return port >= 0 && port <= 0xFFFF;
	}

	@Override
	protected void run() throws Exception {
		try (ServerSocket serverSocket = getServerSocket();
				Socket socket = acceptClient(serverSocket);
				OutputStream os = socket.getOutputStream();
				OutputStreamWriter osw = new OutputStreamWriter(os);
				BufferedWriter bw = new BufferedWriter(osw);) {

			log.finer("Shutting down input side of socket");
			socket.shutdownInput();
			out = bw;

			try {
				packetWriterLoopBody();
			} catch (SocketException e) {
				log.warning("Socket is no longer connected: " + e.getMessage());
			}
		} finally {
			out = null;
		}
	}

	private Socket acceptClient(ServerSocket serverSocket) throws IOException {
		log.info("Waiting for client");
		int oldTimeout = serverSocket.getSoTimeout();
		try {
			serverSocket.setSoTimeout(250);
			while (isRunning()) {
				try {
					return serverSocket.accept();
				} catch (SocketTimeoutException e) {
					// Check conditions, then continue waiting
					continue;
				}
			}
			throw new IllegalStateException("Stopped waiting for client because writer is stopping");
		} finally {
			serverSocket.setSoTimeout(oldTimeout);
		}
	}

	private ServerSocket getServerSocket() throws IOException {
		if (host == null) {
			log.info("Opening server socket on port " + port);
			return new ServerSocket(port);
		} else {
			InetAddress address = InetAddress.getByName(host);
			log.info("Opening server socket on host " + address.toString() + ", port " + port);
			// -1 backlog gives the same length as the port-only ctor.
			return new ServerSocket(port, -1, InetAddress.getByName(host));
		}
	}

	@Override
	protected void outputPacket(Packet packet) throws IOException {
		out.write(packet.getData());
		out.newLine();
		out.flush();
	}

}
