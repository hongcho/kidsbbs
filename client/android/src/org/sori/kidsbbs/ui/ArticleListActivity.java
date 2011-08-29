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
	protected static final int MENU_REFRESH = Menu.FIRST;
	protected static final int MENU_SHOW = Menu.FIRST + 1;
	protected static final int MENU_PREFERENCES = Menu.FIRST + 2;
	protected static final int MENU_MARK_READ = Menu.FIRST + 3;
	protected static final int MENU_MARK_ALL_READ = Menu.FIRST + 4;

	protected ContentResolver mResolver;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArticlesAdapter mAdapter;
	private int mSavedItemPosition;

	private Uri mUri;
	private String[] mFields;
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
				KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = intent.getStringExtra(
				KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_BTITLE);

		mUriList = Uri.parse(ArticleProvider.CONTENT_URISTR_LIST + mTabname);

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
		mHideRead = prefs.getBoolean(Preferences.PREF_HIDE_READ, false);
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
		if (_key.equals(Preferences.PREF_HIDE_READ)) {
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
				_menu.add(0, MENU_REFRESH, Menu.NONE, R.string.menu_refresh)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_refresh", null, null))
					.setShortcut('0', 'r'), 1);
		MenuCompat.setShowAsAction(
				_menu.add(0, MENU_MARK_ALL_READ, Menu.NONE,
							R.string.menu_mark_all_read)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_mark", null, null))
					.setShortcut('1', 't'), 1);
		MenuCompat.setShowAsAction(
				_menu.add(0, MENU_PREFERENCES, Menu.NONE,
							R.string.menu_preferences)
					.setIcon(android.R.drawable.ic_menu_preferences)
					.setShortcut('2', 'p'), 1);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MENU_REFRESH:
			KidsBbs.updateBoardTable(this, mTabname);
			refreshList();
			return true;
		case MENU_MARK_ALL_READ:
			markAllRead();
			return true;
		case MENU_PREFERENCES:
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

		_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
		_menu.add(1, MENU_MARK_READ, Menu.NONE, R.string.mark_read_text);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		switch (_item.getItemId()) {
		case MENU_SHOW:
			showItem(((AdapterView.AdapterContextMenuInfo) _item.getMenuInfo()).position);
			return true;
		case MENU_MARK_READ:
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
				where += mFields[ArticlesAdapter.COLUMN_READ] + "=0";
			}
			return mResolver.query(mUri, mFields, where, null, null);
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
		mFields = _fields;
		mWhere = _where;
	}

	protected final void showItemCommon(Context _from, Class<?> _to,
			Uri _uri, Bundle _extras) {
		final Intent intent = new Intent(_from, _to);
		intent.setData(_uri);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_TABNAME,
				mTabname);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_BTITLE,
				mBoardTitle);
		intent.putExtras(_extras);
		startActivity(intent);
	}

	protected int markReadOne(Cursor _c) {
		final int seq = _c.getInt(_c.getColumnIndex(ArticleProvider.KEYA_SEQ));
		final String user = _c.getString(
				_c.getColumnIndex(ArticleProvider.KEYA_USER));
		final String thread = _c.getString(
				_c.getColumnIndex(ArticleProvider.KEYA_THREAD));
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
					final int seq = c.getInt(
							c.getColumnIndex(ArticleProvider.KEYA_SEQ));
					final String where = _w + ArticleProvider.KEYA_SEQ
							+ "<=" + seq + " AND "
							+ ArticleProvider.KEYA_READ + "=0";
					final ContentValues values = new ContentValues();
					values.put(ArticleProvider.KEYA_READ, 1);
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
			final String tabname = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ ArticleProvider.KEYB_TABNAME);
			final int seq = _intent.getIntExtra(KidsBbs.PARAM_BASE
					+ ArticleProvider.KEYA_SEQ, -1);
			final String user = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ ArticleProvider.KEYA_USER);
			final String thread = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ ArticleProvider.KEYA_THREAD);
			if (mTabname != null && tabname != null && mTabname.equals(tabname)
					&& matchingBroadcast(seq, user, thread)) {
				updateTitle();
			}
		}
	}

	private class BoardUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ ArticleProvider.KEYB_TABNAME);
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

	protected static final String[] FIELDS_TLIST = {
		ArticleProvider.KEY_ID,
		ArticleProvider.KEYA_SEQ,
		ArticleProvider.KEYA_USER,
		ArticleProvider.KEYA_DATE,
		ArticleProvider.KEYA_TITLE,
		ArticleProvider.KEYA_THREAD,
		ArticleProvider.KEYA_BODY,
		ArticleProvider.KEYA_ALLREAD,
		ArticleProvider.KEYA_CNT,
	};
	protected static final String[] FIELDS_LIST = {
		ArticleProvider.KEY_ID,
		ArticleProvider.KEYA_SEQ,
		ArticleProvider.KEYA_USER,
		ArticleProvider.KEYA_DATE,
		ArticleProvider.KEYA_TITLE,
		ArticleProvider.KEYA_THREAD,
		ArticleProvider.KEYA_BODY,
		ArticleProvider.KEYA_READ,
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
		public static final int COLUMN_COUNT = 8;

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
			itemView.mId = _c.getLong(COLUMN_ID);
			itemView.mSeq = _c.getInt(COLUMN_SEQ);
			itemView.mUser = _c.getString(COLUMN_USER);
			itemView.mDate = _c.getString(COLUMN_DATE);
			itemView.mTitle = _c.getString(COLUMN_TITLE);
			itemView.mThread = _c.getString(COLUMN_THREAD);
			final String body = _c.getString(COLUMN_BODY);
			itemView.mRead = _c.getInt(COLUMN_READ) != 0;
			if (mFields.length - 1 >= COLUMN_COUNT) {
				itemView.mCount = _c.getInt(COLUMN_COUNT);
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
			if (mFields[COLUMN_READ].equals(ArticleProvider.KEYA_ALLREAD)) {
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
			where += "(" + ArticleProvider.KEYA_TITLE + " LIKE '%"
					+ _constraint + "%' OR " + ArticleProvider.KEYA_USER
					+ " LIKE '%" + _constraint + "%')";
			if (mHideRead) {
				where += " AND " + mFields[ArticlesAdapter.COLUMN_READ] + "=0";
			}
			return mResolver.query(mUri, mFields, where, null, null);
		}
	}
}
