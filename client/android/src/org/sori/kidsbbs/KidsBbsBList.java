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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class KidsBbsBList extends ListActivity {
	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_SHOW = Menu.FIRST + 2;
	private static final int MENU_SELECT = Menu.FIRST + 3;
	
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
		mStatusView = (TextView)findViewById(R.id.status);
		
		Resources resources = getResources();
		mUpdateText = resources.getString(R.string.update_text);
		mUpdateErrorText = resources.getString(R.string.update_error_text);
		
		mResolver = getContentResolver();
		mNotificationManager = (NotificationManager)getSystemService(
				Context.NOTIFICATION_SERVICE);

		mAdapter = new BoardsAdapter(this);
		setListAdapter(mAdapter);

		registerForContextMenu(getListView());
		registerReceivers();

		initializeStates();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceivers();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mNotificationManager.cancel(KidsBbs.NOTIFICATION_NEW_ARTICLE);
	}

	@Override
	protected void onListItemClick(ListView _l, View _v, int _position,
			long _id) {
		super.onListItemClick(_l, _v, _position, _id);
		showItem(_position);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuItem itemSelect = _menu.add(0, MENU_SELECT, Menu.NONE,
				R.string.menu_select);
		itemSelect.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_add", null, null));
		itemSelect.setShortcut('0', 's');

		MenuItem itemUpdate = _menu.add(0, MENU_REFRESH, Menu.NONE,
				R.string.menu_refresh);
		itemUpdate.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_refresh", null, null));
		itemUpdate.setShortcut('1', 'r');

		MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences);
		itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
		itemPreferences.setShortcut('2', 'p');

		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);
		_menu.setHeaderTitle(getResources().getString(
				R.string.blist_cm_header));
		_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MENU_SELECT:
			selectBoards();
			return true;
		case MENU_REFRESH:
			startService(new Intent(this, KidsBbsService.class));
			refreshList();
			return true;
		case MENU_PREFERENCES:
			startActivity(new Intent(this, Preferences.class));
			return true;
		}
		return false;
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		switch (_item.getItemId()) {
		case MENU_SHOW:
			showItem(((AdapterView.AdapterContextMenuInfo)
					_item.getMenuInfo()).position);
			return true;
		}
		return false;
	}

	private boolean isUpdating() {
		return mLastUpdate != null &&
			!mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private class UpdateTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected void onPreExecute() {
			mStatusView.setText(mUpdateText);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Cursor doInBackground(Void... _args) {
			final String orderby =
				KidsBbsProvider.ORDER_BY_COUNT_DESC + "," +
				KidsBbsProvider.ORDER_BY_TITLE;
			return KidsBbsBList.this.managedQuery(
					KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
					KidsBbsProvider.SELECTION_STATE_ACTIVE, null, orderby);
		}

		@Override
		protected void onPostExecute(Cursor _c) {
			mStatusView.setVisibility(View.GONE);
			if (_c == null || _c.isClosed()) {
				return;
			}
			KidsBbsBList.this.mAdapter.changeCursor(_c);
			restoreListPosition();
			updateTitle();
		}
	}

	private void updateTitle() {
		setTitle(mTitleBase + " (" + mAdapter.getCount() + ")");
	}

	private void refreshList() {
		if (!mError && !isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute();
		}
	}

	private void showItem(int _index) {
		Cursor c = (Cursor)mAdapter.getItem(_index);
		String tabname = c.getString(BoardsAdapter.COLUMN_TABNAME);
		String title = c.getString(BoardsAdapter.COLUMN_TITLE);
		Uri data = Uri.parse(KidsBbs.URI_INTENT_TLIST +
				KidsBbs.PARAM_N_TABNAME + "=" + tabname +
				"&" + KidsBbs.PARAM_N_TITLE + "=" + title);
		Intent i = new Intent(this, KidsBbsTList.class);
		i.setData(data);
		startActivity(i);
	}
	
	private void selectBoards() {
		final String[] FIELDS = {
			KidsBbsProvider.KEYB_TABNAME,
			KidsBbsProvider.KEYB_TITLE,
			KidsBbsProvider.KEYB_STATE,
		};
		final String ORDERBY = KidsBbsProvider.ORDER_BY_STATE_DESC + "," +
			KidsBbsProvider.ORDER_BY_TITLE;
		String[] titles = null;
		Cursor c = mResolver.query(KidsBbsProvider.CONTENT_URI_BOARDS,
				FIELDS, null, null, ORDERBY);
		if (c != null) {
			int size = c.getCount();
			if (size > 0) {
				mTabnames = new String[size];
				titles = new String[size];
				mSelectedOld = new boolean[size];
				mSelectedNew = new boolean[size];
				int i = 0;
				c.moveToFirst();
				do {
					mTabnames[i] = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYB_TABNAME));
					titles[i] = c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYB_TITLE));
					mSelectedOld[i] = c.getInt(c.getColumnIndex(
							KidsBbsProvider.KEYB_STATE)) !=
								KidsBbsProvider.STATE_PAUSED;
					mSelectedNew[i] = mSelectedOld[i];
					++i;
				} while (c.moveToNext());
			}
			c.close();
		}
		if (titles != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.board_selection_title);
			builder.setMultiChoiceItems(titles, mSelectedNew,
					new DialogInterface.OnMultiChoiceClickListener() {
				public void onClick(DialogInterface _dialog, int _which,
						boolean _isChecked) {
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
						values.put(KidsBbsProvider.KEYB_STATE,
								mSelectedNew[i] ? KidsBbsProvider.STATE_INIT :
									KidsBbsProvider.STATE_PAUSED);
						mResolver.update(KidsBbsProvider.CONTENT_URI_BOARDS,
								values, KidsBbsProvider.SELECTION_TABNAME,
								new String[] { mTabnames[i] });
					}
					if (nUpdated > 0) {
						startService(new Intent(KidsBbsBList.this,
								KidsBbsService.class));
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

	private void initializeStates() {
		refreshList();
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
	
	private static final String[] FIELDS = {
		KidsBbsProvider.KEY_ID,
		KidsBbsProvider.KEYB_TABNAME,
		KidsBbsProvider.KEYB_TITLE,
		KidsBbsProvider.KEYB_COUNT,
	};
	private class BoardsAdapter extends CursorAdapter {
		public static final int COLUMN_ID = 0;
		public static final int COLUMN_TABNAME = 1;
		public static final int COLUMN_TITLE = 2;
		public static final int COLUMN_COUNT = 3;
		
		private Context mContext;
		private LayoutInflater mInflater;
		private Drawable mBgRead;
		private Drawable mBgUnread;
		private ColorStateList mTextColorPrimary;
		private ColorStateList mTextColorSecondary;

		public BoardsAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater)mContext.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			
			Resources resources = mContext.getResources();
			mBgRead = resources.getDrawable(
					R.drawable.list_item_background_read);
			mBgUnread = resources.getDrawable(
					R.drawable.list_item_background_unread);
			
			Theme theme = _context.getTheme();
			TypedArray array;
			array = theme.obtainStyledAttributes(
					new int[] { android.R.attr.textColorPrimary });
			mTextColorPrimary = resources.getColorStateList(
					array.getResourceId(0, 0));
			array = theme.obtainStyledAttributes(
					new int[] { android.R.attr.textColorSecondary });
			mTextColorSecondary = resources.getColorStateList(
					array.getResourceId(0, 0));
		}

		private class ViewHolder {
			View item;
			TextView title;
			TextView count;
		}

		@Override
		public void bindView(View _v, Context _context, Cursor _c) {
			KidsBbsBItem itemView = (KidsBbsBItem)_v;
			itemView.mId = _c.getLong(COLUMN_ID);
			itemView.mTabname = _c.getString(COLUMN_TABNAME);
			itemView.mTitle = _c.getString(COLUMN_TITLE);
			itemView.mCount = _c.getInt(COLUMN_COUNT);
			
			ViewHolder holder = (ViewHolder)itemView.getTag();
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
			View v = mInflater.inflate(R.layout.board_list_item, _parent,
					false);
			ViewHolder holder = new ViewHolder();
			holder.item = v.findViewById(R.id.item);
			holder.title = (TextView)v.findViewById(R.id.title);
			holder.count = (TextView)v.findViewById(R.id.count);
			v.setTag(holder);
			return v;
		}
		
		@Override
		protected void onContentChanged() {
			super.onContentChanged();
			updateTitle();
		}
	}
}
