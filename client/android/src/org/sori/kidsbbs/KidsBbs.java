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
package org.sori.kidsbbs;

import android.net.Uri;

public class KidsBbs {
	//private static final String TAG = "KidsBbs";

	public interface PackageBase {
		String MAIN = "org.sori.kidsbbs.";
		String PARAM = MAIN + "param.";
		String ALARM = MAIN + "alarm.";
		String BCAST = MAIN + "broadcast.";
		String PROVIDER = MAIN + "provider";
	}

	public interface UrlString {
		String BASE = "http://sori.org/kids/kids.php?_o=1&";
		String BLIST = BASE;
		String PLIST = BASE + "m=plist&";
		String LIST = BASE + "m=list&";
		String TLIST = BASE + "m=tlist&";
		String THREAD = BASE + "m=thread&";
		String USER = BASE + "m=user&";
	}

	public interface IntentUri {
		String BASE_STRING = "content:/kidsbbs/";
		Uri TLIST = Uri.parse(BASE_STRING + "tlist");
		Uri THREAD = Uri.parse(BASE_STRING + "thread");
		Uri USER = Uri.parse(BASE_STRING + "user");
		Uri TVIEW = Uri.parse(BASE_STRING + "tview");
	}

	public interface ParamName {
		String BTITLE = "bt";
		String BOARD = "b";
		String TYPE = "t";
		String THREAD = "id";
		String USER = "u";
		String SEQ = "p";
		String START = "s";
		String COUNT = "n";
		String TABNAME = "tn";
		String TTITLE = "tt";
		String VTITLE = "vt";
	}

	public interface NotificationType {
		int NEW_ARTICLE = 0;
	}

	public interface Settings {
		int CONN_TIMEOUT = 30 * 1000; // 30 seconds
		int MAX_DAYS = 7;
		int MIN_ARTICLES = 10;
		int MAX_ARTICLES = 300;
		int MAX_FIRST_ARTICLES = 100;
		String KST_DIFF = "'-9 hours'";
		String MAX_TIME = "'-" + MAX_DAYS + " days'";
	}
}