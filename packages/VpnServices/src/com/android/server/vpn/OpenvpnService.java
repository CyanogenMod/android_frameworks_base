/*
 * Copyright (C) 2009, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.vpn;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.vpn.OpenvpnProfile;
import android.net.vpn.VpnManager;
import android.os.SystemProperties;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * The service that manages the openvpn VPN connection.
 */
class OpenvpnService extends VpnService<OpenvpnProfile> {
    private static final String OPENVPN_DAEMON = "openvpn";
    private static final String MTPD = "mtpd";
    private static final String USE_INLINE = "[[INLINE]]";
    private static final String USE_KEYSTORE = "[[ANDROID]]";
    private static final String TAG = OpenvpnService.class.getSimpleName();
    private static int count = 0;

    private final String socketName = OPENVPN_DAEMON + getCount();

    private transient OpenvpnThread thread = null;

    private transient String mPassword;
    private transient String mUsername;

    private synchronized static String getCount() {
	    return Integer.toString(count++);
    }

    @Override
    protected void connect(String serverIp, String username, String password)
            throws IOException {
        OpenvpnProfile p = getProfile();
	ArrayList<String> args = new ArrayList<String>();

	mUsername = username;
	mPassword = password;

	args.add(OPENVPN_DAEMON);
	args.add("--dev"); args.add("tun");
	args.add("--remote"); args.add(serverIp);
	args.add("--nobind");
	args.add("--proto"); args.add(p.getProto());
	args.add("--client");
	args.add("--rport"); args.add(p.getPort());
	args.add("--ca"); args.add(USE_INLINE); args.add(USE_KEYSTORE + p.getCAFile());
	args.add("--cert"); args.add(USE_INLINE); args.add(USE_KEYSTORE + p.getCertFile());
	args.add("--key"); args.add(USE_INLINE); args.add(USE_KEYSTORE + p.getKeyFile());
	args.add("--persist-tun");
	args.add("--persist-key");
	args.add("--management"); args.add("/dev/socket/" + socketName); args.add("unix");
	args.add("--management-hold");
	if (p.getUseCompLzo()) {
	    args.add("--comp-lzo");
	}
	if (p.getUserAuth()) {
	    args.add("--auth-user-pass");
	    args.add("--management-query-passwords");
	}
	if (p.getSupplyAddr()) {
	    args.add("--ifconfig"); args.add(p.getLocalAddr()); args.add(p.getRemoteAddr());
	}

	DaemonProxy mtpd = startDaemon(MTPD);
	mtpd.sendCommand(args.toArray(new String[args.size()]));
    }

    @Override
    protected void disconnect() {
	if (thread != null)
	    thread.disconnectAndWait();
    }

    @Override
    void waitUntilConnectedOrTimedout() throws IOException {
	thread = new OpenvpnThread();
	thread.openvpnStart();
	thread.waitConnect(60);
	setVpnStateUp(true);
    }

    @Override
    protected void stopPreviouslyRunDaemons() {
        stopDaemon(MtpdHelper.MTPD);
    }

    @Override
    protected void recover() {
	try {
	    thread = new OpenvpnThread();
	    thread.openvpnStart();
	} catch (IOException e) {
	    onError(e);
	}
   }

    void startConnectivityMonitor() {
	/* Openvpn is completely event driven, so we don't need
	 * a polling monitor at all, so do nothing here */
    }

    private class OpenvpnThread extends Thread {
	InputStream in;
	OutputStream out;
	LocalSocket mSocket;

	boolean finalDisconnect = false;
	boolean firstConnect = false;
	boolean disconnecting = false;
	boolean passwordError = false;

	boolean SFbool;
	volatile String SFreason;
	String vpnState = "WAIT"; // initial state

	OpenvpnThread() throws IOException {
	    openSocket();
	    in = mSocket.getInputStream();
	    out = mSocket.getOutputStream();
	}

	public void openvpnStart() throws IOException {
	    super.start();
	    send("state on");	// make state dynamic
	    send("log on");	// dynamically log over the socket
	    send("hold off");	// don't hold for subsequent reconnects
	    send("hold release");	// release from hold
	    send("bytecount 2");  // need this to update the monitor
	}

	public synchronized void disconnectAndWait() {
	    try {
		disconnecting = true;
		send("signal SIGTERM");
		while (!finalDisconnect)
		    this.wait();
	    } catch(Exception e) {
		    // we're done
	    }
	}

	public synchronized void waitConnect(long seconds) throws IOException {
	    long endTime = System.currentTimeMillis() + seconds * 1000;
	    long wait;
	    while (!isConnected() && (wait = (endTime -  System.currentTimeMillis())) > 0) {
		try {
		    this.wait(wait);
		} catch(InterruptedException e) {
		    // do nothing
		}
		if (passwordError)
		    throw new VpnConnectingError(VpnManager.VPN_ERROR_AUTH);
	    } 
	    if (!isConnected())
		throw new VpnConnectingError(VpnManager.VPN_ERROR_CONNECTION_FAILED);
	    firstConnect = true;
	}

	private boolean isConnected() {
	    return vpnState.equals("CONNECTED");
	}

	private void openSocket() throws IOException {
	    LocalSocket s = new LocalSocket();
	    LocalSocketAddress a = new LocalSocketAddress(socketName,
							  LocalSocketAddress.Namespace.RESERVED);
	    IOException excp = null;
	    for (int i = 0; i < 10; i++) {
		try {
		    s.connect(a);
		    mSocket = s;
		    return;
		} catch (IOException e) {
		    excp = e;
		    try {
			Thread.currentThread().sleep(500);
		    } catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		    }
		}
	    }
	    throw excp;
	}

