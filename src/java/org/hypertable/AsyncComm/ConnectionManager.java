/**
 * Copyright 2007 Doug Judd (Zvents, Inc.)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.hypertable.AsyncComm;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

import org.hypertable.Common.Error;

public class ConnectionManager implements Runnable {

    static final Logger log = Logger.getLogger("org.hypertable.AsyncComm");

    static final long RETRY_INTERVAL_MS = 10000;

    public ConnectionManager(String waitMessage) {
	mWaitMessage = waitMessage;
    }

    public void Initiate(Comm comm, InetSocketAddress addr, long timeout) {
	mHandler = new Callback(comm, addr, timeout);
	mThread = new Thread(this, "Connection Manager");
	mThread.start();
    }

    public boolean WaitForConnection(long maxWaitSecs) throws InterruptedException {
	long elapsed =0;
	long starttime = System.currentTimeMillis();
	long maxWaitMs = maxWaitSecs * 1000;

	while (elapsed < maxWaitMs) {

	    if (mHandler.WaitForConnection(maxWaitMs-elapsed))
		return true;

	    elapsed = System.currentTimeMillis() - starttime;
	}
	return false;
    }

    public void run() {
	long sendtime = 0;
	long elapsed;

	while (!Thread.interrupted()) {

	    try {

		elapsed = System.currentTimeMillis() - sendtime;

		if (elapsed < RETRY_INTERVAL_MS) {
		    synchronized (this) {
			wait(RETRY_INTERVAL_MS - elapsed);
		    }
		}		    

		mHandler.SendConnectRequest();
		sendtime = System.currentTimeMillis();

		while (mHandler.WaitForEvent())
		    ;

		System.err.println(mWaitMessage);
	    }
	    catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    public static class Callback implements CallbackHandler {

	public Callback(Comm comm, InetSocketAddress addr, long timeout) {
	    mComm = comm;
	    mAddr = addr;
	    mTimeout = timeout;
	    mConnected = false;
	}

	public void handle(Event event) {

	    //log.fine(event.toString());

	    synchronized (this) {
		if (event.type == Event.Type.CONNECTION_ESTABLISHED) {
		    mConnected = true;
		    notifyAll();
		}
		else {
		    mConnected = false;
		    log.warning(event.toString());		    
		    notifyAll();
		}
	    }
	}

	/**
	 */
	public void SendConnectRequest() {

	    int error = mComm.Connect(mAddr, mTimeout, this);
	    
	    if (error == Error.COMM_ALREADY_CONNECTED) {
		synchronized (this) {
		    mConnected = true;
		    notifyAll();
		}
	    }
	    else if (error != Error.OK)
		log.severe("Connect to " + mAddr + " failed - " + Error.GetText(error));
	}

	public synchronized boolean WaitForConnection(long maxWaitMs) throws InterruptedException {
	    if (mConnected)
		return true;
	    wait(maxWaitMs);
	    return mConnected;
	}

	public synchronized boolean WaitForEvent() throws InterruptedException {
	    wait();
	    return mConnected;
	}

	private Comm                mComm;
	private InetSocketAddress   mAddr;
	private long                mTimeout;
	private boolean             mConnected = false;
    }

    private Callback mHandler;
    private Thread   mThread;
    private String   mWaitMessage;
}
