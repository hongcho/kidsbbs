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
package org.sori.kidsbbs.ui;

import java.util.HashMap;

import org.sori.kidsbbs.R;
import org.sori.kidsbbs.KidsBbs.IntentUri;
import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUriString;
import org.sori.kidsbbs.provider.ArticleProvider.OrderBy;
import org.sori.kidsbbs.provider.ArticleProvider.Selection;
import org.sori.kidsbbs.ui.prefernce.MainSettings;
import org.sori.kidsbbs.util.ArticleUtils;
import org.sori.kidsbbs.util.DBUtils;
import org.sori.kidsbbs.util.DateUtils;

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
import android.provider.BaseColumns;
import android.support.v4.view.MenuCompat;
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

public class ThreadedViewActivity extends ListActivity
		implements ListView.OnScrollListener {
	//private static final String TAG = "ThreadedViewActivity";

	private interface MenuId {
		int REFRESH = Menu.FIRST;
		int PREFERENCES = Menu.FIRST + 1;
		int EXPAND_ALL = Menu.FIRST + 2;
		int COLLAPSE_ALL = Menu.FIRST + 3;
		int TOGGLE_EXPANSION = Menu.FIRST + 4;
		int SHOW_USER = Menu.FIRST + 5;
	}

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
	private String mTitle;
	private int mSeq;

	private String mUpdateText;
	private TextView mStatusView;
	private ListView mListView;
	private TextView mUsernameView;
	private TextView mDateView;

	private int mHeaderSeq = -1;

	private UpdateTask mLastUpdate;

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.threaded_view);

		final Intent intent = getIntent();
		mTabname = intent.getStringExtra(PackageBase.PARAM + ParamName.TABNAME);
		mBoardTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.BTITLE);
		mBoardThread = intent.getStringExtra(PackageBase.PARAM + ParamName.THREAD);
		mThreadTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.TTITLE);
		mTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.VTITLE);
		mSeq = intent.getIntExtra(PackageBase.PARAM + ParamName.SEQ, -1);

		mUriList = Uri.parse(ContentUriString.LIST + mTabname);

		mResolver = getContentResolver();

		final Resources resources = getResources();
		mUpdateText = resources.getString(R.string.update_text);
		if (mTitle == null || mTitle.length() == 0) {
			mTitle = resources.getString(R.string.title_tview);
		}

		mUri = Uri.parse(ContentUriString.LIST + mTabname);
		mWhere = (mSeq < 0) ? ArticleColumn.THREAD + "='" + mBoardThread + "'"
				: ArticleColumn.SEQ + "=" + mSeq;
		
		final TextView titleView = (TextView) findViewById(R.id.title);
		titleView.setText(mThreadTitle);

		final View headerView = findViewById(R.id.header);
		headerView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View _v) {
				_v = findViewBySeq(mHeaderSeq);
				if (_v != null) {
					toggleExpansion(_v);
				}
			}
		});
		headerView.setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View _v) {
				_v = findViewBySeq(mHeaderSeq);
				if (_v != null) {
					openContextMenu(_v);
					return true;
				}
				return false;
			}
		});

		mStatusView = (TextView) findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		mUsernameView = (TextView) findViewById(R.id.username);
		mDateView = (TextView) findViewById(R.id.date);
		
		mListView = getListView();

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
		toggleExpansion(_v);
	}

	private View findViewBySeq(final int _seq) {
		if (_seq >= 0) {
			final int n = mListView.getChildCount();
			for (int i = 0; i < n; ++i) {
				final ThreadItemView vItem = (ThreadItemView) mListView.getChildAt(i);
				if (vItem.mSeq == _seq) {
					return vItem;
				}
			}
		}
		return null;
	}

	private void toggleExpansion(final View _v) {
		mAdapter.toggleExpansion(_v);
		refreshView();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.REFRESH, Menu.NONE, R.string.menu_refresh)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_refresh", null, null))
					.setShortcut('0', 'r'), 9);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.EXPAND_ALL, Menu.NONE, R.string.menu_expand_all)
					.setIcon(android.R.drawable.ic_menu_zoom)
					.setShortcut('1', 'e'), 9);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.COLLAPSE_ALL, Menu.NONE,
						R.string.menu_collapse_all)
					.setIcon(getResources().getIdentifier(
							"android:drawable/ic_menu_block", null, null))
					.setShortcut('2', 'c'), 9);
		MenuCompat.setShowAsAction(
				_menu.add(0, MenuId.PREFERENCES, Menu.NONE,
						R.string.menu_preferences)
					.setIcon(android.R.drawable.ic_menu_preferences)
					.setShortcut('3', 'p'), 9);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case MenuId.REFRESH:
			refreshList();
			return true;
		case MenuId.EXPAND_ALL:
			expandAll(true);
			return true;
		case MenuId.COLLAPSE_ALL:
			expandAll(false);
			return true;
		case MenuId.PREFERENCES:
			showPreference();
			return true;
		case R.integer.id_home: // HACK: android.R.id.home
			finish();
			return true;
		}
		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);

		_menu.setHeaderTitle(
				getResources().getString(R.string.tview_cm_header))
			.setHeaderIcon(android.R.drawable.ic_dialog_info);

		_menu.add(0, MenuId.TOGGLE_EXPANSION, Menu.NONE,
				R.string.menu_toggle_expansion);
		_menu.add(1, MenuId.SHOW_USER, Menu.NONE, R.string.menu_show_user);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		View v = ((AdapterView.AdapterContextMenuInfo) _item.getMenuInfo()).targetView;
		switch (_item.getItemId()) {
		case MenuId.TOGGLE_EXPANSION:
			toggleExpansion(v);
			return true;
		case MenuId.SHOW_USER:
			final Intent intent = new Intent(this, UserListActivity.class);
			intent.setData(IntentUri.USER);
			intent.putExtra(PackageBase.PARAM + ParamName.TABNAME, mTabname);
			intent.putExtra(PackageBase.PARAM + ParamName.BTITLE, mBoardTitle);
			intent.putExtra(PackageBase.PARAM + ParamName.USER,
					((ThreadItemView) v).mUser);
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
	
	private void updateHeader(final AbsListView _v) {
		ThreadItemView vItem = (ThreadItemView) getFirstVisibleView(_v);
		if (null == vItem) {
			return;
		}
		if (mHeaderSeq != -1 && mHeaderSeq == vItem.mSeq) {
			return;
		}
		mHeaderSeq = vItem.mSeq;
		mUsernameView.setText(vItem.mAuthor);
		mDateView.setText(vItem.mDate);
		// Force width layout adjustment (does not work all the time still).
		mDateView.getParent().getParent().requestLayout();
	}
	
	private View getFirstVisibleView(final AbsListView _v) {
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
		final int count = mAdapter.getCount();
		final String countString;
		if (mSeq < 0) {
			countString = " (" + count + ")";
		} else {
			countString = "";
		}
		setTitle("[" + mBoardTitle + "] " + mTitle + countString);
	}

	private final int getCount() {
		return mListView.getCount();
	}
	private final Cursor getItem(final int _index) {
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
			return mResolver.query(mUri, COLUMNS, mWhere, null, OrderBy.SEQ_ASC);
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

	private void expandAll(final boolean _state) {
		mAdapter.setExpansionAll(_state);
		final int n = mListView.getChildCount();
		for (int i = n - 1; i >= 0; --i) {
			mAdapter.setExpansion(mListView.getChildAt(i), _state);
		}
		refreshView();
	}

	private void markAllRead() {
		final Cursor c = getItem(getCount() - 1);
		final int seq = c.getInt(c.getColumnIndex(ArticleColumn.SEQ));
		final String where =
			ArticleColumn.THREAD + "='" + mBoardThread
			+ "' AND " + ArticleColumn.SEQ + "<=" + seq
			+ " AND " + Selection.UNREAD;
		final ContentValues values = new ContentValues();
		values.put(ArticleColumn.READ, 1);
		final int nChanged = mResolver.update(mUriList, values, where, null);
		if (nChanged > 0) {
			DBUtils.updateBoardCount(mResolver, mTabname);
		}
	}

	private void showPreference() {
		startActivity(new Intent(this, MainSettings.class));
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

	protected interface ColumnIndex {
		int _ID = 0;
		int SEQ = 1;
		int USER = 2;
		int AUTHOR = 3;
		int DATE = 4;
		int TITLE = 5;
		int THREAD = 6;
		int BODY = 7;
		int READ = 8;
	}
	protected static final String[] COLUMNS = {
		BaseColumns._ID,
		ArticleColumn.SEQ,
		ArticleColumn.USER,
		ArticleColumn.AUTHOR,
		ArticleColumn.DATE,
		ArticleColumn.TITLE,
		ArticleColumn.THREAD,
		ArticleColumn.BODY,
		ArticleColumn.READ,
	};

	protected class ArticlesAdapter extends CursorAdapter
			implements FilterQueryProvider {

		private Context mContext;
		private LayoutInflater mInflater;
		private int mCollapsedHeight0;
		private int mCollapsedHeightX;
		private int mTopPaddingX;

		private HashMap<Integer, Boolean> mExpansionStates =
			new HashMap<Integer, Boolean>();

		public Object getExpansionStates() {
			return mExpansionStates;
		}

		@SuppressWarnings("unchecked")
		public void setExpansionStates(final Object _states) {
			mExpansionStates = (HashMap<Integer, Boolean>) _states;
		}

		public ArticlesAdapter(Context _context) {
			super(_context, null, true);
			mContext = _context;
			mInflater = (LayoutInflater) mContext.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);

			final Resources resources = mContext.getResources();
			final float density = resources.getDisplayMetrics().density;
			final float scaledDensity = resources.getDisplayMetrics().scaledDensity;

			// HACK!!!
			// Top padding for tview items.
			final float topPadding = 10 * density;
			// textAppearanceSmall uses 14sp.
			final float textHeight = 14 * scaledDensity;
			final float extraHeight = 8 * density;

			mTopPaddingX = (int) (topPadding + 0.5f);

			final Theme theme = _context.getTheme();
			TypedValue value = new TypedValue();
			if (theme.resolveAttribute(
					android.R.attr.listPreferredItemHeight,
					value, true)) {
				float collapsedHeight = value.getDimension(
						resources.getDisplayMetrics());
				mCollapsedHeightX = (int) (collapsedHeight + 0.5f);
				mCollapsedHeight0 = (int) (collapsedHeight - topPadding -
						textHeight - extraHeight + 0.5f);
			} else {
				mCollapsedHeightX = ViewGroup.LayoutParams.WRAP_CONTENT;
				mCollapsedHeight0 = ViewGroup.LayoutParams.WRAP_CONTENT;
			}

			setFilterQueryProvider(this);
		}

		private class ViewHolder {
			View item;
			View subheader;
			TextView date;
			TextView username;
			TextView summary;
			TextView body;
		}

		@Override
		public void bindView(View _v, Context _context, Cursor _c) {
			final ThreadItemView itemView = (ThreadItemView) _v;
			itemView.mId = _c.getLong(ColumnIndex._ID);
			itemView.mSeq = _c.getInt(ColumnIndex.SEQ);
			itemView.mUser = _c.getString(ColumnIndex.USER);
			itemView.mAuthor = _c.getString(ColumnIndex.AUTHOR);
			itemView.mDate = _c.getString(ColumnIndex.DATE);
			itemView.mTitle = _c.getString(ColumnIndex.TITLE);
			itemView.mThread = _c.getString(ColumnIndex.THREAD);
			itemView.mBody = _c.getString(ColumnIndex.BODY);
			itemView.mRead = _c.getInt(ColumnIndex.READ) != 0;
			itemView.mFirst = _c.isFirst();
			itemView.mLast = _c.isLast();

			itemView.mDate = DateUtils.KidsToLocalDateString(itemView.mDate);
			itemView.mDate = DateUtils.GetLongDateString(itemView.mDate);

			itemView.mBody = itemView.mBody.trim();
			itemView.mSummary = ArticleUtils.generateSummary(itemView.mBody);

			final ViewHolder holder = (ViewHolder) itemView.getTag();
			holder.date.setText(itemView.mDate);
			holder.username.setText(itemView.mAuthor);
			holder.summary.setText(itemView.mSummary);
			holder.body.setText(itemView.mBody);
			if (itemView.mFirst) {
				holder.subheader.setVisibility(View.GONE);
				itemView.setPadding(0, 0, 0, 0);
			} else {
				holder.subheader.setVisibility(View.VISIBLE);
				itemView.setPadding(0, mTopPaddingX, 0, 0);
			}

			// It seems "itemView" can disappear...
			try {
				setExpansion(itemView, mExpansionStates.get(itemView.mSeq));
			} catch (NullPointerException e) {
				// Ignore...
			}
		}

		@Override
		public View newView(Context _context, Cursor _c, ViewGroup _parent) {
			final View v = mInflater.inflate(R.layout.threaded_view_item,
					_parent, false);
			final ViewHolder holder = new ViewHolder();
			holder.item = v.findViewById(R.id.item);
			holder.subheader = v.findViewById(R.id.subheader);
			holder.date = (TextView) v.findViewById(R.id.date);
			holder.username = (TextView) v.findViewById(R.id.username);
			holder.summary = (TextView) v.findViewById(R.id.summary);
			holder.body = (TextView) v.findViewById(R.id.body);
			v.setTag(holder);
			return v;
		}

		public Cursor runQuery(CharSequence _constraint) {
			return mResolver.query(mUri, COLUMNS, mWhere + " AND ("
					+ ArticleColumn.USER + " LIKE '%" + _constraint + "%' OR "
					+ ArticleColumn.BODY + " LIKE '%" + _constraint + "%')",
					null, null);
		}

		public int initExpansionStates(final Cursor _c) {
			if (_c == null || _c.getCount() <= 0) {
				return 0;
			}
			int pos = -1;
			_c.moveToFirst();
			do {
				final boolean read = _c.getInt(ColumnIndex.READ) != 0;
				if (pos == -1 && !read) {
					pos = _c.getPosition();
				}
				mExpansionStates.put(_c.getInt(ColumnIndex.SEQ), !read);
			} while (_c.moveToNext());
			if (pos == -1) {
				pos = _c.getPosition();
			}
			return pos;
		}

		public boolean isExpanded(final View _v) {
			final ViewHolder holder = (ViewHolder) _v.getTag();
			return (holder.summary.getVisibility() == View.GONE);
		}

		public void setExpansion(final View _v, final boolean _state) {
			final ThreadItemView itemView = (ThreadItemView) _v;
			final ViewHolder holder = (ViewHolder) _v.getTag();
			ViewGroup.LayoutParams params = holder.item.getLayoutParams();
			if (itemView.mLast || _state) {
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
				params.height = itemView.mFirst ?
						mCollapsedHeight0 : mCollapsedHeightX;
				holder.item.setLayoutParams(params);
			}
			mExpansionStates.put(itemView.mSeq, _state);
		}

		public void toggleExpansion(final View _v) {
			setExpansion(_v, !isExpanded(_v));
		}

		public void setExpansionAll(final boolean _state) {
			Cursor c = getCursor();
			if (c == null || c.getCount() <= 0) {
				return;
			}
			final int saved = c.getPosition();
			c.moveToFirst();
			do {
				mExpansionStates.put(c.getInt(ColumnIndex.SEQ), _state);
			} while (c.moveToNext());
			c.moveToPosition(saved);
		}
	}
}
