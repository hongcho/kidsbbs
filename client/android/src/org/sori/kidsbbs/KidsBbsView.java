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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class KidsBbsView extends Activity {
	static final private int MENU_REFRESH = Menu.FIRST;
	static final private int MENU_PREFERENCES = Menu.FIRST + 1;

	private TextView mStatusView;
	private TextView mTitleView;
	private TextView mUserView;
	private TextView mDateView;
	private TextView mBodyView;

	private UpdateTask mLastUpdate;

	private String mBoardTitle;
	private String mTabname;

	private ContentResolver mResolver;

	private Uri mUri;
	private String mWhere;

	private int mSeq;
	private String mUser;
	private String mAuthor;
	private String mDate;
	private String mTitle;
	private String mThread;
	private String mBody;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.board_view);

		final Uri data = getIntent().getData();
		mTabname = data.getQueryParameter(KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
		mSeq = Integer.parseInt(data.getQueryParameter(KidsBbs.PARAM_N_SEQ));
		setTitle(mSeq + " in [" + mBoardTitle + "]");

		mResolver = getContentResolver();

		mUri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + mTabname);
		mWhere = KidsBbsProvider.KEYA_SEQ + "=" + mSeq;

		mStatusView = (TextView) findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		mTitleView = (TextView) findViewById(R.id.title);
		mUserView = (TextView) findViewById(R.id.username);
		mDateView = (TextView) findViewById(R.id.date);
		mBodyView = (TextView) findViewById(R.id.body);

		mTitleView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startThreadView();
			}
		});
		mUserView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startUserView();
			}
		});

		registerReceivers();

		initializeStates();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceivers();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuItem item;
		item = _menu.add(0, MENU_REFRESH, Menu.NONE, R.string.menu_refresh);
		item.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_refresh", null, null));
		item.setShortcut('0', 'r');

		item = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences);
		item.setIcon(android.R.drawable.ic_menu_preferences);
		item.setShortcut('1', 'p');
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MENU_REFRESH:
			refreshView();
			return true;
		case MENU_PREFERENCES:
			startActivity(new Intent(this, Preferences.class));
			return true;
		}
		return false;
	}

	private class UpdateTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected Cursor doInBackground(Void... _args) {
			final String[] FIELDS = {
				KidsBbsProvider.KEY_ID,
				KidsBbsProvider.KEYA_SEQ,
				KidsBbsProvider.KEYA_USER,
				KidsBbsProvider.KEYA_AUTHOR,
				KidsBbsProvider.KEYA_DATE,
				KidsBbsProvider.KEYA_TITLE,
				KidsBbsProvider.KEYA_THREAD,
				KidsBbsProvider.KEYA_BODY,
			};
			return mResolver.query(mUri, FIELDS, mWhere, null, null);
		}

		@Override
		protected void onPostExecute(Cursor _c) {
			if (_c == null || _c.isClosed() || _c.getCount() <= 0) {
				return;
			}

			_c.moveToFirst();
			mSeq = _c.getInt(_c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
			mUser = _c.getString(_c.getColumnIndex(KidsBbsProvider.KEYA_USER));
			mAuthor = _c.getString(
					_c.getColumnIndex(KidsBbsProvider.KEYA_AUTHOR));
			mDate = _c.getString(_c.getColumnIndex(KidsBbsProvider.KEYA_DATE));
			mDate = KidsBbs.KidsToLocalDateString(mDate);
			mTitle = _c.getString(
					_c.getColumnIndex(KidsBbsProvider.KEYA_TITLE));
			mThread = _c.getString(
					_c.getColumnIndex(KidsBbsProvider.KEYA_THREAD));
			mBody = _c.getString(_c.getColumnIndex(KidsBbsProvider.KEYA_BODY));

			if (KidsBbs.updateArticleRead(mResolver, mTabname, mSeq, true)) {
				KidsBbs.announceArticleUpdated(KidsBbsView.this, mTabname,
						mSeq, mUser, mThread);
			}
			updateView();
		}
	}

	private void updateView() {
		mTitleView.setText(mTitle);
		mUserView.setText(mAuthor);
		mDateView.setText(mDate);
		mBodyView.setText(mBody);
	}

	private boolean isUpdating() {
		return mLastUpdate != null
				&& !mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private void refreshView() {
		if (!isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute();
		}
	}

	private void startThreadView() {
		if (mThread != null) {
			final Uri data = Uri.parse(KidsBbs.URI_INTENT_THREAD
					+ KidsBbs.PARAM_N_TABNAME + "=" + mTabname + "&"
					+ KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle + "&"
					+ KidsBbs.PARAM_N_THREAD + "=" + mThread);
			final Intent intent = new Intent(this, KidsBbsThread.class);
			intent.setData(data);
			startActivity(intent);
		}
	}

	private void startUserView() {
		if (mUser != null) {
			final Uri data = Uri.parse(KidsBbs.URI_INTENT_USER
					+ KidsBbs.PARAM_N_TABNAME + "=" + mTabname + "&"
					+ KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle + "&"
					+ KidsBbs.PARAM_N_USER + "=" + mUser);
			final Intent intent = new Intent(this, KidsBbsUser.class);
			intent.setData(data);
			startActivity(intent);
		}
	}

	private class SavedStates {
		int seq;
		String user;
		String author;
		String date;
		String title;
		String thread;
		String body;
	}

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		final SavedStates save = new SavedStates();
		save.seq = mSeq;
		save.user = mUser;
		save.author = mAuthor;
		save.date = mDate;
		save.title = mTitle;
		save.thread = mThread;
		save.body = mBody;
		return save;
	}

	private void initializeStates() {
		final SavedStates save = (SavedStates) getLastNonConfigurationInstance();
		if (save == null) {
			refreshView();
		} else {
			mSeq = save.seq;
			mUser = save.user;
			mAuthor = save.author;
			mDate = save.date;
			mTitle = save.title;
			mThread = save.thread;
			mBody = save.body;
			updateView();
		}
	}

	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ KidsBbsProvider.KEYB_TABNAME);
			final int seq = _intent.getIntExtra(KidsBbs.PARAM_BASE
					+ KidsBbsProvider.KEYA_SEQ, -1);
			if (mTabname != null && tabname != null && tabname.equals(mTabname)
					&& seq == mSeq) {
				refreshView();
			}
		}
	}

	private ArticleUpdatedReceiver mReceiver;

	private void registerReceivers() {
		IntentFilter filter;
		filter = new IntentFilter(KidsBbs.ARTICLE_UPDATED);
		mReceiver = new ArticleUpdatedReceiver();
		registerReceiver(mReceiver, filter);
	}

	private void unregisterReceivers() {
		unregisterReceiver(mReceiver);
	}
}