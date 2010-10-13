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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class KidsBbsThread extends KidsBbsAList {
	private int mUpdateFreq = 0;
	
	private String mBoardThread;
	
    @Override
    public void onCreate(Bundle _state) {
        super.onCreate(_state);
        
        Uri data = getIntent().getData();
        mBoardThread = data.getQueryParameter(KidsBbs.PARAM_N_THREAD);
        
        updateTitle("");
        
        registerForContextMenu(getListView());
        updateFromPreferences();
        refreshList();
    }
    
    protected void refreshList() {
    	refreshListCommon(R.string.url_thread,
    			"&" + KidsBbs.PARAM_N_THREAD + "=" + mBoardThread);
    }
    
    protected void updateTitle(String _extra) {
		setTitle("[" + getBoardTitle() + "] " +
        		getResources().getString(R.string.title_thread) + _extra);
    }
    
    protected void showItem(int _index) {
		ArticleInfo info = getItem(_index);
		showItemCommon(this, KidsBbsView.class, R.string.intent_uri_view,
				"&" + KidsBbs.PARAM_N_SEQ + "=" + Integer.toString(info.getSeq()));
    }
    
    protected void showPreference() {
		Intent intent = new Intent(this, Preferences.class);
		startActivityForResult(intent, SHOW_PREFERENCES);
    }
    
    private void updateFromPreferences() {
    	SharedPreferences prefs =
    		PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    	mUpdateFreq = Integer.parseInt(prefs.getString(Preferences.PREF_UPDATE_FREQ, "0"));
    }
    
    @Override
    public void onActivityResult(int _reqCode, int _resCode, Intent _data) {
    	super.onActivityResult(_reqCode, _resCode, _data);
    	if (_reqCode == SHOW_PREFERENCES) {
    		if (_resCode == Activity.RESULT_OK) {
    			updateFromPreferences();
    		}
    	}
    }
}