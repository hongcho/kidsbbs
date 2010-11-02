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

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class KidsBbsBList extends ListActivity {
	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_SHOW = Menu.FIRST + 2;
	private static final int MENU_SELECT = Menu.FIRST + 3;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArrayList<BoardInfo> mList = new ArrayList<BoardInfo>();
	private String mTitleBase;

	private TextView mStatusView;

	private UpdateTask mLastUpdate = null;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.board_list);

		mTitleBase = getResources().getString(R.string.title_blist);
		mStatusView = (TextView)findViewById(R.id.status);

		setListAdapter(new BListAdapter(this, R.layout.board_info_item,
				mList));

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
			return true;
		case MENU_REFRESH:
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

	private class UpdateTask extends AsyncTask<Void, Integer, Integer> {
		private ArrayList<BoardInfo> mTList = new ArrayList<BoardInfo>();
		private String mProgressBase;

		@Override
		protected void onPreExecute() {
			mProgressBase = getResources().getString(R.string.update_text);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Integer doInBackground(Void... _args) {
			final String[] FIELDS = {
				KidsBbsProvider.KEYB_TABNAME,
				KidsBbsProvider.KEYB_TITLE,
			};
			ContentResolver cr = getContentResolver();
			Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
					KidsBbsProvider.SELECTION_STATE_ACTIVE, null, null);
			if (c != null) {
				BoardInfo info = null;
				if (c.getCount() > 0) {
					c.moveToFirst();
					do {
						String tabname = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYB_TABNAME));
						String title = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYB_TITLE));
						info = new BoardInfo(tabname, title);
					} while (c.moveToNext());
				}
				c.close();
				
				if (info != null) {
					info.setUnreadCount(KidsBbs.getBoardUnreadCount(cr,
							info.getTabname()));
					mTList.add(info);
					//publishProgress(mTList.size());
				}
			}
			return mTList.size();
		}

		@Override
		protected void onProgressUpdate(Integer... _args) {
			mStatusView.setText(mProgressBase + " (" + _args[0] + ")");
		}

		@Override
		protected void onPostExecute(Integer _count) {
			updateView(mTList);
		}
	}

	private void updateView(ArrayList<BoardInfo> _list) {
		mList.clear();
		mList.addAll(_list);
		((BListAdapter)getListAdapter()).notifyDataSetChanged();

		mStatusView.setVisibility(View.GONE);
		setTitle(mTitleBase + " (" + mList.size() + ")");
	}

	private void refreshList() {
		if (!isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute((Void[]) null);
		}
	}

	private void showItem(int _index) {
		BoardInfo info = (BoardInfo)getListView().getItemAtPosition(_index);
		Uri data = Uri.parse(KidsBbs.URI_INTENT_TLIST +
				KidsBbs.PARAM_N_TABNAME + "=" + info.getTabname() +
				"&" + KidsBbs.PARAM_N_TITLE + "=" + info.getTitle());
		Intent i = new Intent(this, KidsBbsTList.class);
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		i.setAction(Intent.ACTION_VIEW);
		i.setData(data);
		startActivity(i);
	}

	@Override
	public void onSaveInstanceState(Bundle _state) {
		super.onSaveInstanceState(_state);
		_state.putInt(KEY_SELECTED_ITEM, getSelectedItemPosition());
	}

	@Override
	public void onRestoreInstanceState(Bundle _state) {
		super.onRestoreInstanceState(_state);
		int pos = -1;
		if (_state != null) {
			if (_state.containsKey(KEY_SELECTED_ITEM)) {
				pos = _state.getInt(KEY_SELECTED_ITEM, -1);
			}
		}
		setSelection(pos);
	}

	private class SavedStates {
		ArrayList<BoardInfo> list;
	}

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		SavedStates save = new SavedStates();
		save.list = mList;
		return save;
	}

	private void initializeStates() {
		SavedStates save = (SavedStates)getLastNonConfigurationInstance();
		if (save == null) {
			refreshList();
		} else {
			updateView(save.list);
		}
	}
	
	private boolean updateUnreadCount(String _tabname) {
		BoardInfo info;
		for (int i = 0; i < mList.size(); ++i) {
			info = mList.get(i);
			if (_tabname.equals(info.getTabname())) {
				info.setUnreadCount(KidsBbs.getBoardUnreadCount(
						getContentResolver(), _tabname));
				mList.set(i, info);
				return true;
			}
		}
		return false;
	}
	
	private class BoardUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			if (updateUnreadCount(tabname)) {
				((BListAdapter)getListAdapter()).notifyDataSetChanged();
			}
		}
	}
	
	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			if (updateUnreadCount(tabname)) {
				((BListAdapter)getListAdapter()).notifyDataSetChanged();
			}
		}
	}
	
	private class NewArticlesReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			if (updateUnreadCount(tabname)) {
				((BListAdapter)getListAdapter()).notifyDataSetChanged();
			}
		}
	}
	
	private BoardUpdatedReceiver mReceiverBoard;
	private ArticleUpdatedReceiver mReceiverArticle;
	private NewArticlesReceiver mReceiverNew;
	
	private void registerReceivers() {
		mReceiverBoard = new BoardUpdatedReceiver();
		IntentFilter filterBoard = new IntentFilter(KidsBbs.BOARD_UPDATED);
		registerReceiver(mReceiverBoard, filterBoard);
		mReceiverArticle = new ArticleUpdatedReceiver();
		IntentFilter filterArticle = new IntentFilter(KidsBbs.ARTICLE_UPDATED);
		registerReceiver(mReceiverArticle, filterArticle);
		mReceiverNew = new NewArticlesReceiver();
		IntentFilter filterNew = new IntentFilter(KidsBbs.NEW_ARTICLES);
		registerReceiver(mReceiverNew, filterNew);
	}
	
	private void unregisterReceivers() {
		unregisterReceiver(mReceiverBoard);
		unregisterReceiver(mReceiverArticle);
		unregisterReceiver(mReceiverNew);
	}
}
