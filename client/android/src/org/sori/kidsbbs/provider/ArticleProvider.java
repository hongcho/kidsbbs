// Copyright (c) 2010-2011, Younghong "Hong" Cho <hongcho@sori.org>.
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
package org.sori.kidsbbs.provider;

import java.util.List;

import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardState;
import org.sori.kidsbbs.provider.ArticleDatabase.Table;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public class ArticleProvider extends ContentProvider {
	private static final String TAG = "ArticleProvider";

	public interface ContentUriString {
		String BASE = "content://" + PackageBase.PROVIDER + "/";
		String LIST = BASE + "list/";
		String TLIST = BASE + "tlist/";
	}

	public interface ContentUri {
		Uri BOARDS = Uri.parse(ContentUriString.BASE + "boards");
	}

	public interface Selection {
		String TABNAME = BoardColumn.TABNAME + "=?";
		String STATE_ACTIVE = BoardColumn.STATE + "!=" + BoardState.PAUSED;
		String SEQ = ArticleColumn.SEQ + "=?";
		String UNREAD = ArticleColumn.READ + "=0";
		String ALLUNREAD = ArticleColumn.ALLREAD + "=0";
	}

	public interface OrderBy {
		String _ID = BaseColumns._ID + " ASC";
		String SEQ_DESC = ArticleColumn.SEQ + " DESC";
		String SEQ_ASC = ArticleColumn.SEQ + " ASC";
		String COUNT_DESC = BoardColumn.COUNT + " DESC";
		String STATE_ASC = BoardColumn.STATE + " ASC";
		String STATE_DESC = BoardColumn.STATE + " DESC";
		String TITLE = "LOWER(" + BoardColumn.TITLE + ")";
	}

	private interface Type {
		int BOARDS = 0;
		int LIST = 1;
		int TLIST = 2;
	}

	private static final UriMatcher sUriMatcher;
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(PackageBase.PROVIDER, "boards", Type.BOARDS);
		sUriMatcher.addURI(PackageBase.PROVIDER, "list/*", Type.LIST);
		sUriMatcher.addURI(PackageBase.PROVIDER, "tlist/*", Type.TLIST);
	}

	private interface TypeString {
		String BASE = "vnd.sori.cursor.";
		String DIR_BASE = BASE + "dir/vnd.sori.";
	}

	private ContentResolver mResolver;
	private SQLiteDatabase mDB;

	@Override
	public boolean onCreate() {
		mResolver = getContext().getContentResolver();

		final ArticleDatabase db = new ArticleDatabase(getContext());
		mDB = db.getWritableDatabase();
		return mDB != null;
	}

	@Override
	public String getType(Uri _uri) {
		switch (sUriMatcher.match(_uri)) {
		case Type.BOARDS:
			return TypeString.DIR_BASE + "boards";
		case Type.LIST:
			return TypeString.DIR_BASE + "list";
		case Type.TLIST:
			return TypeString.DIR_BASE + "tlist";
		}
		Log.e(TAG, "getType: Unsupported URI: " + _uri);
		return null;
	}

	@Override
	public Cursor query(Uri _uri, String[] _projection, String _selection,
			String[] _selectionArgs, String _sortOrder) {
		final String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("query: Unsupported URI: "
					+ _uri);
		}
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		String orderby = OrderBy.SEQ_DESC;
		int type = sUriMatcher.match(_uri);
		switch (type) {
		case Type.LIST:
		case Type.TLIST:
			break;
		case Type.BOARDS:
			orderby = OrderBy.TITLE;
			break;
		default:
			throw new IllegalArgumentException("query: Unsupported URI: "
					+ _uri);
		}
		qb.setTables(table);

		if (!TextUtils.isEmpty(_sortOrder)) {
			orderby = _sortOrder;
		}

		final Cursor c = qb.query(mDB, _projection, _selection, _selectionArgs,
				null, null, orderby);
		if (c != null) {
			// Register the context's ContentResolver to be notified
			// if the cursor result set changes.
			c.setNotificationUri(mResolver, _uri);
		}
		return c;
	}

	@Override
	public Uri insert(Uri _uri, ContentValues _values) {
		final String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("insert: Unsupported URI: "
					+ _uri);
		}
		final long row = mDB.insert(table, null, _values);
		if (row > 0) {
			final Uri uri = ContentUris.withAppendedId(_uri, row);
			mResolver.notifyChange(uri, null);
			return uri;
		}
		throw new SQLException("insert: Failed to insert row into " + _uri);
	}

	@Override
	public int delete(Uri _uri, String _selection, String[] _selectionArgs) {
		final String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("delete: Unsupported URI: "
					+ _uri);
		}
		final int count = mDB.delete(table, _selection, _selectionArgs);
		mResolver.notifyChange(_uri, null);
		return count;
	}

	@Override
	public int update(Uri _uri, ContentValues _values, String _selection,
			String[] _selectionArgs) {
		final String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("update: Unsupported URI: "
					+ _uri);
		}
		final int count = mDB.update(table, _values, _selection, _selectionArgs);
		mResolver.notifyChange(_uri, null);
		return count;
	}

	private String getTableName(Uri _uri) {
		final int type = sUriMatcher.match(_uri);
		final List<String> segments = _uri.getPathSegments();
		switch (type) {
		case Type.LIST:
			if (segments.size() == 2) {
				return segments.get(1);
			}
			break;
		case Type.TLIST:
			if (segments.size() == 2) {
				return ArticleDatabase.getViewName(segments.get(1));
			}
			break;
		case Type.BOARDS:
			return Table.BOARDS;
		}
		Log.e(TAG, "getTableName: Unsupported URI (" + type + "): " + _uri);
		return null;
	}
}
