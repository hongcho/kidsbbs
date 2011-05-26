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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class VT220Screen {
	private static final String TAG = "VT220Screen";
	
	private static final String EUC_KR = "EUC-KR";
	private static final int BUF_SIZE = 1024;
	
	private final Pattern[] PATTERNS = {
		Pattern.compile("\\[(\\d+)$"),
		Pattern.compile("\\[(\\d+);(\\d+)$"),
		Pattern.compile("\\[([012])$"),
	};
	
	private int mWidth = 80;
	private int mHeight = 24;
	
	private int mX = 0;
	private int mY = 0;
	
	private byte[][] mScrBuf;
	private byte[] mBlankLine;
	
	private int mEscState = 0;
	private byte[] mEscBuf = new byte[10];
	private int mEscBufLen = 0;
	
	// Circular buffer.
	private byte[] mInputBuf = new byte[BUF_SIZE];
	private int mInputBufWr = 0;
	private int mInputBufRd = 0;
	
	public int getWidth() { return mWidth; }
	public int getHeight() { return mHeight; }
	
	public VT220Screen(int _width, int _height) {
		mWidth = _width;
		mHeight = _height;
		
		mScrBuf = new byte[mHeight][mWidth];
		mBlankLine = new byte[mWidth];
		for (int i = 0; i < mWidth; ++i) {
			if (i % 10 == 9) {
				mBlankLine[i] = ':';
			} else {
				mBlankLine[i] = '.';
			}
		}
		
		clear();
		
		mEscState = 0;
		mEscBufLen = 0;
	}
	
	public void process(byte[] _buf, int _len) {
		Matcher m;
		int n, x, y;
		L_MAIN: for (int i = 0; i < _len; ++i) {
			byte v = _buf[i];
			
			// Keep X, Y in check
			adjustX();
			adjustY();
			
			// ESC parsing
			switch (mEscState) {
			case 0:
				switch (v) {
				case 13:	// CR
					mX = 0;
					continue L_MAIN;
				case 10:	// LF
					++mY;
					continue L_MAIN;
				case 27:	// ESC
					mEscBuf[mEscBufLen++] = v;
					mEscState = 1;
					continue L_MAIN;
				case 8:	// BS
					--mX;
					continue L_MAIN;
				case 12:	// FF
					clear();
					continue L_MAIN;
				default:
					if ((0 <= v && v < 32) || 127 == v) {	// CNTRL
						continue L_MAIN;
					}
					break;
				}
				break;
			case 1:	// First character after ESC
				switch (v) {
				case '[':
					mEscBuf[mEscBufLen++] = v;
					mEscState = 2;
					continue L_MAIN;
				case '(':
				case ')':
					mEscBuf[mEscBufLen++] = v;
					mEscState = 3;
					continue L_MAIN;
				case '/':
					mEscBuf[mEscBufLen++] = v;
					mEscState = 4;
					continue L_MAIN;
				case '#':
					mEscBuf[mEscBufLen++] = v;
					mEscState = 5;
					continue L_MAIN;
				case 'D':
					scrollDown(1);
					break;
				case 'M':
					scrollUp(1);
					break;
				default:
					mEscBufLen = 0;
					mEscState = 0;
					continue L_MAIN;
				}
				break;
			case 2:	// ESC [
				if (('A' <= v && v <= 'Z') || ('a' <= v && v <= 'z')) {
    				switch (v) {
    				case 'A': case 'B': case 'C': case 'D':
    					n = 1;
    					m = PATTERNS[0].matcher(
    							new String(mEscBuf, 0, mEscBufLen));
    					if (m.find()) {
    						n = Integer.parseInt(m.group(0));
    					}
    					switch (v) {
    					case 'A': mY -= n; break;
    					case 'B': mY += n; break;
    					case 'C': mX += n; break;
    					case 'D': mX -= n; break;
    					}
    					break;
    				case 'H': case 'f':
    					x = 1; y = 1;
    					m = PATTERNS[1].matcher(
    							new String(mEscBuf, 0, mEscBufLen));
    					if (m.find()) {
    						x = Integer.parseInt(m.group(0));
    						y = Integer.parseInt(m.group(1));
    					}
    					mX = x - 1;
    					mY = y - 1;
    					break;
    				case 'K': case 'J':
    					n = 0;
    					m = PATTERNS[2].matcher(
    							new String(mEscBuf, 0, mEscBufLen));
    					if (m.find()) {
    						n = Integer.parseInt(m.group(0));
    					}
    					switch (v) {
    					case 'K':
    						switch (n) {
    						case 0: clearLine(mY, mX, mWidth - mX); break;
    						case 1: clearLine(mY, 0, mX + 1); break;
    						case 2: clearLine(mY, 0, mWidth); break;
    						}
    						break;
    					case 'J':
    						switch (n) {
    						case 0: clearLines(mY, mHeight - mY); break;
    						case 1: clearLines(0, mY + 1); break;
    						case 2: clear(); break;
    						}
    						break;
    					}
    				}
    				mEscBufLen = 0;
    				mEscState = 0;
				} else {
					mEscBuf[mEscBufLen++] = v;
				}
				continue L_MAIN;
			case 3:	// ESC ( or ESC )
			case 4:	// ESC /
			case 5:	// ESC #
				// Consume a byte.
				mEscBufLen = 0;
				mEscState = 0;
				continue L_MAIN;
			default:
				Log.e(TAG, "Unknown state: mEscState = " + mEscState);
				break;
			}
			
			// Now put it in the screen
			mScrBuf[mY][mX++] = v;
			
			// Circular input buffer
			mInputBuf[mInputBufWr++] = v;
			mInputBufWr %= BUF_SIZE;
			if (mInputBufWr == mInputBufRd) {
				mInputBufRd = (mInputBufRd + 1) % BUF_SIZE;
			}
		}
	}
	
	public int getInputBuf(byte[] _buf, int _size) {
		if (mInputBufRd == mInputBufWr) {
			return 0;
		} else if (mInputBufRd < mInputBufWr) {
			int len = mInputBufWr - mInputBufRd;
			if (len > _size) {
				len = _size;
			}
			System.arraycopy(mInputBuf, mInputBufRd, _buf, 0, len);
			return len;
		} else {
			int len0 = BUF_SIZE - mInputBufRd;
			if (len0 > _size) {
				len0 = _size;
			}
			if (len0 > 0) {
				System.arraycopy(mInputBuf, mInputBufRd, _buf, 0, len0);
				_size -= len0;
			}
			int len1 = mInputBufWr;
			if (len1 > _size) {
				len1 = _size;
			}
			if (len1 > 0) {
				System.arraycopy(mInputBuf, 0, _buf, len0, len1);
			}
			return len0 + len1;
		}
	}
	public void consumeInputBuf(int _size) {
		mInputBufRd = (mInputBufRd + _size) % BUF_SIZE;
	}
	
	public byte[] getLine(int _y) {
		return mScrBuf[_y];
	}
	public String dumpLine(int _y) throws IOException {
		return new String(mScrBuf[_y], 0, mWidth, EUC_KR);
	}
	public String dump() throws IOException {
		String d = "";
		for (int i = 0; i < mHeight; ++i) {
			d += String.format("%02d", i) + ": " + dumpLine(i) + "\n";
		}
		return d;
	}
	
	private void clearLine(int _y, int _off, int _len) {
		System.arraycopy(mBlankLine, _off, mScrBuf[_y], _off, _len);
	}
	private void clearLines(int _start, int _count) {
		int last = _start + _count;
		for (int i = _start; i < last; ++i) {
			clearLine(i, 0, mWidth);
		}
	}
	private void clear() {
		clearLines(0, mHeight);
		mX = 0;
		mY = 0;
	}
	
	private void scrollUp(int _n) {
		System.arraycopy(mScrBuf, _n, mScrBuf, 0, mHeight - _n);
		clearLines(mHeight - _n, _n);
	}
	private void scrollDown(int _n) {
		System.arraycopy(mScrBuf, 0, mScrBuf, _n, mHeight - _n);
		clearLines(0, _n);
	}
	
	private void adjustX() {
		if (mX < 0) {
			mX = 0;
		} else {
			while (mX >= mWidth) {
				++mY;
				mX -= mWidth;
			}
		}
	}
	private void adjustY() {
		if (mY < 0) {
			int n = -mY;
			if (n > mHeight) {
				n = mHeight;
			}
			scrollDown(n);
			mY = 0;
		} else if (mY >= mHeight) {
			int n = mY - mHeight + 1;
			if (n > mHeight) {
				n = mHeight;
			}
			scrollUp(n);
			mY = mHeight - 1;
		}
	}
}
