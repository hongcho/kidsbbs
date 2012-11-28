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

import org.sori.kidsbbs.KidsBbs.IntentUri;
import org.sori.kidsbbs.KidsBbs.NotificationType;
import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardState;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUri;
import org.sori.kidsbbs.provider.ArticleProvider.OrderBy;
import org.sori.kidsbbs.provider.ArticleProvider.Selection;
import org.sori.kidsbbs.ui.preference.MainSettings;
import org.sori.kidsbbs.util.DBUtils;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public class BoardListFragment extends ListFragment
		implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ContentResolver mResolver;
	private NotificationManager mNotificationManager;

	private BoardsAdapter mAdapter;

	private String mTitleBase;

	// Board selection dialog stuff
	private String[] mTabnames;
	private boolean[] mSelectedOld;
	private boolean[] mSelectedNew;

	@Override
	public void onActivityCreated(Bundle _savedInstanceState) {
		super.onActivityCreated(_savedInstanceState);

		mTitleBase = getResources().getString(R.string.title_blist);

		mResolver = getActivity().getContentResolver();
		mNotificationManager =
				(NotificationManager) getActivity().getSystemService(
						Context.NOTIFICATION_SERVICE);

		mAdapter = new BoardsAdapter(getActivity());
		setListAdapter(mAdapter);
		
		updateTitle();
		setHasOptionsMenu(true);
		registerForContextMenu(getListView());
		
		getLoaderManager().initLoader(0, null, this);
		
		if (_savedInstanceState != null) {
			restoreInstanceStates(_savedInstanceState);
		} else {
			getListView().setChoiceMode(ListView.CHOICE_MODE_NONE);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		mNotificationManager.cancel(NotificationType.NEW_ARTICLE);
		updateTitle();
	}

	@Override
	public void onSaveInstanceState(Bundle _outState) {
		super.onSaveInstanceState(_outState);
		saveInstanceStates(_outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu _menu, MenuInflater _inflater) {
		_inflater.inflate(R.menu.board_list, _menu);
		
		// Access non-public Android icons.
		_menu.findItem(R.id.menu_refresh).setIcon(
				getResources().getIdentifier(
						"android:drawable/ic_menu_refresh", null, null));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_select:
			selectBoards();
			return true;
		case R.id.menu_refresh:
			DBUtils.updateBoardTable(getActivity(), null);
			return true;
		case R.id.menu_preferences:
			startActivity(new Intent(getActivity(), MainSettings.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onListItemClick(ListView _l, View _v, int _position, long _id) {
		showItem(_position);
	}

	public Loader<Cursor> onCreateLoader(int _id, Bundle _args) {
		// This is called when a new Loader needs to be created. This
		// has only one Loader, so we don't care about the ID.
		final String ORDERBY = OrderBy.COUNT_DESC + "," + OrderBy.TITLE;
		return new CursorLoader(getActivity(), ContentUri.BOARDS, COLUMNS,
				Selection.STATE_ACTIVE, null, ORDERBY);
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

	private void updateTitle() {
		getActivity().setTitle("(" + mAdapter.getCount() + ") " + mTitleBase);
	}

	private void showItem(final int _index) {
		final Cursor c = (Cursor) mAdapter.getItem(_index);
		final Intent intent = new Intent(getActivity(), ThreadListActivity.class);
		intent.setData(IntentUri.TLIST);
		intent.putExtra(PackageBase.PARAM + ParamName.TABNAME,
				c.getString(ColumnIndex.TABNAME));
		intent.putExtra(PackageBase.PARAM + ParamName.BTITLE,
				c.getString(ColumnIndex.TITLE));
		startActivity(intent);
	}

	private void selectBoards() {
		final String[] PROJECTION = {
				BoardColumn.TABNAME,
				BoardColumn.TITLE,
				BoardColumn.STATE,
		};
		final String ORDERBY = OrderBy.STATE_DESC + "," + OrderBy.TITLE;
		String[] titles = null;
		final Cursor c = mResolver.query(ContentUri.BOARDS, PROJECTION,
				null, null, ORDERBY);
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
							BoardColumn.TABNAME));
					titles[i] = c.getString(c.getColumnIndex(
							BoardColumn.TITLE));
					mSelectedOld[i] = c.getInt(c.getColumnIndex(
							BoardColumn.STATE)) != BoardState.PAUSED;
					mSelectedNew[i] = mSelectedOld[i];
					++i;
				} while (c.moveToNext());
			}
			c.close();
		}
		if (titles != null) {
			new AlertDialog.Builder(getActivity())
			.setTitle(R.string.board_selection_title)
			.setMultiChoiceItems(titles, mSelectedNew,
					new DialogInterface.OnMultiChoiceClickListener() {
						public void onClick(DialogInterface _dialog,
								int _which, boolean _isChecked) {
							mSelectedNew[_which] = _isChecked;
						}
					})
			.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface _dialog, int _which) {
							int nUpdated = 0;
							int length = mSelectedNew.length;
							for (int i = 0; i < length; ++i) {
								if (mSelectedNew[i] == mSelectedOld[i]) {
									continue;
								}
								++nUpdated;
								ContentValues values = new ContentValues();
								values.put(BoardColumn.STATE,
										mSelectedNew[i] ? BoardState.SELECTED
												: BoardState.PAUSED);
								mResolver.update(ContentUri.BOARDS, values,
										Selection.TABNAME,
										new String[] { mTabnames[i] });
							}
							if (nUpdated > 0) {
								DBUtils.updateBoardTable(getActivity(), "");
								getLoaderManager().restartLoader(
										0, null, BoardListFragment.this);
							}
						}
					})
			.setNegativeButton(android.R.string.cancel, null)
			.create()
			.show();
		}
	}

	private interface ColumnIndex {
		int _ID = 0;
		int TABNAME = 1;
		int TITLE = 2;
		int COUNT = 3;
	}
	private static final String[] COLUMNS = {
		BaseColumns._ID,
		BoardColumn.TABNAME,
		BoardColumn.TITLE,
		BoardColumn.COUNT,
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
			final String WHERE = Selection.STATE_ACTIVE
					+ " AND " + BoardColumn.TITLE + " LIKE '%" + _constraint + "%'";
			final String ORDERBY = OrderBy.COUNT_DESC + "," + OrderBy.TITLE;
			return mResolver.query(ContentUri.BOARDS, COLUMNS, WHERE, null,
					ORDERBY);
		}
	}
}
