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

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;

public class KidsBbsTList extends KidsBbsAList {

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);

		setTitleCommon(getResources().getString(R.string.title_tlist));
		setQueryBase(KidsBbsProvider.CONTENT_URISTR_TLIST, FIELDS_TLIST, null);

		updateTitle();

		registerForContextMenu(getListView());

		initializeStates();
	}

	protected void refreshList() {
		refreshListCommon();
	}

	protected void updateTitle() {
		updateTitleCommon(getCount(KidsBbsProvider.CONTENT_URISTR_LIST,
				KidsBbsProvider.SELECTION_UNREAD), getCount(
				KidsBbsProvider.CONTENT_URISTR_LIST, null));
	}

	protected boolean matchingBroadcast(int _seq, String _user, String _thread) {
		return true;
	}

	protected void showItem(int _index) {
		final Cursor c = getItem(_index);
		final int count = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_CNT));
		String base;
		String extra;
		Class<?> target;
		if (count > 1) {
			final String thread = c.getString(
					c.getColumnIndex(KidsBbsProvider.KEYA_THREAD));
			base = KidsBbs.URI_INTENT_THREAD;
			extra = "&" + KidsBbs.PARAM_N_THREAD + "=" + thread;
			target = KidsBbsThread.class;
		} else {
			final int seq = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
			base = KidsBbs.URI_INTENT_VIEW;
			extra = "&" + KidsBbs.PARAM_N_SEQ + "=" + seq;
			target = KidsBbsView.class;
		}
		showItemCommon(this, target, base, extra);
	}

	protected void toggleRead(int _index) {
		final Cursor c = getItem(_index);
		final int count = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_CNT));
		final boolean read = c.getInt(ArticlesAdapter.COLUMN_READ) != 0;
		int nChanged;
		if (count > 1) {
			final String thread = c.getString(
					c.getColumnIndex(KidsBbsProvider.KEYA_THREAD));
			final String where = KidsBbsProvider.KEYA_THREAD + "='" + thread
					+ "' AND " + KidsBbsProvider.KEYA_READ
					+ (read ? "!=0" : "=0");
			final ContentValues values = new ContentValues();
			values.put(KidsBbsProvider.KEYA_READ, read ? 0 : 1);
			nChanged = mResolver.update(getUriList(), values, where, null);
		} else {
			nChanged = toggleReadOne(c);
		}
		if (nChanged > 0) {
			KidsBbs.updateBoardCount(mResolver, mTabname);
			refreshList();
		}
	}

	protected void toggleAllRead() {
		toggleAllReadCommon("");
	}
}
