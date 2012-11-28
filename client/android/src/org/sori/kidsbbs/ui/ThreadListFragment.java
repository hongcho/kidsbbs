// Copyright (c) 2012, Younghong "Hong" Cho <hongcho@sori.org>.
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

import org.sori.kidsbbs.KidsBbs.IntentUri;
import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUriString;
import org.sori.kidsbbs.provider.ArticleProvider.Selection;
import org.sori.kidsbbs.util.ArticleUtils;
import org.sori.kidsbbs.util.DBUtils;

import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;

public class ThreadListFragment extends ArticleListFragment {

	private String mTitleTView;

	@Override
	public void onActivityCreated(Bundle _savedInstanceState) {
		super.onActivityCreated(_savedInstanceState);

		final Resources resources = getResources();
		mTitleTView = resources.getString(R.string.title_tview);

		setQueryBase(ContentUriString.TLIST, COLUMNS_TLIST, null);

		updateTitle();

		registerForContextMenu(getListView());
	}

	protected void updateTitle() {
		updateTitleCommon(getCount(ContentUriString.LIST, Selection.UNREAD),
				getCount(ContentUriString.LIST, null));
	}

	protected boolean matchingBroadcast(final int _seq, final String _user,
			final String _thread) {
		return true;
	}

	protected void showItem(final int _index) {
		final Cursor c = getItem(_index);
		final int count = c.getInt(c.getColumnIndex(ArticleColumn.CNT));
		final String title = c.getString(c.getColumnIndex(ArticleColumn.TITLE));
		final String ttitle = count > 1 ?
				ArticleUtils.getThreadTitle(title) : title;
		Bundle extras = new Bundle();
		extras.putString(PackageBase.PARAM + ParamName.VTITLE, mTitleTView);
		extras.putString(PackageBase.PARAM + ParamName.THREAD,
				c.getString(c.getColumnIndex(ArticleColumn.THREAD)));
		extras.putString(PackageBase.PARAM + ParamName.TTITLE, ttitle);
		showItemCommon(getActivity(), ThreadedViewActivity.class, IntentUri.TVIEW, extras);
	}

	protected void markRead(final int _index) {
		final Cursor c = getItem(_index);
		final int count = c.getInt(c.getColumnIndex(ArticleColumn.CNT));
		int nChanged;
		// Change only one for Marking it unread.
		if (count == 1) {
			nChanged = markReadOne(c);
		} else {
			final int seq = c.getInt(c.getColumnIndex(ArticleColumn.SEQ));
			final String thread = c.getString(c.getColumnIndex(
					ArticleColumn.THREAD));
			final String where =
				ArticleColumn.THREAD + "='" + thread
				+ "' AND " + ArticleColumn.SEQ + "<=" + seq
				+ " AND " + Selection.UNREAD;
			final ContentValues values = new ContentValues();
			values.put(ArticleColumn.READ, 1);
			nChanged = mResolver.update(getUriList(), values, where, null);
		}
		if (nChanged > 0) {
			DBUtils.updateBoardCount(mResolver, mTabname);
			getLoaderManager().restartLoader(0, null, this);
		}
	}

	protected void markAllRead() {
		markAllReadCommon("");
	}
}
