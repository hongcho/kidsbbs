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
import org.sori.kidsbbs.service.UpdateService;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
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

public class BoardListActivity extends ListActivity {

	private interface MenuId {
		int REFRESH = Menu.FIRST;
		int PREFERENCES = Menu.FIRST + 1;
		int SHOW = Menu.FIRST + 2;
		int SELECT = Menu.FIRST + 3;
	}

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ContentResolver mResolver;
	private NotificationManager mNotificationManager;

	private BoardsAdapter mAdapter;
	private int mSavedItemPosition;

	private String mTitleBase;
	private String mUpdateText;
	private String mUpdateErrorText;

	private TextView mStatusView;

	private UpdateTask mLastUpdate = null;
	private boolean mError = false;

	// Board selection dialog stuff
	private String[] mTabnames;
	private boolean[] mSelectedOld;
	private boolean[] mSelectedNew;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.board_list);

		mTitleBase = getResources().getString(R.string.title_blist);
		mStatusView = (TextView) findViewById(R.id.status);

		final Resources resources = getResources();
		mUpdateText = resources.getString(R.string.update_text);
		mUpdateErrorText = resources.getString(R.string.update_error_text);

		mResolver = getContentResolver();
		mNotificationManager = (NotificationManager) getSystemService(
				Context.NOTIFICATION_SERVICE);

		mAdapter = new BoardsAdapter(this);
		setListAdapter(mAdapter);

		registerForContextMenu(getListView());
		registerReceivers();

		initializeStates();
	}

	@Override
	protected void onDestroy() {
		unregisterReceivers();
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mNotificationManager.cancel(KidsBbs.NotificationType.NEW_ARTICLE);
	}
	
	@Override
	protected void onRestart() {
		super.onRestart();
		final Cursor c = mAdapter.getCursor();
		if (c != null) {
			c.requery();
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
				_menu.add(0, MenuId.SELECT, Menu.NONE, R.string.menu_select)
					.setIcon(android.R.drawable.ic_menu_add)
					.setShortcut('0', 's'), 1);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.REFRESH, Menu.NONE, R.string.menu_refresh)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_refresh", null, null))
					.setShortcut('1', 'r'), 1);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.PREFERENCES, Menu.NONE,
						R.string.menu_preferences)
					.setIcon(android.R.drawable.ic_menu_preferences)
					.setShortcut('2', 'p'), 1);

		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);

		_menu.setHeaderTitle(getResources().getString(
				R.string.blist_cm_header))
			.setHeaderIcon(android.R.drawable.ic_dialog_info);

		_menu.add(0, MenuId.SHOW, Menu.NONE, R.string.read_text);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MenuId.SELECT:
			selectBoards();
			return true;
		case MenuId.REFRESH:
			KidsBbs.updateBoardTable(this, null);
			refreshList();
			return true;
		case MenuId.PREFERENCES:
			startActivity(new Intent(this, Preferences.class));
			return true;
		}
		return false;
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		switch (_item.getItemId()) {
		case MenuId.SHOW:
			showItem(((AdapterView.AdapterContextMenuInfo)
					_item.getMenuInfo()).position);
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
			final String ORDERBY = ArticleProvider.OrderBy.COUNT_DESC + ","
					+ ArticleProvider.OrderBy.TITLE;
			return mResolver.query(ArticleProvider.ContentUri.BOARDS,
					COLUMNS, ArticleProvider.Selection.STATE_ACTIVE,
					null, ORDERBY);
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

	private void updateTitle() {
		setTitle(mTitleBase + " (" + mAdapter.getCount() + ")");
	}

	private boolean isUpdating() {
		return mLastUpdate != null
				&& !mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private void refreshList() {
		if (!mError && !isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute();
		}
	}

	private void showItem(int _index) {
		final Cursor c = (Cursor) mAdapter.getItem(_index);
		final Intent intent = new Intent(this, ThreadListActivity.class);
		intent.setData(KidsBbs.IntentUri.TLIST);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbs.ParamName.TABNAME,
				c.getString(ColumnIndex.TABNAME));
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbs.ParamName.BTITLE,
				c.getString(ColumnIndex.TITLE));
		startActivity(intent);
	}

	private void selectBoards() {
		final String[] PROJECTION = {
				ArticleDatabase.BoardColumn.TABNAME,
				ArticleDatabase.BoardColumn.TITLE,
				ArticleDatabase.BoardColumn.STATE,
		};
		final String ORDERBY = ArticleProvider.OrderBy.STATE_DESC + ","
				+ ArticleProvider.OrderBy.TITLE;
		String[] titles = null;
		final Cursor c = mResolver.query(ArticleProvider.ContentUri.BOARDS,
				PROJECTION, null, null, ORDERBY);
		if (c != null) {
			final int size = c.getCount();
			if (size > 0) {
				mTabnames = new String[size];
				titles = new String[size];
				mSelectedOld = new boolean[size];
				mSelectedNew = new boolean[size];
				int i = 0;
				c.moveToFirst();
				do {
					mTabnames[i] = c.getString(c.getColumnIndex(
							ArticleDatabase.BoardColumn.TABNAME));
					titles[i] = c.getString(c.getColumnIndex(
							ArticleDatabase.BoardColumn.TITLE));
					mSelectedOld[i] = c.getInt(c.getColumnIndex(
							ArticleDatabase.BoardColumn.STATE))
							!= ArticleDatabase.BoardState.PAUSED;
					mSelectedNew[i] = mSelectedOld[i];
					++i;
				} while (c.moveToNext());
			}
			c.close();
		}
		if (titles != null) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.board_selection_title);
			builder.setMultiChoiceItems(titles, mSelectedNew,
					new DialogInterface.OnMultiChoiceClickListener() {
						public void onClick(DialogInterface _dialog,
								int _which, boolean _isChecked) {
							mSelectedNew[_which] = _isChecked;
						}
					});
			builder.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface _dialog, int _which) {
							int nUpdated = 0;
							for (int i = 0; i < mSelectedNew.length; ++i) {
								if (mSelectedNew[i] == mSelectedOld[i]) {
									continue;
								}
								++nUpdated;
								ContentValues values = new ContentValues();
								values.put(ArticleDatabase.BoardColumn.STATE,
										mSelectedNew[i] ?
												ArticleDatabase.BoardState.SELECTED
												: ArticleDatabase.BoardState.PAUSED);
								mResolver.update(ArticleProvider.ContentUri.BOARDS,
										values, ArticleProvider.Selection.TABNAME,
										new String[] { mTabnames[i] });
							}
							if (nUpdated > 0) {
								startService(new Intent(BoardListActivity.this,
										UpdateService.class));
							}
						}
					});
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.create().show();
		}
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

	private void initializeStates() {
		final SavedStates save = (SavedStates) getLastNonConfigurationInstance();
		if (save == null || save.cursor == null) {
			refreshList();
		} else {
			mAdapter.changeCursor(save.cursor);
		}
	}

	private void setError() {
		mError = true;
		mStatusView.setVisibility(View.VISIBLE);
	}

	private void clearError() {
		mError = false;
		mStatusView.setVisibility(View.GONE);
	}

	private class UpdateErrorReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			mStatusView.setText(mUpdateErrorText);
			setError();
		}
	}

	private class BoardUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			clearError();
		}
	}

	private UpdateErrorReceiver mReceiverError;
	private BoardUpdatedReceiver mReceiverBoard;

	private void registerReceivers() {
		IntentFilter filter;
		mReceiverError = new UpdateErrorReceiver();
		filter = new IntentFilter(KidsBbs.UPDATE_ERROR);
		registerReceiver(mReceiverError, filter);
		mReceiverBoard = new BoardUpdatedReceiver();
		filter = new IntentFilter(KidsBbs.BOARD_UPDATED);
		registerReceiver(mReceiverBoard, filter);
	}

	private void unregisterReceivers() {
		unregisterReceiver(mReceiverError);
		unregisterReceiver(mReceiverBoard);
	}

	private interface ColumnIndex {
		int _ID = 0;
		int TABNAME = 1;
		int TITLE = 2;
		int COUNT = 3;
	}
	private static final String[] COLUMNS = {
		BaseColumns._ID,
		ArticleDatabase.BoardColumn.TABNAME,
		ArticleDatabase.BoardColumn.TITLE,
		ArticleDatabase.BoardColumn.COUNT,
	};

	private class BoardsAdapter extends CursorAdapter implements
			FilterQueryProvider {

		private Context mContext;
		private LayoutInflater mInflater;
		private Drawable mBgRead;
		private Drawable mBgUnread;
		private ColorStateList mTextColorPrimary;
		private ColorStateList mTextColorSecondary;

		public BoardsAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater) mContext
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

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

			setFilterQueryProvider(this);
		}

		private class ViewHolder {
			View item;
			TextView title;
			TextView count;
		}

		@Override
		public void bindView(View _v, Context _context, Cursor _c) {
			final BoardItemView itemView = (BoardItemView) _v;
			itemView.mId = _c.getLong(ColumnIndex._ID);
			itemView.mTabname = _c.getString(ColumnIndex.TABNAME);
			itemView.mTitle = _c.getString(ColumnIndex.TITLE);
			itemView.mCount = _c.getInt(ColumnIndex.COUNT);

			final ViewHolder holder = (ViewHolder) itemView.getTag();
			holder.title.setText(itemView.mTitle);
			holder.count.setText(Integer.toString(itemView.mCount));
			if (itemView.mCount > 0) {
				holder.title.setTypeface(Typeface.DEFAULT_BOLD);
				holder.count.setTypeface(Typeface.DEFAULT_BOLD);
				holder.title.setTextColor(mTextColorPrimary);
				holder.count.setTextColor(mTextColorPrimary);
				holder.item.setBackgroundDrawable(mBgUnread);
			} else {
				holder.title.setTypeface(Typeface.DEFAULT);
				holder.count.setTypeface(Typeface.DEFAULT);
				holder.title.setTextColor(mTextColorSecondary);
				holder.count.setTextColor(mTextColorSecondary);
				holder.item.setBackgroundDrawable(mBgRead);
			}
		}

		@Override
		public View newView(Context _context, Cursor _c, ViewGroup _parent) {
			final View v = mInflater.inflate(R.layout.board_list_item, _parent,
					false);
			final ViewHolder holder = new ViewHolder();
			holder.item = v.findViewById(R.id.item);
			holder.title = (TextView) v.findViewById(R.id.title);
			holder.count = (TextView) v.findViewById(R.id.count);
			v.setTag(holder);
			return v;
		}

		@Override
		protected void onContentChanged() {
			super.onContentChanged();
			updateTitle();
		}

		public Cursor runQuery(CharSequence _constraint) {
			final String WHERE = ArticleProvider.Selection.STATE_ACTIVE
					+ " AND " + ArticleDatabase.BoardColumn.TITLE
					+ " LIKE '%" + _constraint + "%'";
			final String ORDERBY = ArticleProvider.OrderBy.COUNT_DESC + ","
					+ ArticleProvider.OrderBy.TITLE;
			return mResolver.query(ArticleProvider.ContentUri.BOARDS,
					COLUMNS, WHERE, null, ORDERBY);
		}
	}
}
