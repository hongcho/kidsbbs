// Copyright (c) 2012, Younghong "Hong" Cho <hongcho@sori.org>.
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

import java.util.ArrayList;

import org.sori.kidsbbs.KidsBbs.IntentUri;
import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUriString;
import org.sori.kidsbbs.provider.ArticleProvider.OrderBy;
import org.sori.kidsbbs.provider.ArticleProvider.Selection;
import org.sori.kidsbbs.ui.preference.MainSettings;
import org.sori.kidsbbs.util.ArticleUtils;
import org.sori.kidsbbs.util.DBUtils;
import org.sori.kidsbbs.util.DateUtils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public class ThreadedViewFragment extends ListFragment
		implements LoaderManager.LoaderCallbacks<Cursor>, ListView.OnScrollListener {

	private ContentResolver mResolver;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";
	private static final String KEY_EXPANSION_STATES_KEYS = "KEY_EXPANSION_STATES_KEYS";
	private static final String KEY_EXPANSION_STATES_VALUES = "KEY_EXPANSION_STATES_VALUES";

	private ArticlesAdapter mAdapter;

	private Uri mUri;
	private Uri mUriList;
	private String mWhere;

	private String mBoardTitle;
	private String mTabname;
	private String mBoardThread;
	private String mThreadTitle;
	private String mTitle;
	private int mSeq;

	private ListView mListView;
	private TextView mUsernameView;
	private TextView mDateView;

	private int mHeaderSeq = -1;

	@Override
	public void onActivityCreated(Bundle _savedInstanceState) {
		super.onActivityCreated(_savedInstanceState);

		final Intent intent = getActivity().getIntent();
		mTabname = intent.getStringExtra(PackageBase.PARAM + ParamName.TABNAME);
		mBoardTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.BTITLE);
		mBoardThread = intent.getStringExtra(PackageBase.PARAM + ParamName.THREAD);
		mThreadTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.TTITLE);
		mTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.VTITLE);
		mSeq = intent.getIntExtra(PackageBase.PARAM + ParamName.SEQ, -1);

		mUriList = Uri.parse(ContentUriString.LIST + mTabname);

		mResolver = getActivity().getContentResolver();

		final Resources resources = getResources();
		if (mTitle == null || mTitle.length() == 0) {
			mTitle = resources.getString(R.string.title_tview);
		}

		mUri = Uri.parse(ContentUriString.LIST + mTabname);
		mWhere = (mSeq < 0) ? ArticleColumn.THREAD + "='" + mBoardThread + "'"
				: ArticleColumn.SEQ + "=" + mSeq;
		
		final TextView titleView = (TextView) getActivity().findViewById(R.id.title);
		titleView.setText(mThreadTitle);

		final View headerView = getActivity().findViewById(R.id.header);
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
					getActivity().openContextMenu(_v);
					return true;
				}
				return false;
			}
		});

		mUsernameView = (TextView) getActivity().findViewById(R.id.username);
		mDateView = (TextView) getActivity().findViewById(R.id.date);
		
		mListView = getListView();

		mAdapter = new ArticlesAdapter(getActivity());
		setListAdapter(mAdapter);
		mListView.setOnScrollListener(this);

		updateTitle();

		registerForContextMenu(mListView);
		
		if (_savedInstanceState != null) {
			restoreInstanceStates(_savedInstanceState);
		} else {
			getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
		}
		
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void onResume() {
		super.onResume();
		updateTitle();
	}

	@Override
	public void onSaveInstanceState(Bundle _outState) {
		super.onSaveInstanceState(_outState);
		saveInstanceStates(_outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu _menu, MenuInflater _inflater) {
		_inflater.inflate(R.menu.threaded_view, _menu);
		
		// Access non-public Android icons.
		_menu.findItem(R.id.menu_refresh).setIcon(
				getResources().getIdentifier(
						"android:drawable/ic_menu_refresh", null, null));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_toggle_expansion:
			toggleExpansion();
			return true;
		case R.id.menu_refresh:
			//refreshList();
			return true;
		case R.id.menu_preferences:
			showPreference();
			return true;
		case android.R.id.home:
			//finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateContextMenu(_menu, _v, _menuInfo);
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.threaded_view_context, _menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) _item.getMenuInfo();
		switch (_item.getItemId()) {
		case R.id.menu_toggle_expansion:
			toggleExpansion(info.targetView);
			return true;
		case R.id.menu_show_user:
			final Intent intent = new Intent(getActivity(), UserListActivity.class);
			intent.setData(IntentUri.USER);
			intent.putExtra(PackageBase.PARAM + ParamName.TABNAME, mTabname);
			intent.putExtra(PackageBase.PARAM + ParamName.BTITLE, mBoardTitle);
			intent.putExtra(PackageBase.PARAM + ParamName.USER,
					((ThreadItemView) info.targetView).mUser);
			startActivity(intent);
			return true;
		default:
			return super.onContextItemSelected(_item);
		}
	}

	@Override
	public void onListItemClick(ListView _l, View _v, int _position, long _id) {
		toggleExpansion(_v);
	}

	public Loader<Cursor> onCreateLoader(int _id, Bundle _args) {
		// This is called when a new Loader needs to be created. This
		// has only one Loader, so we don't care about the ID.
		return new CursorLoader(getActivity(), mUri, COLUMNS, mWhere, null, OrderBy.SEQ_ASC);
	}
	
	public void onLoadFinished(Loader<Cursor> _loader, Cursor _data) {
		// Swap the new cursor in. (The framework will take care of closing the
		// old cursor once we return.)
		mAdapter.swapCursor(_data);

		mHeaderSeq = -1;
		setSelection(mAdapter.initExpansionStates(_data));
		updateTitle();
		markAllRead();
	}

	public void onLoaderReset(Loader<Cursor> _loader) {
		// This is called when the last Cursor provided to onLoadFinished()
		// about is about to be closed. We need to make sure we are no
		// longer using it.
		mAdapter.swapCursor(null);
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
		getActivity().setTitle(countString + " " + mBoardTitle);
	}

	private final int getCount() {
		return mListView.getCount();
	}
	private final Cursor getItem(final int _index) {
		return (Cursor) mListView.getItemAtPosition(_index);
	}
	
	private final void refreshView() {
		updateHeader(mListView);
	}

	private void toggleExpansion() {
		// First get the current overall expansion state.
		boolean curState = !mAdapter.isAllCollapsed();
		
		// Toggle the expansion.
		final int n = mListView.getChildCount();
		mAdapter.setExpansionAll(!curState);
		for (int i = n - 1; i >= 0; --i) {
			mAdapter.setExpansion(mListView.getChildAt(i), !curState);
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
		startActivity(new Intent(getActivity(), MainSettings.class));
	}

	private void saveInstanceStates(Bundle _outState) {
		_outState.putInt(KEY_SELECTED_ITEM, getSelectedItemPosition());
		
		// Can't directly put SparseBooleanArray into Bundle.
		final SparseBooleanArray states = mAdapter.getExpansionStates();
		final ArrayList<Integer> stateKeys = new ArrayList<Integer>();
		final ArrayList<Integer> stateValues = new ArrayList<Integer>();
		for (int i = 0; i < states.size(); ++i) {
			stateKeys.add(i, states.keyAt(i));
			stateValues.add(i, states.valueAt(i) ? 1 : 0);
		}
		_outState.putIntegerArrayList(KEY_EXPANSION_STATES_KEYS, stateKeys);
		_outState.putIntegerArrayList(KEY_EXPANSION_STATES_VALUES, stateValues);
	}

	private void restoreInstanceStates(Bundle _savedState) {
		setSelection(_savedState.getInt(KEY_SELECTED_ITEM, 0));
		
		// Couldn't directly put SparseBooleanArray into Bundle.
		final SparseBooleanArray states = new SparseBooleanArray();
		final ArrayList<Integer> stateKeys =
				_savedState.getIntegerArrayList(KEY_EXPANSION_STATES_KEYS);
		final ArrayList<Integer> stateValues =
				_savedState.getIntegerArrayList(KEY_EXPANSION_STATES_VALUES);
		for (int i = 0; i < stateKeys.size(); ++i) {
			states.append(stateKeys.get(i), stateValues.get(i) != 0);
		}
		mAdapter.setExpansionStates(states);
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

		private SparseBooleanArray mExpansionStates = new SparseBooleanArray();

		public SparseBooleanArray getExpansionStates() {
			return mExpansionStates;
		}

		public void setExpansionStates(final SparseBooleanArray _states) {
			mExpansionStates = _states;
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
		
		public boolean isAllCollapsed() {
			Cursor c = getCursor();
			if (c == null || c.getCount() <= 0) {
				return true;
			}
			boolean isCollapsed = true;
			final int saved = c.getPosition();
			do {
				if (mExpansionStates.get(c.getInt(ColumnIndex.SEQ))) {
					isCollapsed = false;
					break;
				}
			} while (c.moveToNext());
			c.moveToPosition(saved);
			return isCollapsed;
		}
	}
}
