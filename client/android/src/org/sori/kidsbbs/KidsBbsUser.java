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

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class KidsBbsUser extends KidsBbsAList {
	private String mBoardUser;
	private String mTitle;
	private int mUnreadCount;
	
    @Override
    public void onCreate(Bundle _state) {
        super.onCreate(_state);
        
        Uri data = getIntent().getData();
        mBoardUser = data.getQueryParameter(KidsBbs.PARAM_N_USER);
        
        Resources resources = getResources();
        mTitle = resources.getString(R.string.title_user);
        
        updateTitle();
        setQueryBase(KidsBbsProvider.CONTENT_URISTR_LIST, FIELDS_LIST,
        		KidsBbsProvider.KEYA_USER + "='" + mBoardUser + "'");
        
        registerForContextMenu(getListView());
        
        initializeStates();
    }
    
    protected void refreshList() {
    	refreshListCommon();
    }
    
    protected void updateTitle() {
    	mUnreadCount = getCount(KidsBbsProvider.CONTENT_URISTR_LIST,
    			KidsBbsProvider.SELECTION_UNREAD + " AND " +
    			KidsBbsProvider.KEYA_USER + "='" + mBoardUser + "'");
    	int count =  getCount(KidsBbsProvider.CONTENT_URISTR_LIST,
    			KidsBbsProvider.KEYA_USER + "='" + mBoardUser + "'");
    	setTitleCommon(mTitle, mUnreadCount, count);
    }
    
    protected boolean matchingBroadcast(int _seq, String _user,
    		String _thread) {
    	return _user.equals(mBoardUser);
    }
   
    protected void showItem(int _index) {
    	Cursor c = getItem(_index);
    	int seq = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
		showItemCommon(this, KidsBbsView.class, KidsBbs.URI_INTENT_VIEW,
				"&" + KidsBbs.PARAM_N_SEQ + "=" + seq);
    }
    
    protected void toggleRead(int _index) {
    	toggleReadOne(getItem(_index));
    }
    
    protected void toggleAllRead() {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(R.string.confirm_text);
    	builder.setMessage(R.string.toggle_all_read_message);
    	builder.setPositiveButton(android.R.string.ok,
    			new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface _dialog, int _which) {
    			String where =
    				KidsBbsProvider.KEYA_USER + "='" + mBoardUser +
    				"' AND " + KidsBbsProvider.KEYA_READ +
    				(mUnreadCount == 0 ? "!=0" : "=0");
    			ContentValues values = new ContentValues();
    			values.put(KidsBbsProvider.KEYA_READ,
    					mUnreadCount > 0 ? 1 : 0);
    			int nChanged = mResolver.update(getUriList(), values,
    					where, null);
    			if (nChanged > 0) {
    	    		KidsBbs.updateBoardCount(mResolver, mTabname);
    				refreshList();
    				}
    			}
    	});
    	builder.setNegativeButton(android.R.string.cancel, null);
    	builder.create().show();
    }
}