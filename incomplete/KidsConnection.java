// Copyright (c) 2011, Younghong "Hong" Cho <hongcho@sori.org>.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
//   1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//   2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
//   3. Neither the name of the organization nor the names of its contributors
// may be used to endorse or promote products derived from this software
// without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
// THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package org.sori.sshtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;

public class KidsConnection {
	private static final int BUF_SIZE = 8192;
	private static final int TIMEOUT = 2 * 1000;
	private static final int WIDTH = 80;
	private static final int HEIGHT = 24;
	
	private static final String STR_HOST = "203.231.233.47";
	private static final String STR_KIDS = "kids";
	private static final String STR_VT100 = "vt100";
	
	private String mUser;
	private String mPass;
	
	private Connection mConn = null;
	private Session mSess = null;
	private InputStream mStdout;
	private InputStream mStderr;
	
	private VT220Screen mScreen;
	private byte[] mBuf = new byte[BUF_SIZE];
	private int mBufLen = 0;
	
	private static final int S_NOTCONNECTED = 0;
	private static final int S_CONNECTED = 1;
	private static final int S_LIST = 10;
	private static final int S_POST = 11;
	private int mState = S_NOTCONNECTED;
	private String mStateArg = "";
	
	protected void finalize() throws Throwable {
		close();
	}
	
	// Custom exceptions
	@SuppressWarnings("serial")
	public class TimeoutException extends IOException {
		public TimeoutException() { super("Timeout"); }
	}
	@SuppressWarnings("serial")
	public class AuthenticationException extends IOException {
		public AuthenticationException() { super("Authentication failed"); }
	}
	@SuppressWarnings("serial")
	public class EOFException extends IOException {
		public EOFException() { super("EOF"); }
	}
	
	public void open(String _user, String _pass) throws IOException {
		mUser = _user;
		mPass = _pass;
		
		mConn = new Connection(STR_HOST);
		mConn.connect();
		if (!mConn.authenticateWithNone(STR_KIDS)) {
			throw new AuthenticationException();
		}
		mSess = mConn.openSession();
		mSess.requestPTY(STR_VT100, WIDTH, HEIGHT, 640, 480, null);
		mSess.startShell();
		
		mScreen = new VT220Screen(WIDTH, HEIGHT);
		
		mStdout = mSess.getStdout();
		mStderr = mSess.getStderr();
		
		mState = S_CONNECTED;
	}
	
	public void close() {
		if (mSess != null) {
			mSess.close();
		}
		if (mConn != null) {
			mConn.close();
		}
		mState = S_NOTCONNECTED;
	}
	
	private void read() throws IOException {
		// Check for conditions.
		if (mStdout.available() == 0 && mStderr.available() == 0) {
			int conditions = mSess.waitForCondition(
					ChannelCondition.STDOUT_DATA |
					ChannelCondition.STDERR_DATA |
					ChannelCondition.EOF,
					TIMEOUT);
			if ((conditions & ChannelCondition.TIMEOUT) != 0) {
				throw new TimeoutException();
			}
			if ((conditions & ChannelCondition.EOF) != 0) {
				if ((conditions & (ChannelCondition.STDOUT_DATA |
						ChannelCondition.STDERR_DATA)) == 0) {
					throw new EOFException();
				}
			}
		}
		
		// Read from the streams.
		while (mStderr.available() > 0 && mBufLen < BUF_SIZE) {
			mBufLen += mStderr.read(mBuf, mBufLen, BUF_SIZE - mBufLen);
		}
		while (mStdout.available() > 0 && mBufLen <= BUF_SIZE) {
			mBufLen += mStdout.read(mBuf, mBufLen, BUF_SIZE - mBufLen);
		}
	}
	
	public String dump() throws IOException {
		return mScreen.dump();
	}
	
	public void process() throws IOException {
		try {
			read();
			if (mBufLen > 0) {
				mScreen.process(mBuf, mBufLen);
				mBufLen = 0;
			}
		} catch (IOException e) {
			throw e;
		}
	}
}
