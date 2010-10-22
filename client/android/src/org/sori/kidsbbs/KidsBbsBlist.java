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

import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
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

public class KidsBbsBlist extends ListActivity {
	static final private int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_SHOW = Menu.FIRST + 2;

	private static final int SHOW_PREFERENCES = 1;

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
		restoreUIState();

		initializeStates();
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
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);
		_menu
				.setHeaderTitle(getResources().getString(
						R.string.blist_cm_header));
		_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MENU_REFRESH:
			refreshList();
			return true;
		case MENU_PREFERENCES:
			Intent i = new Intent(this, Preferences.class);
			startActivityForResult(i, SHOW_PREFERENCES);
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
		return mLastUpdate != null
				&& !mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private class UpdateTask extends AsyncTask<Void, Integer, Integer> {
		private final String[] FIELDS = {
			KidsBbsProvider.KEYB_TABNAME,
			KidsBbsProvider.KEYB_TITLE,
		};
		
		private ArrayList<BoardInfo> mTList = new ArrayList<BoardInfo>();
		private String mProgressBase;

		@Override
		protected void onPreExecute() {
			mProgressBase = getResources().getString(R.string.update_text);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Integer doInBackground(Void... _args) {
			ContentResolver cr = getContentResolver();
			Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
					null, null, null);
			if (c != null) {
				if (c.moveToFirst()) {
					do {
						String tabname = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYB_TABNAME));
						String title = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYB_TITLE));
						if (tabname != null && title != null) {
							mTList.add(new BoardInfo(tabname, title));
							//publishProgress(mTList.size());
						}
					} while (c.moveToNext());
				}
				c.close();
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
		Intent i = new Intent(this, KidsBbsTlist.class);
		i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		i.setAction(Intent.ACTION_VIEW);
		i.setData(data);
		startActivity(i);
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

	private void restoreUIState() {
		// SharedPreferences prefs = getPreferences(Activity.MODE_PRIVATE);
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
			updateFromPreferences();
			refreshList();
		} else {
			updateView(save.list);
		}
	}
}
