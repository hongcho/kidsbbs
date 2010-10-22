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

	private static final int SHOW_PREFERENCES = 1;

	private TextView mStatusView;
	private TextView mTitleView;
	private TextView mUserView;
	private TextView mDateView;
	private TextView mBodyView;

	private UpdateTask mLastUpdate = null;

	private ErrUtils mErrUtils;

	private String mBoardTitle;
	private String mBoardSeq;
	private String mTabname;
	
	private Uri mUri;
	private String mWhere;

	private ArticleInfo mInfo = null;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.board_view);

		mErrUtils = new ErrUtils(this, R.array.err_strings);

		Uri data = getIntent().getData();
		mTabname = data.getQueryParameter(KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
		mBoardSeq = data.getQueryParameter(KidsBbs.PARAM_N_SEQ);
		setTitle(mBoardSeq + " in [" + mBoardTitle + "]");
		
		mUri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + mTabname);
		mWhere = KidsBbsProvider.KEYA_SEQ + "=" + mBoardSeq;

		mStatusView = (TextView)findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		mTitleView = (TextView)findViewById(R.id.title);
		mUserView = (TextView)findViewById(R.id.username);
		mDateView = (TextView)findViewById(R.id.date);
		mBodyView = (TextView)findViewById(R.id.body);

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

		initializeStates();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuItem itemUpdate = _menu.add(0, MENU_REFRESH, Menu.NONE,
				R.string.menu_refresh);
		itemUpdate.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_refresh", null, null));
		itemUpdate.setShortcut('0', 'r');

		MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences);
		itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
		itemUpdate.setShortcut('1', 'p');
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
			Intent i = new Intent(this, Preferences.class);
			startActivityForResult(i, SHOW_PREFERENCES);
			return true;
		}
		return false;
	}

	private boolean isUpdating() {
		return mLastUpdate != null &&
				!mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private class UpdateTask extends AsyncTask<Void, Void, Integer> {
		private ArticleInfo mTInfo;
		
		private final String[] FIELDS = {
			KidsBbsProvider.KEYA_USER,
			KidsBbsProvider.KEYA_AUTHOR,
			KidsBbsProvider.KEYA_DATE,
			KidsBbsProvider.KEYA_TITLE,
			KidsBbsProvider.KEYA_THREAD,
			KidsBbsProvider.KEYA_BODY,
		};

		@Override
		protected void onPreExecute() {
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Integer doInBackground(Void... _args) {
			int ret = 0;
			ContentResolver cr = getContentResolver();

			// Mark it read.
			cr.update(mUri, KidsBbsProvider.V_READ_TRUE, mWhere, null);

			// Retrieve the content.
			Cursor c = cr.query(mUri, FIELDS, mWhere, null, null);
			if (c != null) {
				if (c.getCount() > 0) {
					c.moveToFirst();
					String user = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYA_USER));
					String author = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYA_AUTHOR));
					String date = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYA_DATE));
					String title = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYA_TITLE));
					String thread = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYA_THREAD));
					String body = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYA_BODY));
					if (user != null && author != null && date != null &&
							title != null && thread != null && body != null) {
						mTInfo = new ArticleInfo(mTabname,
								Integer.parseInt(mBoardSeq), user, author, date,
								title, thread, body, 1, true);
						++ret;
					}
				}
				c.close();
			}
			return ret > 0 ? 0 : ErrUtils.ERR_EMPTY;
		}

		@Override
		protected void onPostExecute(Integer _result) {
			if (_result >= 0) {
				mInfo = mTInfo;
				updateView();
			} else {
				mStatusView.setText(mErrUtils.getErrString(_result));
			}
		}
	}

	private void updateView() {
		mStatusView.setVisibility(View.GONE);
		mTitleView.setText(mInfo.getTitle());
		mUserView.setText(mInfo.getAuthor());
		mDateView.setText(mInfo.getDateString());
		mBodyView.setText(mInfo.getBody());
	}

	private void refreshView() {
		if (!isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute();
		}
	}

	private void startThreadView() {
		if (mInfo != null) {
			Uri data = Uri.parse(KidsBbs.URI_INTENT_THREAD +
					KidsBbs.PARAM_N_TABNAME + "=" + mTabname +
					"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
					"&" + KidsBbs.PARAM_N_THREAD + "=" + mInfo.getThread());
			Intent i = new Intent(this, KidsBbsThread.class);
			i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			i.setAction(Intent.ACTION_VIEW);
			i.setData(data);
			startActivity(i);
		}
	}

	private void startUserView() {
		if (mInfo != null) {
			Uri data = Uri.parse(KidsBbs.URI_INTENT_USER +
					KidsBbs.PARAM_N_TABNAME + "=" + mTabname +
					"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
					"&" + KidsBbs.PARAM_N_USER + "=" + mInfo.getUser());
			Intent i = new Intent(this, KidsBbsUser.class);
			i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			i.setAction(Intent.ACTION_VIEW);
			i.setData(data);
			startActivity(i);
		}
	}

	private void updateFromPreferences() {
	}

	@Override
	public void onActivityResult(int _reqCode, int _resCode, Intent _data) {
		super.onActivityResult(_reqCode, _resCode, _data);
		if (_reqCode == SHOW_PREFERENCES) {
			if (_resCode == Activity.RESULT_OK) {
				updateFromPreferences();
				// refreshBoardList();
			}
		}
	}

	private class SavedStates {
		ArticleInfo info;
	}

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		SavedStates save = new SavedStates();
		save.info = mInfo;
		return save;
	}

	private void initializeStates() {
		SavedStates save = (SavedStates) getLastNonConfigurationInstance();
		if (save == null) {
			updateFromPreferences();
			refreshView();
		} else {
			mInfo = save.info;
			updateView();
		}
	}
	
	public class ArticleReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PKG_BASE + KidsBbsProvider.KEYB_TABNAME);
			String seq = _intent.getStringExtra(
					KidsBbs.PKG_BASE + KidsBbsProvider.KEYA_SEQ);
			if (mTabname != null && tabname != null && mTabname == tabname &&
					mBoardSeq != null && seq != null && mBoardSeq == seq) {
				refreshView();
			}
		}
	}
	
	private ArticleReceiver mReceiver;
	
	@Override
	public void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter(KidsBbsService.NEW_ARTICLE);
		mReceiver = new ArticleReceiver();
		registerReceiver(mReceiver, filter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(mReceiver);
	}
}