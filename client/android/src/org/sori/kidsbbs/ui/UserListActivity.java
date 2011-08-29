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
package org.sori.kidsbbs.ui;

import org.sori.kidsbbs.KidsBbs;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.provider.ArticleProvider;

import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;

public class UserListActivity extends ArticleListActivity {
	private String mBoardUser;
	private String mTitleView;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);

		final Intent intent = getIntent();
		mBoardUser = intent.getStringExtra(
				KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_USER);

		final Resources resources = getResources();
		mTitleView = resources.getString(R.string.title_view);

		setTitleCommon(resources.getString(R.string.title_user));
		setQueryBase(ArticleProvider.CONTENT_URISTR_LIST, FIELDS_LIST,
				ArticleProvider.KEYA_USER + "='" + mBoardUser + "'");

		updateTitle();

		registerForContextMenu(getListView());

		initializeStates();
	}

	protected void refreshList() {
		refreshListCommon();
	}

	protected void updateTitle() {
		updateTitleCommon(getCount(ArticleProvider.CONTENT_URISTR_LIST,
				ArticleProvider.SELECTION_UNREAD + " AND "
						+ ArticleProvider.KEYA_USER + "='" + mBoardUser + "'"),
				getCount(ArticleProvider.CONTENT_URISTR_LIST,
						ArticleProvider.KEYA_USER + "='" + mBoardUser + "'"));
	}

	protected boolean matchingBroadcast(int _seq, String _user, String _thread) {
		return _user.equals(mBoardUser);
	}

	protected void showItem(int _index) {
		final Cursor c = getItem(_index);
		Bundle extras = new Bundle();
		extras.putString(KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_VTITLE,
				mTitleView);
		extras.putString(KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_THREAD,
				c.getString(c.getColumnIndex(ArticleProvider.KEYA_THREAD)));
		extras.putInt(KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_SEQ,
				c.getInt(c.getColumnIndex(ArticleProvider.KEYA_SEQ)));
		extras.putString(KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_TTITLE,
				c.getString(c.getColumnIndex(ArticleProvider.KEYA_TITLE)));
		showItemCommon(this, ThreadedViewActivity.class, KidsBbs.URI_INTENT_TVIEW,
				extras);
	}

	protected void markRead(int _index) {
		markReadOne(getItem(_index));
	}

	protected void markAllRead() {
		markAllReadCommon(ArticleProvider.KEYA_USER + "='" + mBoardUser
				+ "' AND ");
	}
}