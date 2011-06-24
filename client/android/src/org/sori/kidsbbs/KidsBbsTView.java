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

import java.util.HashMap;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public class KidsBbsTView extends ListActivity
		implements ListView.OnScrollListener {
	private static final String TAG = "KidsBbsTView";

	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_EXPAND_ALL = Menu.FIRST + 2;
	private static final int MENU_COLLAPSE_ALL = Menu.FIRST + 3;
	private static final int MENU_TOGGLE_EXPANSION = Menu.FIRST + 4;
	private static final int MENU_SHOW_USER = Menu.FIRST + 5;

	private ContentResolver mResolver;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ContextMenu mContextMenu;
	private ArticlesAdapter mAdapter;
	private int mSavedItemPosition;

	private Uri mUri;
	private Uri mUriList;
	private String mWhere;

	private String mBoardTitle;
	private String mTabname;
	private String mBoardThread;
	private String mThreadTitle;
	private String mTitle;

	private String mUpdateText;
	private TextView mStatusView;
	private ListView mListView;
	private View mHeaderView;
	private TextView mUsernameView;
	private TextView mDateView;

	private int mHeaderSeq;

	private UpdateTask mLastUpdate;

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
		
		mUsernameView = (TextView) findViewById(R.id.username);
		mDateView = (TextView) findViewById(R.id.date);
		mHeaderView = findViewById(R.id.header);
		mHeaderView.setVisibility(View.GONE);
		
		mListView = getListView();
		//mListView.setSmoothScrollbarEnabled(false);

		mAdapter = new ArticlesAdapter(this);
		setListAdapter(mAdapter);
		mListView.setOnScrollListener(this);

		registerReceivers();

		updateTitle();

		registerForContextMenu(mListView);

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
		updateTitle();
	}

	@Override
	protected void onListItemClick(ListView _l, View _v, int _position, long _id) {
		super.onListItemClick(_l, _v, _position, _id);
		toggleExpansion(_v, _position);
	}

	private void toggleExpansion(View _v, int _position) {
		mAdapter.toggleExpansion(_v);
		refreshView();
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
				"android:drawable/ic_menu_zoom", null, null));
		item.setShortcut('1', 'e');

		item = _menu.add(0, MENU_COLLAPSE_ALL, Menu.NONE,
				R.string.menu_collapse_all);
		item.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_revert", null, null));
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
			expandAll(true);
			return true;
		case MENU_COLLAPSE_ALL:
			expandAll(false);
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

		mContextMenu = _menu;
		if (mContextMenu != null) {
			mContextMenu.setHeaderTitle(
					getResources().getString(R.string.tview_cm_header));
		}
		mContextMenu.add(0, MENU_TOGGLE_EXPANSION, Menu.NONE,
				R.string.menu_toggle_expansion);
		mContextMenu.add(1, MENU_SHOW_USER, Menu.NONE,
				R.string.menu_show_user);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		int pos = ((AdapterView.AdapterContextMenuInfo) _item.getMenuInfo()).position;
		View v = ((AdapterView.AdapterContextMenuInfo) _item.getMenuInfo()).targetView;
		switch (_item.getItemId()) {
		case MENU_TOGGLE_EXPANSION:
			toggleExpansion(v, pos);
			return true;
		case MENU_SHOW_USER:
			final Uri data = Uri.parse(KidsBbs.URI_INTENT_USER
					+ KidsBbs.PARAM_N_TABNAME + "=" + mTabname + "&"
					+ KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle + "&"
					+ KidsBbs.PARAM_N_USER + "=" + ((KidsBbsTItem) v).mUser);
			final Intent intent = new Intent(this, KidsBbsUser.class);
			intent.setData(data);
			startActivity(intent);
			return true;
		}
		return false;
	}

	public void onScroll(AbsListView _v, int _first, int _count, int _total) {
		if (_total <= 0) {
			return;
		}
		refreshView();
	}

	public void onScrollStateChanged(AbsListView _v, int _state) {
		if (AbsListView.OnScrollListener.SCROLL_STATE_IDLE == _state) {
			refreshView();
		}
	}
	
	private void updateHeader(AbsListView _v) {
		KidsBbsTItem vItem = (KidsBbsTItem) getFirstVisibleView(_v);
		if (null == vItem) {
			return;
		}
		final boolean curHeader =
			mHeaderView.getVisibility() == View.VISIBLE;
		final boolean newHeader = vItem.getTop() < 0;
		Log.i(TAG, "updateHeader:0(" + mHeaderSeq
				+ "," + vItem.mSeq
				+ "," + vItem.getTop()
				+ ","+ mHeaderView.isShown()
				+ ","+ mListView.getTop()
				+ "," + newHeader + ")");
		if (mHeaderSeq != -1 && mHeaderSeq == vItem.mSeq
				&& newHeader == curHeader) {
			return;
		}
		mHeaderSeq = vItem.mSeq;
		if (newHeader) {
			mUsernameView.setText(vItem.mAuthor);
			mDateView.setText(vItem.mDate);
			if (!curHeader) {
				mHeaderView.setVisibility(View.VISIBLE);
			}
		} else {
			mHeaderView.setVisibility(View.GONE);
		}
		Log.i(TAG, "updateHeader:1(" + mHeaderSeq
				+ "," + vItem.mSeq
				+ "," + vItem.getTop()
				+ ","+ mHeaderView.isShown()
				+ ","+ mListView.getTop()
				+ "," + newHeader + ")");
	}
	
	private View getFirstVisibleView(AbsListView _v) {
		final int n = _v.getChildCount();
		for (int i = 0; i < n; ++i) {
			final View vChild = _v.getChildAt(i);
			if (vChild.getTop() >= 0 || vChild.getBottom() > 0) {
				return vChild;
			}
		}
		return null;
	}

	private final void updateTitle() {
		setTitle("[" + mBoardTitle + "] " + mTitle + " ("
				+ mAdapter.getCount() + ")");
	}

	private final int getCount() {
		return mListView.getCount();
	}
	private final Cursor getItem(int _index) {
		return (Cursor) mListView.getItemAtPosition(_index);
	}

	private class UpdateTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected void onPreExecute() {
			mStatusView.setText(mUpdateText);
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Cursor doInBackground(Void... _args) {
			return mResolver.query(mUri, FIELDS, mWhere, null,
					KidsBbsProvider.ORDER_BY_SEQ_ASC);
		}

		@Override
		protected void onPostExecute(Cursor _c) {
			mStatusView.setVisibility(View.GONE);
			if (_c == null || _c.isClosed() || mAdapter == null) {
				return;
			}
			mHeaderSeq = -1;
			mAdapter.changeCursor(_c);
			mSavedItemPosition = mAdapter.initExpansionStates(_c);
			restoreListPosition();
			updateTitle();
			markAllRead();
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
	
	private final void refreshView() {
		updateHeader(mListView);
	}

	private void expandAll(boolean _state) {
		mAdapter.setExpansionAll(_state);
		final int n = mListView.getChildCount();
		for (int i = n - 1; i >= 0; --i) {
			mAdapter.setExpansion(mListView.getChildAt(i), _state);
		}
		refreshView();
	}

	private void markAllRead() {
		final Cursor c = getItem(getCount() - 1);
		final int seq = c.getInt(
				c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
		final String where =
			KidsBbsProvider.KEYA_THREAD + "='" + mBoardThread
			+ "' AND " + KidsBbsProvider.KEYA_SEQ + "<=" + seq
			+ " AND " + KidsBbsProvider.KEYA_READ + "=0";
		final ContentValues values = new ContentValues();
		values.put(KidsBbsProvider.KEYA_READ, 1);
		final int nChanged = mResolver.update(mUriList,
				values, where, null);
		if (nChanged > 0) {
			KidsBbs.updateBoardCount(mResolver, mTabname);
		}
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

	private class SavedStates {
		Cursor cursor;
		Object expansionStates;
	};

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		final SavedStates save = new SavedStates();
		save.cursor = mAdapter.getCursor();
		save.expansionStates = mAdapter.getExpansionStates();
		return save;
	}

	private void initializeStates() {
		final SavedStates save = (SavedStates) getLastNonConfigurationInstance();
		if (save == null || save.cursor == null || save.expansionStates == null) {
			refreshList();
		} else {
			mAdapter.setExpansionStates(save.expansionStates);
			mAdapter.changeCursor(save.cursor);
		}
	}

	private void registerReceivers() {
	}

	private void unregisterReceivers() {
	}

	protected static final String[] FIELDS = {
		KidsBbsProvider.KEY_ID,
		KidsBbsProvider.KEYA_SEQ,
		KidsBbsProvider.KEYA_USER,
		KidsBbsProvider.KEYA_AUTHOR,
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
		public static final int COLUMN_AUTHOR = 3;
		public static final int COLUMN_DATE = 4;
		public static final int COLUMN_TITLE = 5;
		public static final int COLUMN_THREAD = 6;
		public static final int COLUMN_BODY = 7;
		public static final int COLUMN_READ = 8;

		private Context mContext;
		private LayoutInflater mInflater;
		private int mCollapsedHeight;

		private HashMap<Integer, Boolean> mExpansionStates =
			new HashMap<Integer, Boolean>();
		
		public Object getExpansionStates() {
			return mExpansionStates;
		}
		@SuppressWarnings("unchecked")
		public void setExpansionStates(Object _states) {
			mExpansionStates = (HashMap<Integer, Boolean>) _states;
		}

		public ArticlesAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater) mContext.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			
			final Resources resources = mContext.getResources();
			final Theme theme = _context.getTheme();

			TypedValue value = new TypedValue();
			if (theme.resolveAttribute(
					android.R.attr.listPreferredItemHeight,
					value, true)) {
				mCollapsedHeight = (int) (value.getDimension(
						resources.getDisplayMetrics()) + 0.5f);
			} else {
				mCollapsedHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
			}

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
			itemView.mAuthor = _c.getString(COLUMN_AUTHOR);
			itemView.mDate = _c.getString(COLUMN_DATE);
			itemView.mTitle = _c.getString(COLUMN_TITLE);
			itemView.mThread = _c.getString(COLUMN_THREAD);
			itemView.mBody = _c.getString(COLUMN_BODY);
			itemView.mRead = _c.getInt(COLUMN_READ) != 0;

			itemView.mDate = KidsBbs.KidsToLocalDateString(itemView.mDate);
			itemView.mDate = KidsBbs.GetLongDateString(itemView.mDate);

			itemView.mBody = KidsBbs.trimText(itemView.mBody);
			itemView.mSummary = KidsBbs.generateSummary(itemView.mBody);

			final ViewHolder holder = (ViewHolder) itemView.getTag();
			holder.date.setText(itemView.mDate);
			holder.username.setText(itemView.mAuthor);
			holder.summary.setText(itemView.mSummary);
			holder.body.setText(itemView.mBody);

			setExpansion(itemView, mExpansionStates.get(itemView.mSeq));
		}

		@Override
		public View newView(Context _context, Cursor _c, ViewGroup _parent) {
			final View v = mInflater.inflate(R.layout.threaded_view_item,
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
			return mResolver.query(mUri, FIELDS, mWhere + " AND ("
					+ KidsBbsProvider.KEYA_USER + " LIKE '%" + _constraint
					+ "%' OR " + KidsBbsProvider.KEYA_BODY + " LIKE '%"
					+ _constraint + "%')",
					null, null);
		}

		public int initExpansionStates(Cursor _c) {
			if (_c == null || _c.getCount() <= 0) {
				return 0;
			}
			int pos = -1;
			_c.moveToFirst();
			do {
				final boolean read = _c.getInt(COLUMN_READ) != 0;
				if (pos == -1 && !read) {
					pos = _c.getPosition();
				}
				mExpansionStates.put(_c.getInt(COLUMN_SEQ), !read);
			} while (_c.moveToNext());
			if (pos == -1) {
				pos = _c.getPosition();
			}
			return pos;
		}

		public boolean isExpanded(View _v) {
			final ViewHolder holder = (ViewHolder) _v.getTag();
			return (holder.summary.getVisibility() == View.GONE);
		}

		public void setExpansion(View _v, boolean _state) {
			final KidsBbsTItem itemView = (KidsBbsTItem) _v;
			final ViewHolder holder = (ViewHolder) _v.getTag();
			ViewGroup.LayoutParams params = holder.item.getLayoutParams();
			if (_state) {
				holder.summary.setVisibility(View.GONE);
				holder.body.setVisibility(View.VISIBLE);
				params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
				holder.item.setLayoutParams(params);
				// Un-focusing is necessary because the content of body may
				// have other focusable stuff (e.g., links).
				holder.body.setFocusable(false);
				holder.body.setFocusableInTouchMode(false);
			} else {
				holder.summary.setVisibility(View.VISIBLE);
				holder.body.setVisibility(View.GONE);
				params.height = mCollapsedHeight;
				holder.item.setLayoutParams(params);
			}
			mExpansionStates.put(itemView.mSeq, _state);
		}

		public void toggleExpansion(View _v) {
			setExpansion(_v, !isExpanded(_v));
		}

		public void setExpansionAll(boolean _state) {
			Cursor c = getCursor();
			if (c == null || c.getCount() <= 0) {
				return;
			}
			final int saved = c.getPosition();
			c.moveToFirst();
			do {
				mExpansionStates.put(c.getInt(COLUMN_SEQ), _state);
			} while (c.moveToNext());
			c.moveToPosition(saved);
		}
	}
}
