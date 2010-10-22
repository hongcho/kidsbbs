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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public abstract class KidsBbsAList extends ListActivity implements
		ListView.OnScrollListener {
	protected static final int MENU_REFRESH = Menu.FIRST;
	protected static final int MENU_SHOW = Menu.FIRST + 1;
	protected static final int MENU_PREFERENCES = Menu.FIRST + 2;
	protected static final int MENU_LAST = MENU_PREFERENCES;

	protected static final int SHOW_PREFERENCES = 1;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArrayList<ArticleInfo> mList = new ArrayList<ArticleInfo>();
	private int mItemTotal = 0;

	private String mUriBase;
	private String mWhere;

	private String mBoardTitle;
	private String mTabname;

	private TextView mStatusView;

	private ErrUtils mErrUtils;
	private UpdateTask mLastUpdate = null;

	// First call setQueryBase(), and all refreshListCommon().
	abstract protected void refreshList();

	// Update title...
	abstract protected void updateTitle(String _extra);

	// Call showItemCommon() with custom parameters.
	abstract protected void showItem(int _index);

	// Handle preference stuff.
	abstract protected void showPreference();

	protected final String getBoardTitle() {
		return mBoardTitle;
	}

	protected final ArticleInfo getItem(int _index) {
		return (ArticleInfo)getListView().getItemAtPosition(_index);
	}

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.article_list);

		mErrUtils = new ErrUtils(this, R.array.err_strings);

		Uri data = getIntent().getData();
		mTabname = data.getQueryParameter(KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);

		mStatusView = (TextView)findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		setListAdapter(new AListAdapter(this, R.layout.article_info_item, mList));
		getListView().setOnScrollListener(this);
	}

	@Override
	protected void onListItemClick(ListView _l, View _v, int _position, long _id) {
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

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);

		_menu.setHeaderTitle(getResources().getString(R.string.alist_cm_header));
		_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		switch (_item.getItemId()) {
		case MENU_SHOW:
			showItem(((AdapterView.AdapterContextMenuInfo)_item.getMenuInfo()).position);
			return true;
		}
		return false;
	}

	private boolean isUpdating() {
		return mLastUpdate != null &&
			!mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private class UpdateTask extends AsyncTask<Integer, Integer, Integer> {
		private ArrayList<ArticleInfo> mTList = new ArrayList<ArticleInfo>();
		private boolean mIsAppend = false;
		private int mTotalCount = 0;
		
		private final String[] FIELDS = {
			KidsBbsProvider.KEYA_SEQ,
			KidsBbsProvider.KEYA_USER,
			KidsBbsProvider.KEYA_DATE,
			KidsBbsProvider.KEYA_TITLE,
			KidsBbsProvider.KEYA_THREAD,
			KidsBbsProvider.KEYA_BODY,
			KidsBbsProvider.KEYA_CNT_FIELD,
		};

		@Override
		protected void onPreExecute() {
			mStatusView.setText(getResources().getString(R.string.update_text));
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Integer doInBackground(Integer... _args) {
			int start = _args[0];
			mIsAppend = start > 0;
			Uri uri = Uri.parse(mUriBase);
			ContentResolver cr = getContentResolver();
			Cursor c = cr.query(uri, FIELDS, mWhere, null, null);
			if (c != null) {
				if (c.moveToFirst()) {
					do {
						int seq = c.getInt(c.getColumnIndex(
								KidsBbsProvider.KEYA_SEQ));
						String user = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_USER));
						String date = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_DATE));
						String title = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_TITLE));
						String thread = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_THREAD));
						String body = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_BODY));
						int cnt = c.getInt(c.getColumnIndex(
								KidsBbsProvider.KEYA_CNT)); 
						if (seq > 0 && user != null && date != null &&
								title != null && thread != null && body != null) {
							mTList.add(new ArticleInfo(mTabname, seq, user,
									null, date, title, thread, body, cnt,
									false));
							publishProgress(start + mTList.size());
						}
					} while (c.moveToNext());
				}
				c.close();
			}
			return mTList.size();
		}

		@Override
		protected void onProgressUpdate(Integer... _args) {
			String text = getResources().getString(R.string.update_text);
			text += " (" + _args[0] + ")";
			mStatusView.setText(text);
		}

		@Override
		protected void onPostExecute(Integer _result) {
			if (_result >= 0) {
				mItemTotal = mTotalCount;
				updateView(mTList, mIsAppend);
			} else {
				mStatusView.setText(mErrUtils.getErrString(_result));
			}
		}
	}

	private void updateView(ArrayList<ArticleInfo> _list, boolean _append) {
		if (!_append) {
			mList.clear();
		}
		mList.addAll(_list);
		((AListAdapter)getListAdapter()).notifyDataSetChanged();

		mStatusView.setVisibility(View.GONE);
		updateTitle(" (" + mList.size() + "/" + mItemTotal + ")");
	}

	private void updateList(boolean _append) {
		if (mUriBase != null && !isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute(_append ? mList.size() : 0);
		}
	}

	protected final void setQueryBase(String _base, String _where) {
		mUriBase = _base + mTabname + "/";
		mWhere = _where;
	}

	protected final void refreshListCommon() {
		updateList(false);
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

	public void onScroll(AbsListView _v, int _first, int _nVisible, int _nTotal) {
		if (_nTotal > 0 && _first + _nVisible >= _nTotal - 1 &&
				_nTotal == mList.size() && mList.size() < mItemTotal) {
			updateList(true);
		}
	}

	public void onScrollStateChanged(AbsListView _v, int _state) {
	}

	private class SavedStates {
		ArrayList<ArticleInfo> list;
		int total;
	}

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		SavedStates save = new SavedStates();
		save.list = mList;
		save.total = mItemTotal;
		return save;
	}

	protected final void initializeStates() {
		SavedStates save = (SavedStates)getLastNonConfigurationInstance();
		if (save == null) {
			refreshList();
		} else {
			mItemTotal = save.total;
			updateView(save.list, false);
		}
	}
	
	public class ArticleReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PKG_BASE + KidsBbsProvider.KEYB_TABNAME);
			if (mTabname != null && tabname != null && mTabname == tabname) {
				refreshList();
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
