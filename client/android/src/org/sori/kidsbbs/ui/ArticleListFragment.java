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

import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardColumn;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUriString;
import org.sori.kidsbbs.provider.ArticleProvider.Selection;
import org.sori.kidsbbs.ui.preference.MainSettings;
import org.sori.kidsbbs.ui.preference.MainSettings.PrefKey;
import org.sori.kidsbbs.util.ArticleUtils;
import org.sori.kidsbbs.util.BroadcastUtils;
import org.sori.kidsbbs.util.BroadcastUtils.BroadcastType;
import org.sori.kidsbbs.util.DBUtils;
import org.sori.kidsbbs.util.DateUtils;

import android.app.AlertDialog;
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
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public abstract class ArticleListFragment extends ListFragment
		implements LoaderManager.LoaderCallbacks<Cursor>,
				OnSharedPreferenceChangeListener {

	protected ContentResolver mResolver;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArticlesAdapter mAdapter;

	private Uri mUri;
	private String[] mColumns;
	private String mWhere;
	private Uri mUriList;

	private String mBoardTitle;
	protected String mTabname;

	private boolean mHideRead;

	// Update title...
	abstract protected void updateTitle();

	// Call showItemCommon() with custom parameters.
	abstract protected void showItem(int _index);

	// Marking articles read.
	abstract protected void markRead(int _index);

	abstract protected void markAllRead();

	// A matching broadcast?
	abstract protected boolean matchingBroadcast(final int _seq,
			final String _user, final String _thread);

	protected final String getBoardTitle() {
		return mBoardTitle;
	}

	protected final Uri getUriList() {
		return mUriList;
	}

	protected final int getCount(final String _uriBase, final String _where) {
		return DBUtils.getTableCount(mResolver, _uriBase, mTabname, _where);
	}

	protected final void updateTitleCommon(final int _unread, final int _total) {
		getActivity().setTitle("(" + _unread + "/" + _total + ") " + mBoardTitle);
	}

	protected final Cursor getItem(final int _index) {
		return (Cursor) getListView().getItemAtPosition(_index);
	}

	@Override
	public void onActivityCreated(Bundle _savedInstanceState) {
		super.onActivityCreated(_savedInstanceState);

		final Intent intent = getActivity().getIntent();
		mTabname = intent.getStringExtra(PackageBase.PARAM + ParamName.TABNAME);
		mBoardTitle = intent.getStringExtra(PackageBase.PARAM + ParamName.BTITLE);

		mUriList = Uri.parse(ContentUriString.LIST + mTabname);

		mResolver = getActivity().getContentResolver();

		mAdapter = new ArticlesAdapter(getActivity());
		setListAdapter(mAdapter);

		registerReceivers();

		final SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(
					getActivity().getApplicationContext());
		mHideRead = prefs.getBoolean(PrefKey.HIDE_READ, false);
		prefs.registerOnSharedPreferenceChangeListener(this);
		
		getLoaderManager().initLoader(0, null, this);
		
		if (_savedInstanceState != null) {
			restoreInstanceStates(_savedInstanceState);
		} else {
			getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
		}
	}

	@Override
	public void onDestroy() {
		unregisterReceivers();
		super.onDestroy();
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
		_inflater.inflate(R.menu.article_list, _menu);
		
		// Access non-public Android icons.
		_menu.findItem(R.id.menu_refresh).setIcon(
				getResources().getIdentifier(
						"android:drawable/ic_menu_refresh", null, null));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_mark_all_read:
			markAllRead();
			return true;
		case R.id.menu_refresh:
			DBUtils.updateBoardTable(getActivity(), mTabname);
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
		inflater.inflate(R.menu.article_list_context, _menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) _item.getMenuInfo();
		switch (_item.getItemId()) {
		case R.id.menu_show:
			showItemWrapper(info.position);
			return true;
		case R.id.menu_mark_read:
			markRead(info.position);
			return true;
		default:
			return super.onContextItemSelected(_item);
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key) {
		if (_key.equals(PrefKey.HIDE_READ)) {
			boolean hideRead = _prefs.getBoolean(_key, false);
			if (hideRead != mHideRead) {
				mHideRead = hideRead;
				getLoaderManager().restartLoader(0, null, this);
			}
		}
	}

	@Override
	public void onListItemClick(ListView _l, View _v, int _position, long _id) {
		showItemWrapper(_position);
	}

	public Loader<Cursor> onCreateLoader(int _id, Bundle _args) {
		// This is called when a new Loader needs to be created. This
		// has only one Loader, so we don't care about the ID.
		String where = mWhere;
		if (mHideRead) {
			if (where == null) {
				where = "";
			} else {
				where += " AND ";
			}
			where += mColumns[ColumnIndex.READ] + "=0";
		}
		return new CursorLoader(getActivity(), mUri, mColumns, where, null, null);
	}
	
	public void onLoadFinished(Loader<Cursor> _loader, Cursor _data) {
		// Swap the new cursor in. (The framework will take care of closing the
		// old cursor once we return.)
		mAdapter.swapCursor(_data);
		updateTitle();
	}

	public void onLoaderReset(Loader<Cursor> _loader) {
		// This is called when the last Cursor provided to onLoadFinished()
		// about is about to be closed. We need to make sure we are no
		// longer using it.
		mAdapter.swapCursor(null);
	}

	private void saveInstanceStates(Bundle _outState) {
		_outState.putInt(KEY_SELECTED_ITEM, getSelectedItemPosition());
	}
	private void restoreInstanceStates(Bundle _savedState) {
		setSelection(_savedState.getInt(KEY_SELECTED_ITEM, -1));
	}

	protected final void setQueryBase(final String _uriBase,
			final String[] _fields, final String _where) {
		mUri = Uri.parse(_uriBase + mTabname);
		mColumns = _fields;
		mWhere = _where;
	}

	private void showItemWrapper(int _index) {
		showItem(_index);
	}

	protected final void showItemCommon(final Context _from, final Class<?> _to,
			final Uri _uri, final Bundle _extras) {
		final Intent intent = new Intent(_from, _to);
		intent.setData(_uri);
		intent.putExtra(PackageBase.PARAM + ParamName.TABNAME,
				mTabname);
		intent.putExtra(PackageBase.PARAM + ParamName.BTITLE,
				mBoardTitle);
		intent.putExtras(_extras);
		startActivity(intent);
	}

	protected int markReadOne(final Cursor _c) {
		final int seq = _c.getInt(_c.getColumnIndex(ArticleColumn.SEQ));
		final String user = _c.getString(_c.getColumnIndex(ArticleColumn.USER));
		final String thread = _c.getString(_c.getColumnIndex(ArticleColumn.THREAD));
		if (DBUtils.updateArticleRead(mResolver, mTabname, seq, true)) {
			BroadcastUtils.announceArticleUpdated(
					getActivity(), mTabname, seq, user, thread);
			return 1;
		}
		return 0;
	}

	protected void markAllReadCommon(final String _w) {
		new AlertDialog.Builder(getActivity())
		.setTitle(R.string.menu_mark_all_read)
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setMessage(R.string.mark_all_read_message)
		.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface _dialog, int _which) {
						final Cursor c = getItem(0);
						final int seq =
								c.getInt(c.getColumnIndex(ArticleColumn.SEQ));
						final String where = _w
								+ ArticleColumn.SEQ + "<=" + seq
								+ " AND " + Selection.UNREAD;
						final ContentValues values = new ContentValues();
						values.put(ArticleColumn.READ, 1);
						final int nChanged = mResolver.update(getUriList(),
								values, where, null);
						if (nChanged > 0) {
							DBUtils.updateBoardCount(mResolver, mTabname);
							getLoaderManager().restartLoader(
									0, null, ArticleListFragment.this);
						}
					}
				})
		.setNegativeButton(android.R.string.cancel, null)
		.create()
		.show();
	}

	protected void showPreference() {
		startActivity(new Intent(getActivity(), MainSettings.class));
	}

	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(
					PackageBase.PARAM + BoardColumn.TABNAME);
			final int seq = _intent.getIntExtra(
					PackageBase.PARAM + ArticleColumn.SEQ, -1);
			final String user = _intent.getStringExtra(
					PackageBase.PARAM + ArticleColumn.USER);
			final String thread = _intent.getStringExtra(
					PackageBase.PARAM + ArticleColumn.THREAD);
			if (mTabname != null && tabname != null && mTabname.equals(tabname)
					&& matchingBroadcast(seq, user, thread)) {
				updateTitle();
			}
		}
	}

	private class BoardUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(
					PackageBase.PARAM + BoardColumn.TABNAME);
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
		filter = new IntentFilter(BroadcastType.ARTICLE_UPDATED);
		getActivity().registerReceiver(mReceiverArticleUpdated, filter);
		mReceiverBoardUpdated = new BoardUpdatedReceiver();
		filter = new IntentFilter(BroadcastType.BOARD_UPDATED);
		getActivity().registerReceiver(mReceiverBoardUpdated, filter);
	}

	private void unregisterReceivers() {
		getActivity().unregisterReceiver(mReceiverArticleUpdated);
		getActivity().unregisterReceiver(mReceiverBoardUpdated);
	}

	private interface ColumnIndex {
		int _ID = 0;
		int SEQ = 1;
		int USER = 2;
		int DATE = 3;
		int TITLE = 4;
		int THREAD = 5;
		int BODY = 6;
		int READ = 7;
		int COUNT = 8;
	}
	protected static final String[] COLUMNS_TLIST = {
		BaseColumns._ID,
		ArticleColumn.SEQ,
		ArticleColumn.USER,
		ArticleColumn.DATE,
		ArticleColumn.TITLE,
		ArticleColumn.THREAD,
		ArticleColumn.BODY,
		ArticleColumn.ALLREAD,
		ArticleColumn.CNT,
	};
	protected static final String[] COLUMNS_LIST = {
		BaseColumns._ID,
		ArticleColumn.SEQ,
		ArticleColumn.USER,
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
			itemView.mId = _c.getLong(ColumnIndex._ID);
			itemView.mSeq = _c.getInt(ColumnIndex.SEQ);
			itemView.mUser = _c.getString(ColumnIndex.USER);
			itemView.mDate = _c.getString(ColumnIndex.DATE);
			itemView.mTitle = _c.getString(ColumnIndex.TITLE);
			itemView.mThread = _c.getString(ColumnIndex.THREAD);
			final String body = _c.getString(ColumnIndex.BODY);
			itemView.mRead = _c.getInt(ColumnIndex.READ) != 0;
			if (mColumns.length - 1 >= ColumnIndex.COUNT) {
				itemView.mCount = _c.getInt(ColumnIndex.COUNT);
			} else {
				itemView.mCount = 1;
			}

			String user = itemView.mUser;
			if (itemView.mCount > 1) {
				final int cnt = itemView.mCount - 1;
				user += " (+" + cnt + ")";
			}

			itemView.mDate = DateUtils.KidsToLocalDateString(itemView.mDate);
			itemView.mDate = DateUtils.GetShortDateString(itemView.mDate);

			itemView.mSummary = ArticleUtils.generateSummary(body);

			// Remove "RE:" for threaded list.
			if (mColumns[ColumnIndex.READ].equals(ArticleColumn.ALLREAD)) {
				itemView.mTitle = ArticleUtils.getThreadTitle(itemView.mTitle);
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
			where += "(" + ArticleColumn.TITLE
				+ " LIKE '%" + _constraint + "%' OR "
				+ ArticleColumn.USER + " LIKE '%" + _constraint + "%')";
			if (mHideRead) {
				where += " AND " + mColumns[ColumnIndex.READ] + "=0";
			}
			return mResolver.query(mUri, mColumns, where, null, null);
		}
	}
}
