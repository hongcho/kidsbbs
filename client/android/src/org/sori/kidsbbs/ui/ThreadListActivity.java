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
import org.sori.kidsbbs.provider.ArticleDatabase;
import org.sori.kidsbbs.provider.ArticleProvider;

import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;

public class ThreadListActivity extends ArticleListActivity {

	private String mTitleTView;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);

		final Resources resources = getResources();
		mTitleTView = resources.getString(R.string.title_tview);

		setTitleCommon(resources.getString(R.string.title_tlist));
		setQueryBase(ArticleProvider.ContentUriString.TLIST, COLUMNS_TLIST, null);

		updateTitle();

		registerForContextMenu(getListView());

		initializeStates();
	}

	protected void refreshList() {
		refreshListCommon();
	}

	protected void updateTitle() {
		updateTitleCommon(
				getCount(ArticleProvider.ContentUriString.LIST,
						ArticleProvider.Selection.UNREAD),
				getCount(ArticleProvider.ContentUriString.LIST, null));
	}

	protected boolean matchingBroadcast(int _seq, String _user, String _thread) {
		return true;
	}

	protected void showItem(int _index) {
		final Cursor c = getItem(_index);
		final int count = c.getInt(c.getColumnIndex(
				ArticleDatabase.ArticleColumn.CNT));
		final String title = c.getString(c.getColumnIndex(
				ArticleDatabase.ArticleColumn.TITLE));
		final String ttitle = count > 1 ? KidsBbs.getThreadTitle(title) : title;
		Bundle extras = new Bundle();
		extras.putString(KidsBbs.PARAM_BASE + KidsBbs.ParamName.VTITLE,
				mTitleTView);
		extras.putString(KidsBbs.PARAM_BASE + KidsBbs.ParamName.THREAD,
				c.getString(c.getColumnIndex(
						ArticleDatabase.ArticleColumn.THREAD)));
		extras.putString(KidsBbs.PARAM_BASE + KidsBbs.ParamName.TTITLE,
				ttitle);
		showItemCommon(this, ThreadedViewActivity.class,
				KidsBbs.IntentUri.TVIEW, extras);
	}

	protected void markRead(int _index) {
		final Cursor c = getItem(_index);
		final int count = c.getInt(c.getColumnIndex(
				ArticleDatabase.ArticleColumn.CNT));
		int nChanged;
		// Change only one for Marking it unread.
		if (count == 1) {
			nChanged = markReadOne(c);
		} else {
			final int seq = c.getInt(c.getColumnIndex(
					ArticleDatabase.ArticleColumn.SEQ));
			final String thread = c.getString(c.getColumnIndex(
					ArticleDatabase.ArticleColumn.THREAD));
			final String where =
				ArticleDatabase.ArticleColumn.THREAD + "='" + thread
				+ "' AND " + ArticleDatabase.ArticleColumn.SEQ + "<=" + seq
				+ " AND " + ArticleProvider.Selection.UNREAD;
			final ContentValues values = new ContentValues();
			values.put(ArticleDatabase.ArticleColumn.READ, 1);
			nChanged = mResolver.update(getUriList(), values, where, null);
		}
		if (nChanged > 0) {
			KidsBbs.updateBoardCount(mResolver, mTabname);
			refreshList();
		}
	}

	protected void markAllRead() {
		markAllReadCommon("");
	}
}
