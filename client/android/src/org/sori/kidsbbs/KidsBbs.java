// Copyright (c) 2010, Younghong "Hong" Cho <hongcho@sori.org>.
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class KidsBbs extends Activity {
	public static final String PKG_BASE = "org.sori.kidsbbs.";
	
	private static final String URL_BASE = "http://sori.org/kids/kids.php?_o=1&";
	public static final String URL_BLIST = URL_BASE; 
	public static final String URL_LIST = URL_BASE + "m=list&";
	public static final String URL_TLIST = URL_BASE + "m=tlist&";
	public static final String URL_THREAD = URL_BASE + "m=thread&";
	public static final String URL_USER = URL_BASE + "m=user&";
	public static final String URL_VIEW = URL_BASE + "m=view&";
	
	private static final String URI_BASE = "content:/kidsbbs/";
	public static final String URI_INTENT_TLIST = URI_BASE + "tlist?";
	public static final String URI_INTENT_THREAD = URI_BASE + "thread?";
	public static final String URI_INTENT_USER = URI_BASE + "user?";
	public static final String URI_INTENT_VIEW = URI_BASE + "view?";

	public static final String PARAM_N_TITLE = "bt";
	public static final String PARAM_N_BOARD = "b";
	public static final String PARAM_N_TYPE = "t";
	public static final String PARAM_N_THREAD = "id";
	public static final String PARAM_N_USER = "u";
	public static final String PARAM_N_SEQ = "p";
	public static final String PARAM_N_START = "s";
	public static final String PARAM_N_COUNT = "n";

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);

		//startService(new Intent(this, KidsBbsService.class));

		startActivity(new Intent(this, KidsBbsBlist.class));
		finish();
	}
}