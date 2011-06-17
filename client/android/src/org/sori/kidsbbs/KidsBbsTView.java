// Copyright (c) 2011, Younghong "Hong" Cho <hongcho@sori.org>.
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
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public class KidsBbsTView extends ListActivity {
	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_MARK_UNREAD = Menu.FIRST + 2;
	private static final int MENU_EXPAND_ALL = Menu.FIRST + 3;
	private static final int MENU_COLLAPSE_ALL = Menu.FIRST + 4;

	private ContentResolver mResolver;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArticlesAdapter mAdapter;
	private int mSavedItemPosition;

	private Uri mUri;
	private Uri mUriList;
	private String mWhere;

	private String mBoardTitle;
	private String mTabname;
	private String mBoardThread;
	private String mThreadTitle;

	private String mUpdateText;

	private TextView mStatusView;

	private UpdateTask mLastUpdate;

	private String mTitle;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.threaded_view);

		final Uri data = getIntent().getData();
		mTabname = data.getQueryParameter(KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
		mBoardThread = data.getQueryParameter(KidsBbs.PARAM_N_THREAD);
		mThreadTitle = data.getQueryParameter(KidsBbs.PARAM_N_TTITLE);

		mUriList = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + mTabname);

		mResolver = getContentResolver();

		final Resources resources = getResources();
		mTitle = resources.getString(R.string.title_tview);
		mUpdateText = resources.getString(R.string.update_text);

		mUri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + mTabname);
		mWhere = KidsBbsProvider.KEYA_THREAD + "='" + mBoardThread + "'";
		
		final TextView titleView = (TextView) findViewById(R.id.title);
		titleView.setText(mThreadTitle);

		mStatusView = (TextView) findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		mAdapter = new ArticlesAdapter(this);
		setListAdapter(mAdapter);

		registerReceivers();
	}
	
	@Override
	protected void onPause() {
		markAllRead(true);
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		unregisterReceivers();
		mAdapter.changeCursor(null);
		mAdapter = null;
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateTitle();
	}

	@Override
	protected void onListItemClick(ListView _l, View _v, int _position, long _id) {
		super.onListItemClick(_l, _v, _position, _id);
		showItem(_position);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuItem item;
		item = _menu.add(0, MENU_REFRESH, Menu.NONE, R.string.menu_refresh);
		item.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_refresh", null, null));
		item.setShortcut('0', 'r');

		item = _menu.add(0, MENU_EXPAND_ALL, Menu.NONE,
				R.string.menu_expand_all);
		item.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_forward", null, null));
		item.setShortcut('1', 'e');

		item = _menu.add(0, MENU_COLLAPSE_ALL, Menu.NONE,
				R.string.menu_collapse_all);
		item.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_back", null, null));
		item.setShortcut('2', 'c');

		item = _menu.add(0, MENU_MARK_UNREAD, Menu.NONE,
				R.string.menu_mark_unread);
		item.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_mark", null, null));
		item.setShortcut('2', 'c');

		item = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences);
		item.setIcon(android.R.drawable.ic_menu_preferences);
		item.setShortcut('3', 'p');
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MENU_REFRESH:
			refreshList();
			return true;
		case MENU_EXPAND_ALL:
			expandAll();
			return true;
		case MENU_COLLAPSE_ALL:
			collapseAll();
			return true;
		case MENU_MARK_UNREAD:
			markAllRead(false);
			return true;
		case MENU_PREFERENCES:
			showPreference();
			return true;
		}
		return false;
	}
	
	private final int getCount(String _where) {
		return KidsBbs.getTableCount(mResolver,
				KidsBbsProvider.CONTENT_URISTR_LIST, mTabname,
				KidsBbsProvider.KEYA_THREAD + "='" + mBoardThread + "'"
				+ _where);
	}

	private final void updateTitle() {
		setTitle("[" + mBoardTitle + "] " + mTitle + " ("
				+ getCount(" AND " + KidsBbsProvider.SELECTION_UNREAD) + "/"
				+ getCount("") + ")");
	}

	private final Cursor getItem(int _index) {
		return (Cursor) getListView().getItemAtPosition(_index);
	}

	private void showItem(int _index) {
		final Cursor c = getItem(_index);
		final int seq = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
		final Intent intent = new Intent(this, KidsBbsView.class);
		intent.setData(Uri.parse(KidsBbs.URI_INTENT_VIEW
				+ KidsBbs.PARAM_N_TABNAME + "=" + mTabname + "&"
				+ KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle + "&"
				+ KidsBbs.PARAM_N_SEQ + "=" + seq));
		startActivity(intent);
	}

	private class UpdateTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected void onPreExecute() {
			mStatusView.setText(mUpdateText);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Cursor doInBackground(Void... _args) {
			return mResolver.query(mUri, FIELDS, mWhere, null, null);
		}

		@Override
		protected void onPostExecute(Cursor _c) {
			mStatusView.setVisibility(View.GONE);
			if (_c == null || _c.isClosed()) {
				return;
			}
			mAdapter.changeCursor(_c);
			restoreListPosition();
			updateTitle();
		}
	}

	private boolean isUpdating() {
		return mLastUpdate != null
				&& !mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private final void refreshList() {
		if (mUri != null && !isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute();
		}
	}

	private void expandAll() {

	}

	private void collapseAll() {

	}

	private void markAllRead(final boolean _read) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.confirm_text);
		builder.setMessage(R.string.toggle_all_read_message);
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface _dialog, int _which) {
						final Cursor c = getItem(0);
						final int seq = c.getInt(
								c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
						final String where =
							KidsBbsProvider.KEYA_THREAD + "='" + mBoardThread
							+ "' AND " + KidsBbsProvider.KEYA_SEQ + "<=" + seq
							+ " AND " + KidsBbsProvider.KEYA_READ
							+ (_read ? "=0" : "!=0");
						final ContentValues values = new ContentValues();
						values.put(KidsBbsProvider.KEYA_READ, _read ? 1 : 0);
						final int nChanged = mResolver.update(mUriList,
								values, where, null);
						if (nChanged > 0) {
							KidsBbs.updateBoardCount(mResolver, mTabname);
							refreshList();
						}
					}
				});
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.create().show();
	}

	private void showPreference() {
		startActivity(new Intent(this, Preferences.class));
	}

	@Override
	public void onSaveInstanceState(Bundle _state) {
		super.onSaveInstanceState(_state);
		saveListPosition();
		_state.putInt(KEY_SELECTED_ITEM, mSavedItemPosition);
	}

	@Override
	public void onRestoreInstanceState(Bundle _state) {
		super.onRestoreInstanceState(_state);
		mSavedItemPosition = _state.getInt(KEY_SELECTED_ITEM, -1);
		restoreListPosition();
	}

	private void saveListPosition() {
		mSavedItemPosition = getSelectedItemPosition();
	}

	private void restoreListPosition() {
		setSelection(mSavedItemPosition);
	}

	protected final void initializeStates() {
		refreshList();
	}

	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ KidsBbsProvider.KEYB_TABNAME);
			final String thread = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ KidsBbsProvider.KEYA_THREAD);
			if (mTabname != null && tabname != null && mTabname.equals(tabname)
					&& mBoardThread != null && thread != null
					&& mBoardThread.equals(thread)) {
				updateTitle();
			}
		}
	}

	private class BoardUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ KidsBbsProvider.KEYB_TABNAME);
			if (mTabname != null && tabname != null && mTabname.equals(tabname)) {
				updateTitle();
			}
		}
	}

	private ArticleUpdatedReceiver mReceiverArticleUpdated;
	private BoardUpdatedReceiver mReceiverBoardUpdated;

	private void registerReceivers() {
		IntentFilter filter;
		mReceiverArticleUpdated = new ArticleUpdatedReceiver();
		filter = new IntentFilter(KidsBbs.ARTICLE_UPDATED);
		registerReceiver(mReceiverArticleUpdated, filter);
		mReceiverBoardUpdated = new BoardUpdatedReceiver();
		filter = new IntentFilter(KidsBbs.BOARD_UPDATED);
		registerReceiver(mReceiverBoardUpdated, filter);
	}

	private void unregisterReceivers() {
		unregisterReceiver(mReceiverArticleUpdated);
		unregisterReceiver(mReceiverBoardUpdated);
	}

	protected static final String[] FIELDS = {
		KidsBbsProvider.KEY_ID,
		KidsBbsProvider.KEYA_SEQ,
		KidsBbsProvider.KEYA_USER,
		KidsBbsProvider.KEYA_DATE,
		KidsBbsProvider.KEYA_TITLE,
		KidsBbsProvider.KEYA_THREAD,
		KidsBbsProvider.KEYA_BODY,
		KidsBbsProvider.KEYA_READ,
	};

	protected class ArticlesAdapter extends CursorAdapter
			implements FilterQueryProvider {
		public static final int COLUMN_ID = 0;
		public static final int COLUMN_SEQ = 1;
		public static final int COLUMN_USER = 2;
		public static final int COLUMN_DATE = 3;
		public static final int COLUMN_TITLE = 4;
		public static final int COLUMN_THREAD = 5;
		public static final int COLUMN_BODY = 6;
		public static final int COLUMN_READ = 7;

		private Context mContext;
		private LayoutInflater mInflater;
		
		private int mCollapsedHeight;

		public ArticlesAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater) mContext.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			
			final Resources resources = mContext.getResources();
			final Theme theme = mContext.getTheme();
			TypedArray array = theme.obtainStyledAttributes(
					new int[] { android.R.attr.listPreferredItemHeight });
			mCollapsedHeight = resources.getDimensionPixelSize(
					array.getResourceId(0, 0));

			setFilterQueryProvider(this);
		}

		private class ViewHolder {
			View item;
			TextView date;
			TextView username;
			TextView summary;
			TextView body;
		}

		@Override
		public void bindView(View _v, Context _context, Cursor _c) {
			final KidsBbsTItem itemView = (KidsBbsTItem) _v;
			itemView.mId = _c.getLong(COLUMN_ID);
			itemView.mSeq = _c.getInt(COLUMN_SEQ);
			itemView.mUser = _c.getString(COLUMN_USER);
			String date = _c.getString(COLUMN_DATE);
			itemView.mTitle = _c.getString(COLUMN_TITLE);
			itemView.mThread = _c.getString(COLUMN_THREAD);
			itemView.mBody = _c.getString(COLUMN_BODY);
			itemView.mRead = _c.getInt(COLUMN_READ) != 0;
			itemView.mExpanded = !itemView.mRead;

			date = KidsBbs.KidsToLocalDateString(date);
			itemView.mDate = KidsBbs.GetShortDateString(date);
			itemView.mSummary = KidsBbs.generateSummary(itemView.mBody);

			final ViewHolder holder = (ViewHolder) itemView.getTag();
			holder.date.setText(itemView.mDate);
			holder.username.setText(itemView.mUser);
			holder.summary.setText(itemView.mSummary);
			holder.body.setText(itemView.mBody);

			ViewGroup.LayoutParams params = holder.item.getLayoutParams();
			if (itemView.mExpanded) {
				holder.summary.setVisibility(View.GONE);
				holder.body.setVisibility(View.VISIBLE);
				params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
				holder.item.setLayoutParams(params);
			} else {
				holder.summary.setVisibility(View.VISIBLE);
				holder.body.setVisibility(View.GONE);
				params.height = mCollapsedHeight;
				holder.item.setLayoutParams(params);
			}
		}

		@Override
		public View newView(Context _context, Cursor _c, ViewGroup _parent) {
			final View v = mInflater.inflate(R.layout.article_list_item,
					_parent, false);
			final ViewHolder holder = new ViewHolder();
			holder.item = v.findViewById(R.id.item);
			holder.date = (TextView) v.findViewById(R.id.date);
			holder.username = (TextView) v.findViewById(R.id.username);
			holder.summary = (TextView) v.findViewById(R.id.summary);
			holder.body = (TextView) v.findViewById(R.id.body);
			v.setTag(holder);
			return v;
		}

		public Cursor runQuery(CharSequence _constraint) {
			return mResolver.query(mUri, FIELDS,
					mWhere + "(" + KidsBbsProvider.KEYA_USER
					+ " LIKE '%" + _constraint + "%')",
					null, null);
		}
	}
}
