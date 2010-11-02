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

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
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

public abstract class KidsBbsAList extends ListActivity {
	protected static final int MENU_REFRESH = Menu.FIRST;
	protected static final int MENU_SHOW = Menu.FIRST + 1;
	protected static final int MENU_PREFERENCES = Menu.FIRST + 2;
	protected static final int MENU_LAST = MENU_PREFERENCES;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArticlesAdapter mAdapter;
	private int mSavedItemPosition;

	private ContentResolver mResolver;
	private Uri mUri;
	private String[] mFields;
	private String mWhere;

	private String mBoardTitle;
	private String mTabname;
	
	private String mUpdateText;

	private TextView mStatusView;
	
	private ContextMenu mContextMenu;
	
	private UpdateTask mLastUpdate;

	// First call setQueryBase(), and all refreshListCommon().
	abstract protected void refreshList();

	// Update title...
	abstract protected void updateTitle();

	// Call showItemCommon() with custom parameters.
	abstract protected void showItem(int _index);
	
	// A matching broadcast?
	abstract protected boolean matchingBroadcast(int _seq, String _user,
			String _thread);

	protected final String getBoardTitle() {
		return mBoardTitle;
	}
	
	protected final int getUnreadCount(String _uriBase, String _where) {
		return KidsBbs.getTableUnreadCount(mResolver, _uriBase, mTabname,
				_where);
	}

	protected final Cursor getItem(int _index) {
		return (Cursor)getListView().getItemAtPosition(_index);
	}

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.article_list);
		
		Uri data = getIntent().getData();
		mTabname = data.getQueryParameter(KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
		
		mResolver = getContentResolver();
		
		Resources resources = getResources();
		mUpdateText = resources.getString(R.string.update_text);

		mStatusView = (TextView)findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		mAdapter = new ArticlesAdapter(this);
		setListAdapter(mAdapter);
		
		registerReceivers();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceivers();
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

		MenuItem itemUpdate = _menu.add(0, MENU_REFRESH, Menu.NONE,
				R.string.menu_refresh);
		itemUpdate.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_refresh", null, null));
		itemUpdate.setShortcut('0', 'r');

		MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences);
		itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
		itemPreferences.setShortcut('1', 'p');

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MENU_REFRESH:
			refreshList();
			return true;
		case MENU_PREFERENCES:
			showPreference();
			return true;
		}
		return false;
	}
	
	protected final void setContextMenuTitle(String _title) {
		if (mContextMenu != null) {
			mContextMenu.setHeaderTitle(_title);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);

		mContextMenu = _menu;
		setContextMenuTitle(getResources().getString(
				R.string.alist_cm_header));
		mContextMenu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
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
	
	private class UpdateTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected void onPreExecute() {
			mStatusView.setText(mUpdateText);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Cursor doInBackground(Void... _args) {
			return KidsBbsAList.this.managedQuery(mUri, mFields,
					mWhere, null, null);
		}
		
		@Override
		protected void onPostExecute(Cursor _c) {
			mStatusView.setVisibility(View.GONE);
			if (_c == null || _c.isClosed()) {
				return;
			}
			KidsBbsAList.this.mAdapter.changeCursor(_c);
			restoreListPosition();
			updateTitle();
		}
	}
	
	private boolean isUpdating() {
		return mLastUpdate != null &&
			!mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
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
			String _base, String _extra) {
		String uriString = _base +
			KidsBbs.PARAM_N_TABNAME + "=" + mTabname +
			"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
			_extra;
		Intent intent = new Intent(_from, _to);
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.setAction(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(uriString));
		startActivity(intent);
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

	protected final void initializeStates() {
		refreshList();
	}
	
	private class NewArticlesReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			if (mTabname != null && tabname != null &&
					tabname.equals(mTabname)) {
				//refreshList();
			}
		}
	}
	
	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			int seq = _intent.getIntExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYA_SEQ, -1);
			String user = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYA_USER);
			String thread = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYA_THREAD);
			if (mTabname != null && tabname != null &&
					matchingBroadcast(seq, user, thread)) {
				updateTitle();
			}
		}
	}
	
	private NewArticlesReceiver mReceiverNew;
	private ArticleUpdatedReceiver mReceiverUpdated;
	
	private void registerReceivers() {
		mReceiverNew = new NewArticlesReceiver();
		IntentFilter filterNew = new IntentFilter(KidsBbs.NEW_ARTICLES);
		registerReceiver(mReceiverNew, filterNew);
		mReceiverUpdated = new ArticleUpdatedReceiver();
		IntentFilter filterUpdated = new IntentFilter(KidsBbs.ARTICLE_UPDATED);
		registerReceiver(mReceiverUpdated, filterUpdated);
	}
	
	private void unregisterReceivers() {
		unregisterReceiver(mReceiverNew);
	}
	
	private class ArticlesAdapter extends CursorAdapter {
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

		public ArticlesAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater)mContext.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			
			Resources resources = mContext.getResources();
			mBgRead = resources.getDrawable(
					R.drawable.list_item_background_read);
			mBgUnread = resources.getDrawable(
					R.drawable.list_item_background_unread);
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
			KidsBbsAItem itemView = (KidsBbsAItem)_v;
			itemView.mId = _c.getLong(COLUMN_ID);
			itemView.mSeq = _c.getInt(COLUMN_SEQ);
			itemView.mUser = _c.getString(COLUMN_USER);
			String date = _c.getString(COLUMN_DATE);
			itemView.mTitle = _c.getString(COLUMN_TITLE);
			itemView.mThread = _c.getString(COLUMN_THREAD);
			String body = _c.getString(COLUMN_BODY);
			itemView.mRead = _c.getInt(COLUMN_READ) != 0;
			if (mFields.length - 1 >= COLUMN_COUNT) {
				itemView.mCount = _c.getInt(COLUMN_COUNT);
			} else {
				itemView.mCount = 1;
			}
			
			String user = itemView.mUser;
			if (itemView.mCount > 1) {
				int cnt = itemView.mCount - 1;
				user += " (+" + cnt + ")";
			}
			date = KidsBbs.KidsToLocalDateString(date);
			itemView.mDate = KidsBbs.GetShortDateString(date);
			itemView.mSummary = KidsBbs.generateSummary(body);
			
			ViewHolder holder = (ViewHolder)itemView.getTag();
			if (itemView.mRead) {
				holder.item.setBackgroundDrawable(mBgRead);
			} else {
				holder.item.setBackgroundDrawable(mBgUnread);
			}
			holder.title.setText(itemView.mTitle);
			holder.date.setText(itemView.mDate);
			holder.username.setText(user);
			holder.summary.setText(itemView.mSummary);
		}

		@Override
		public View newView(Context _context, Cursor _c, ViewGroup _parent) {
			View v = mInflater.inflate(R.layout.article_list_item, _parent,
					false);
			ViewHolder holder = new ViewHolder();
			holder.item = v.findViewById(R.id.item);
			holder.title = (TextView)v.findViewById(R.id.title);
			holder.date = (TextView)v.findViewById(R.id.date);
			holder.username = (TextView)v.findViewById(R.id.username);
			holder.summary = (TextView)v.findViewById(R.id.summary);
			v.setTag(holder);
			return v;
		}
	}
}