	private synchronized boolean waitForSuccessOrFail() {
	    SFreason = null;
	    try {
		while (SFreason == null) {
		    this.wait();
		}
		return SFbool;
	    } catch(InterruptedException e) {
		return false;
	    }
	}

	private synchronized void signalSuccessOrFail(boolean success, String reason) {
	    SFbool = success;
	    SFreason = reason;
	    this.notifyAll();
	}
			

	private synchronized void sendAsync(String str) throws IOException {
	    str += "\n";
	    out.write(str.getBytes());
	    out.flush();
	}

	private boolean send(String str) throws IOException {
	    sendAsync(str);
	    return waitForSuccessOrFail();
	}

	private synchronized void signalState(String s) {
	    // state strings come as <date in secs>, <state>, <other stuff>
	    int first = s.indexOf(',');
	    if (first == -1)
		return;
	    int second = s.indexOf(',', first + 1);
	    if (second == -1)
		return;
	    String state = s.substring(first + 1, second);

	    /*
	     * state can be:
	     *
	     *
	     * CONNECTING    -- OpenVPN's initial state.
	     * WAIT          -- (Client only) Waiting for initial response
	     *                  from server.
	     * AUTH          -- (Client only) Authenticating with server.
	     * GET_CONFIG    -- (Client only) Downloading configuration options
	     *                 from server.
	     * ASSIGN_IP     -- Assigning IP address to virtual network
	     *                 interface.
	     * ADD_ROUTES    -- Adding routes to system.
	     * CONNECTED     -- Initialization Sequence Completed.
	     * RECONNECTING  -- A restart has occurred.
	     * EXITING       -- A graceful exit is in progress.
	     *
	     * Really all we care about is connected or not
	     */
	    vpnState = state;
	    this.notifyAll();
	    if (state.equals("EXITING") && firstConnect && !disconnecting)
		onError(new IOException("Connection Closed"));
	}

	private synchronized void signalPassword(String s) throws IOException {
	    /* message should be Need '<auth type>' password
	     * but coult be Verification Failed: '<auth type'
	     */

	    int first = s.indexOf('\'');
	    int second = s.indexOf('\'', first + 1);
	    final String authType = s.substring(first + 1, second);

	    /* AuthType can be one of
	     *
	     * "Auth"	- regular client server authentication
	     * "Private Key" - password for private key (unimplemented)
	     */

	    if (s.startsWith("Need")) {
		/* we're in the processor thread, so we have to send
		 * these asynchronously to avoid a deadlock */
		sendAsync("username '" + authType +"' '" + mUsername + "'");
		sendAsync("password '" + authType +"' '" + mPassword + "'");
	    } else {
		// must be signalling authentication failure
		passwordError = true;
		this.notifyAll();
	    }
	}

	private void signalBytecount(String s) {
	    int index = s.indexOf(',');
	    if (index == -1)
		// no , in message, ignore it
		return;

	    String in = s.substring(0, index);
	    String out = s.substring(index+1);
	    vpnStateUpdate(Long.parseLong(in), Long.parseLong(out));
	}

	private void signalLog(String s) {
	    //log format is <date in secs>,<severity>,<message>
	    int first = s.indexOf(',');
	    if (first == -1)
		return;
	    int second = s.indexOf(',', first + 1);
	    if (second == -1)
		return;
	    String message = s.substring(second + 1);
	    Log.i("openvpn", message);
	}

	private void parseLine(String s) throws IOException {
	    int index = s.indexOf(':');
	    if (index == -1)
		// no : in message, ignore it
		return;

	    String token = s.substring(0, index);
	    String body = s.substring(index +1);

	    if (token.equals(">INFO")) {
		// This is the starting string, just skip it
	    } else if (token.equals("SUCCESS")) {
		signalSuccessOrFail(true, body);
	    } else if (token.equals("ERROR")) {
		signalSuccessOrFail(false, body);
	    } else if (token.equals(">STATE")) {
		signalState(body);
	    } else if (token.equals(">FATAL")) {
		signalState("EXITING," + body);
	    } else if (token.equals(">PASSWORD")) {
		signalPassword(body);
	    } else if (token.equals(">LOG")) {
		signalLog(body);
	    }else if (token.equals(">HOLD")) {
		// just warning us we're in a hold state, ignore
	    } else if (token.equals(">BYTECOUNT")) {
		signalBytecount(body);
	    } else {
		Log.w(TAG, "Unknown control token:\"" + token + "\"");
	    }
	}
	    
	public void run() {

	    System.out.println("THREAD " + this + " RUNNING");
	    
	    try {
		int c;
		StringBuffer s = new StringBuffer();
		while (true) {
		    c = in.read();
		    if (c == -1)
			throw new IOException("End of Stream");
		    if (c == '\n') {
			parseLine(s.toString());
			s = new StringBuffer();
			continue;
		    }
		    if (c == '\r')
			continue;
		    s.append((char)c);
		}
	    } catch(IOException e) {
		// terminate
	    } finally {
		synchronized(this) {
		    finalDisconnect = true;
		    this.notifyAll();
		}
		System.out.println("THREAD " + this + " TERMINATED");
	    }
	}
    }
}
