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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.view.MenuCompat;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public abstract class ArticleListActivity extends ListActivity
		implements OnSharedPreferenceChangeListener {

	protected interface MenuId {
		int REFRESH = Menu.FIRST;
		int SHOW = Menu.FIRST + 1;
		int PREFERENCES = Menu.FIRST + 2;
		int MARK_READ = Menu.FIRST + 3;
		int MARK_ALL_READ = Menu.FIRST + 4;
	}

	protected ContentResolver mResolver;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArticlesAdapter mAdapter;
	private int mSavedItemPosition;

	private Uri mUri;
	private String[] mColumns;
	private String mWhere;
	private Uri mUriList;

	private String mBoardTitle;
	protected String mTabname;

	private String mUpdateText;

	private TextView mStatusView;

	private UpdateTask mLastUpdate;

	private boolean mHideRead;

	private String mTitle;

	// First call setQueryBase(), and all refreshListCommon().
	abstract protected void refreshList();

	// Update title...
	abstract protected void updateTitle();

	// Call showItemCommon() with custom parameters.
	abstract protected void showItem(int _index);

	// Marking articles read.
	abstract protected void markRead(int _index);

	abstract protected void markAllRead();

	// A matching broadcast?
	abstract protected boolean matchingBroadcast(int _seq, String _user,
			String _thread);

	protected final String getBoardTitle() {
		return mBoardTitle;
	}

	protected final Uri getUriList() {
		return mUriList;
	}

	protected final int getCount(String _uriBase, String _where) {
		return KidsBbs.getTableCount(mResolver, _uriBase, mTabname, _where);
	}

	protected final void setTitleCommon(String _title) {
		mTitle = _title;
	}

	protected final void updateTitleCommon(int _unread, int _total) {
		setTitle("[" + mBoardTitle + "] " + mTitle + " (" + _unread + "/"
				+ _total + ")");
	}

	protected final Cursor getItem(int _index) {
		return (Cursor) getListView().getItemAtPosition(_index);
	}

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.article_list);

		final Intent intent = getIntent();
		mTabname = intent.getStringExtra(
				KidsBbs.PARAM_BASE + KidsBbs.ParamName.TABNAME);
		mBoardTitle = intent.getStringExtra(
				KidsBbs.PARAM_BASE + KidsBbs.ParamName.BTITLE);

		mUriList = Uri.parse(ArticleProvider.ContentUriString.LIST + mTabname);

		mResolver = getContentResolver();

		mUpdateText = getResources().getString(R.string.update_text);

		mStatusView = (TextView) findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		mAdapter = new ArticlesAdapter(this);
		setListAdapter(mAdapter);

		registerReceivers();

		final SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(
					getApplicationContext());
		mHideRead = prefs.getBoolean(Preferences.PrefKey.HIDE_READ, false);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onDestroy() {
		unregisterReceivers();
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateTitle();
	}
	
	@Override
	protected void onRestart() {
		super.onRestart();
		final Cursor c = mAdapter.getCursor();
		if (c != null) {
			c.requery();
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key) {
		if (_key.equals(Preferences.PrefKey.HIDE_READ)) {
			boolean hideRead = _prefs.getBoolean(_key, false);
			if (hideRead != mHideRead) {
				mHideRead = hideRead;
				refreshList();
			}
		}
	}

	@Override
	protected void onListItemClick(ListView _l, View _v, int _position, long _id) {
		super.onListItemClick(_l, _v, _position, _id);
		showItem(_position);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.REFRESH, Menu.NONE, R.string.menu_refresh)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_refresh", null, null))
					.setShortcut('0', 'r'), 1);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.MARK_ALL_READ, Menu.NONE,
							R.string.menu_mark_all_read)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_mark", null, null))
					.setShortcut('1', 't'), 1);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.PREFERENCES, Menu.NONE,
							R.string.menu_preferences)
					.setIcon(android.R.drawable.ic_menu_preferences)
					.setShortcut('2', 'p'), 1);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MenuId.REFRESH:
			KidsBbs.updateBoardTable(this, mTabname);
			refreshList();
			return true;
		case MenuId.MARK_ALL_READ:
			markAllRead();
			return true;
		case MenuId.PREFERENCES:
			showPreference();
			return true;
		}
		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);

		_menu.setHeaderTitle(getResources().getString(
				R.string.alist_cm_header))
			.setHeaderIcon(android.R.drawable.ic_dialog_info);

		_menu.add(0, MenuId.SHOW, Menu.NONE, R.string.read_text);
		_menu.add(1, MenuId.MARK_READ, Menu.NONE, R.string.mark_read_text);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		switch (_item.getItemId()) {
		case MenuId.SHOW:
			showItem(((AdapterView.AdapterContextMenuInfo) _item.getMenuInfo()).position);
			return true;
		case MenuId.MARK_READ:
			markRead(((AdapterView.AdapterContextMenuInfo) _item
					.getMenuInfo()).position);
			return true;
		}
		return false;
	}

	private class UpdateTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected void onPreExecute() {
			mStatusView.setText(mUpdateText);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Cursor doInBackground(Void... _args) {
			String where = mWhere;
			if (mHideRead) {
				if (where == null) {
					where = "";
				} else {
					where += " AND ";
				}
				where += mColumns[ColumnIndex.READ] + "=0";
			}
			return mResolver.query(mUri, mColumns, where, null, null);
		}

		@Override
		protected void onPostExecute(Cursor _c) {
			mStatusView.setVisibility(View.GONE);
			if (_c == null || _c.isClosed() || mAdapter == null) {
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

	protected final void refreshListCommon() {
		if (mUri != null && !isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute();
		}
	}

	protected final void setQueryBase(String _uriBase, String[] _fields,
			String _where) {
		mUri = Uri.parse(_uriBase + mTabname);
		mColumns = _fields;
		mWhere = _where;
	}

	protected final void showItemCommon(Context _from, Class<?> _to,
			Uri _uri, Bundle _extras) {
		final Intent intent = new Intent(_from, _to);
		intent.setData(_uri);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbs.ParamName.TABNAME,
				mTabname);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbs.ParamName.BTITLE,
				mBoardTitle);
		intent.putExtras(_extras);
		startActivity(intent);
	}

	protected int markReadOne(Cursor _c) {
		final int seq = _c.getInt(_c.getColumnIndex(
				ArticleDatabase.ArticleColumn.SEQ));
		final String user = _c.getString(_c.getColumnIndex(
				ArticleDatabase.ArticleColumn.USER));
		final String thread = _c.getString(_c.getColumnIndex(
				ArticleDatabase.ArticleColumn.THREAD));
		if (KidsBbs.updateArticleRead(mResolver, mTabname, seq, true)) {
			KidsBbs.announceArticleUpdated(ArticleListActivity.this, mTabname, seq,
					user, thread);
			return 1;
		}
		return 0;
	}

	protected void markAllReadCommon(final String _w) {
		new AlertDialog.Builder(this)
			.setTitle(R.string.confirm_text)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setMessage(R.string.mark_all_read_message)
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface _dialog, int _which) {
					final Cursor c = getItem(0);
					final int seq = c.getInt(c.getColumnIndex(
							ArticleDatabase.ArticleColumn.SEQ));
					final String where = _w
						+ ArticleDatabase.ArticleColumn.SEQ + "<=" + seq
						+ " AND " + ArticleProvider.Selection.UNREAD;
					final ContentValues values = new ContentValues();
					values.put(ArticleDatabase.ArticleColumn.READ, 1);
					final int nChanged = mResolver.update(getUriList(),
							values, where, null);
					if (nChanged > 0) {
						KidsBbs.updateBoardCount(mResolver, mTabname);
						refreshList();
					}
				}
			})
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.show();
	}

	protected void showPreference() {
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

	private class SavedStates {
		Cursor cursor;
	};

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		final SavedStates save = new SavedStates();
		save.cursor = mAdapter.getCursor();
		return save;
	}

	protected final void initializeStates() {
		final SavedStates save = (SavedStates) getLastNonConfigurationInstance();
		if (save == null || save.cursor == null) {
			refreshList();
		} else {
			mAdapter.changeCursor(save.cursor);
		}
	}

	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + ArticleDatabase.BoardColumn.TABNAME);
			final int seq = _intent.getIntExtra(
					KidsBbs.PARAM_BASE + ArticleDatabase.ArticleColumn.SEQ, -1);
			final String user = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + ArticleDatabase.ArticleColumn.USER);
			final String thread = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + ArticleDatabase.ArticleColumn.THREAD);
			if (mTabname != null && tabname != null && mTabname.equals(tabname)
					&& matchingBroadcast(seq, user, thread)) {
				updateTitle();
			}
		}
	}

	private class BoardUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + ArticleDatabase.BoardColumn.TABNAME);
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

	private interface ColumnIndex {
		int _ID = 0;
		int SEQ = 1;
		int USER = 2;
		int DATE = 3;
		int TITLE = 4;
		int THREAD = 5;
		int BODY = 6;
		int READ = 7;
		int COUNT = 8;
	}
	protected static final String[] COLUMNS_TLIST = {
		BaseColumns._ID,
		ArticleDatabase.ArticleColumn.SEQ,
		ArticleDatabase.ArticleColumn.USER,
		ArticleDatabase.ArticleColumn.DATE,
		ArticleDatabase.ArticleColumn.TITLE,
		ArticleDatabase.ArticleColumn.THREAD,
		ArticleDatabase.ArticleColumn.BODY,
		ArticleDatabase.ArticleColumn.ALLREAD,
		ArticleDatabase.ArticleColumn.CNT,
	};
	protected static final String[] COLUMNS_LIST = {
		BaseColumns._ID,
		ArticleDatabase.ArticleColumn.SEQ,
		ArticleDatabase.ArticleColumn.USER,
		ArticleDatabase.ArticleColumn.DATE,
		ArticleDatabase.ArticleColumn.TITLE,
		ArticleDatabase.ArticleColumn.THREAD,
		ArticleDatabase.ArticleColumn.BODY,
		ArticleDatabase.ArticleColumn.READ,
	};

	protected class ArticlesAdapter extends CursorAdapter
			implements FilterQueryProvider {

		private Context mContext;
		private LayoutInflater mInflater;
		private Drawable mBgRead;
		private Drawable mBgUnread;
		private ColorStateList mTextColorPrimary;
		private ColorStateList mTextColorSecondary;

		public ArticlesAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater) mContext.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);

			final Resources resources = mContext.getResources();
			mBgRead = resources.getDrawable(
					R.drawable.list_item_background_read);
			mBgUnread = resources.getDrawable(
					R.drawable.list_item_background_unread);

			final Theme theme = _context.getTheme();
			TypedArray array;
			array = theme.obtainStyledAttributes(
					new int[] { android.R.attr.textColorPrimary });
			mTextColorPrimary = resources.getColorStateList(
					array.getResourceId(0, 0));
			array = theme.obtainStyledAttributes(
					new int[] { android.R.attr.textColorSecondary });
			mTextColorSecondary = resources.getColorStateList(
					array.getResourceId(0, 0));
			array.recycle();

			setFilterQueryProvider(this);
		}

		private class ViewHolder {
			View item;
			TextView title;
			TextView date;
			TextView username;
			TextView summary;
		}

		@Override
		public void bindView(View _v, Context _context, Cursor _c) {
			final ArticleItemView itemView = (ArticleItemView) _v;
			itemView.mId = _c.getLong(ColumnIndex._ID);
			itemView.mSeq = _c.getInt(ColumnIndex.SEQ);
			itemView.mUser = _c.getString(ColumnIndex.USER);
			itemView.mDate = _c.getString(ColumnIndex.DATE);
			itemView.mTitle = _c.getString(ColumnIndex.TITLE);
			itemView.mThread = _c.getString(ColumnIndex.THREAD);
			final String body = _c.getString(ColumnIndex.BODY);
			itemView.mRead = _c.getInt(ColumnIndex.READ) != 0;
			if (mColumns.length - 1 >= ColumnIndex.COUNT) {
				itemView.mCount = _c.getInt(ColumnIndex.COUNT);
			} else {
				itemView.mCount = 1;
			}

			String user = itemView.mUser;
			if (itemView.mCount > 1) {
				final int cnt = itemView.mCount - 1;
				user += " (+" + cnt + ")";
			}

			itemView.mDate = KidsBbs.KidsToLocalDateString(itemView.mDate);
			itemView.mDate = KidsBbs.GetShortDateString(itemView.mDate);

			itemView.mSummary = KidsBbs.generateSummary(body);

			// Remove "RE:" for threaded list.
			if (mColumns[ColumnIndex.READ].equals(
					ArticleDatabase.ArticleColumn.ALLREAD)) {
				itemView.mTitle = KidsBbs.getThreadTitle(itemView.mTitle);
			}

			final ViewHolder holder = (ViewHolder) itemView.getTag();
			holder.title.setText(itemView.mTitle);
			holder.date.setText(itemView.mDate);
			holder.username.setText(user);
			holder.summary.setText(itemView.mSummary);
			if (itemView.mRead) {
				holder.title.setTypeface(Typeface.DEFAULT);
				holder.username.setTypeface(Typeface.DEFAULT);
				holder.title.setTextColor(mTextColorSecondary);
				holder.date.setTextColor(mTextColorSecondary);
				holder.username.setTextColor(mTextColorSecondary);
				holder.summary.setTextColor(mTextColorSecondary);
				holder.item.setBackgroundDrawable(mBgRead);
			} else {
				holder.title.setTypeface(Typeface.DEFAULT_BOLD);
				holder.username.setTypeface(Typeface.DEFAULT_BOLD);
				holder.title.setTextColor(mTextColorPrimary);
				holder.date.setTextColor(mTextColorPrimary);
				holder.username.setTextColor(mTextColorPrimary);
				holder.summary.setTextColor(mTextColorPrimary);
				holder.item.setBackgroundDrawable(mBgUnread);
			}
		}

		@Override
		public View newView(Context _context, Cursor _c, ViewGroup _parent) {
			final View v = mInflater.inflate(R.layout.article_list_item,
					_parent, false);
			final ViewHolder holder = new ViewHolder();
			holder.item = v.findViewById(R.id.item);
			holder.title = (TextView) v.findViewById(R.id.title);
			holder.date = (TextView) v.findViewById(R.id.date);
			holder.username = (TextView) v.findViewById(R.id.username);
			holder.summary = (TextView) v.findViewById(R.id.summary);
			v.setTag(holder);
			return v;
		}

		public Cursor runQuery(CharSequence _constraint) {
			String where = mWhere;
			if (where == null) {
				where = "";
			} else {
				where += " AND ";
			}
			where += "(" + ArticleDatabase.ArticleColumn.TITLE
				+ " LIKE '%" + _constraint + "%' OR "
				+ ArticleDatabase.ArticleColumn.USER
				+ " LIKE '%" + _constraint + "%')";
			if (mHideRead) {
				where += " AND " + mColumns[ColumnIndex.READ] + "=0";
			}
			return mResolver.query(mUri, mColumns, where, null, null);
		}
	}
}
