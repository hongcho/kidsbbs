// Copyright (c) 2010-2011, Younghong "Hong" Cho <hongcho@sori.org>.
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
package org.sori.kidsbbs.data;

public class BoardInfo {
	private String mTabname;
	private String mTitle;
	private int mType;
	private String mBoard;
	private int mUnreadCount;

	public String getTabname() { return mTabname; }
	public String getTitle() { return mTitle; }
	public int getType() { return mType; }
	public String getBoard() { return mBoard; }
	public int getUnreadCount() { return mUnreadCount; }
	public void setUnreadCount(int _count) { mUnreadCount = _count; }

	public static final String buildTabname(String _board, int _type) {
		return "b" + _type + "_" + _board;
	}

	public static final String[] parseTabname(String _tabname) {
		final String[] parsed = _tabname.split("_");
		parsed[0] = parsed[0].substring(1);
		return parsed;
	}

	public BoardInfo(String _tabname, String _title) {
		mTabname = _tabname;
		mTitle = _title;
		mUnreadCount = 0;

		final String[] parsed = parseTabname(mTabname);
		mType = Integer.parseInt(parsed[0]);
		mBoard = parsed[1];
	}

	@Override
	public String toString() {
		return mTitle;
	}
}
