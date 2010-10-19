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

import java.util.HashMap;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class KidsBbsProvider extends ContentProvider {
	private static final String TAG = "KidsBbsProvider";
	private static final String PROVIDER = "org.sori.provider.kidsbbs";
	private static final String CONTENT_URI_BASE =
			"content://" + PROVIDER + "/";

	public static final Uri CONTENT_URI_BOARDS = Uri.parse(
			CONTENT_URI_BASE + "boards");
	public static final String CONTENT_URISTR_LIST =
			CONTENT_URI_BASE + "list/";
	public static final String CONTENT_URISTR_TLIST =
			CONTENT_URI_BASE + "tlist/";
	public static final String CONTENT_URISTR_THREAD =
			CONTENT_URI_BASE + "thread/";
	public static final String CONTENT_URISTR_USER =
			CONTENT_URI_BASE + "user/";
	
	private static final int TYPE_BOARDS = 0;
	private static final int TYPE_BOARDS_ID = 1;
	private static final int TYPE_LIST = 2;
	private static final int TYPE_LIST_ID = 3;
	private static final int TYPE_TLIST = 4;
	private static final int TYPE_TLIST_ID = 5;
	private static final int TYPE_THREAD = 6;
	private static final int TYPE_THREAD_ID = 7;
	private static final int TYPE_USER = 8;
	private static final int TYPE_USER_ID = 9;

	private static final UriMatcher sUriMatcher;
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(PROVIDER, "boards", TYPE_BOARDS);
		sUriMatcher.addURI(PROVIDER, "boards/#", TYPE_BOARDS_ID);
		sUriMatcher.addURI(PROVIDER, "list/*", TYPE_LIST);
		sUriMatcher.addURI(PROVIDER, "list/*/#", TYPE_LIST_ID);
		sUriMatcher.addURI(PROVIDER, "tlist/*", TYPE_TLIST);
		sUriMatcher.addURI(PROVIDER, "tlist/*/#", TYPE_TLIST_ID);
		sUriMatcher.addURI(PROVIDER, "thread/*", TYPE_THREAD);
		sUriMatcher.addURI(PROVIDER, "thread/*/#", TYPE_THREAD_ID);
		sUriMatcher.addURI(PROVIDER, "user/*", TYPE_USER);
		sUriMatcher.addURI(PROVIDER, "user/*/#", TYPE_USER_ID);
	}
	
	private static final String TYPESTR_BASE = "vnd.sori.cursor.";
	private static final String TYPESTR_DIR_BASE =
			TYPESTR_BASE + "dir/vnd.sori.";
	private static final String TYPESTR_ITEM_BASE =
			TYPESTR_BASE + "item/vnd.sori.";

	@Override
	public boolean onCreate() {
		DBHelper dbHelper = new DBHelper(getContext(), DB_NAME, null,
				DB_VERSION);
		mDB = dbHelper.getWritableDatabase();
		return mDB != null;
	}

	@Override
	public String getType(Uri _uri) {
		switch (sUriMatcher.match(_uri)) {
		case TYPE_BOARDS:
			return TYPESTR_DIR_BASE + "boards";
		case TYPE_BOARDS_ID:
			return TYPESTR_ITEM_BASE + "boards";
		case TYPE_LIST:
			return TYPESTR_DIR_BASE + "list";
		case TYPE_LIST_ID:
			return TYPESTR_ITEM_BASE + "list";
		case TYPE_TLIST:
			return TYPESTR_DIR_BASE + "tlist";
		case TYPE_TLIST_ID:
			return TYPESTR_ITEM_BASE + "tlist";
		case TYPE_THREAD:
			return TYPESTR_DIR_BASE + "thread";
		case TYPE_THREAD_ID:
			return TYPESTR_ITEM_BASE + "thread";
		case TYPE_USER:
			return TYPESTR_DIR_BASE + "user";
		case TYPE_USER_ID:
			return TYPESTR_ITEM_BASE + "user";
		}
		Log.e(TAG, "getType: Unsupported URI: " + _uri);
		return null;
	}

	@Override
	public Cursor query(Uri _uri, String[] _projection, String _selection,
			String[] _selectionArgs, String _sortOrder) {
		String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException(
					"query: Unsupported URI: " + _uri);
		}
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		String order = KEYA_SEQ + " DESC";
		int type = sUriMatcher.match(_uri);
		switch (type) {
		case TYPE_LIST_ID:
		case TYPE_TLIST_ID:
		case TYPE_THREAD_ID:
		case TYPE_USER_ID:
			qb.appendWhere(KEY_ID + "=" + _uri.getPathSegments().get(2));
		case TYPE_LIST:
		case TYPE_TLIST:
		case TYPE_THREAD:
		case TYPE_USER:
			break;
		case TYPE_BOARDS_ID:
			qb.appendWhere(KEY_ID + "=" + _uri.getPathSegments().get(1));
		case TYPE_BOARDS:
			order = "LOWER(" + KEYB_TITLE + ")";
			break;
		default:
			throw new IllegalArgumentException(
					"query: Unsupported URI: " + _uri);
		}
		qb.setTables(table);

		if (!TextUtils.isEmpty(_sortOrder)) {
			order = _sortOrder;
		}

		Cursor c = qb.query(mDB, _projection, _selection, _selectionArgs, null,
				null, order);
		if (c != null) {
			// Register the context's ContentResolver to be notified
			// if the cursor result set changes.
			c.setNotificationUri(getContext().getContentResolver(), _uri);
		}
		return c;
	}

	@Override
	public Uri insert(Uri _uri, ContentValues _values) {
		String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException(
					"insert: Unsupported URI: " + _uri);
		}
		long row = mDB.insert(table, null, _values);
		if (row > 0) {
			Uri uri = ContentUris.withAppendedId(getContentUriWithoutId(_uri),
					row);
			getContext().getContentResolver().notifyChange(uri, null);
			return uri;
		}
		throw new SQLException("insert: Failed to insert row into " + _uri);
	}

	@Override
	public int delete(Uri _uri, String _selection, String[] _selectionArgs) {
		String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException(
					"delete: Unsupported URI: " + _uri);
		}
		int count;
		String idString = "";
		switch (sUriMatcher.match(_uri)) {
		case TYPE_LIST_ID:
		case TYPE_TLIST_ID:
		case TYPE_THREAD_ID:
		case TYPE_USER_ID:
			idString = _uri.getPathSegments().get(2);
			break;
		case TYPE_LIST:
		case TYPE_TLIST:
		case TYPE_THREAD:
		case TYPE_USER:
			break;
		case TYPE_BOARDS_ID:
			idString = _uri.getPathSegments().get(1);
		case TYPE_BOARDS:
			break;
		default:
			throw new IllegalArgumentException(
					"delete: Unsupported URI: " + _uri);
		}
		if (idString.equals("")) {
			count = mDB.delete(table, _selection, _selectionArgs);
		} else {
			count = mDB.delete(table,
					KEY_ID  + "=" + idString +
						(!TextUtils.isEmpty(_selection) ?
							" AND (" + _selection + ")" : ""),
					_selectionArgs);
		}
		getContext().getContentResolver().notifyChange(_uri, null);
		return count;
	}

	@Override
	public int update(Uri _uri, ContentValues _values, String _selection,
			String[] _selectionArgs) {
		String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException(
					"update: Unsupported URI: " + _uri);
		}
		int count;
		String idString = "";
		switch (sUriMatcher.match(_uri)) {
		case TYPE_LIST_ID:
		case TYPE_TLIST_ID:
		case TYPE_THREAD_ID:
		case TYPE_USER_ID:
			idString = _uri.getPathSegments().get(2);
			break;
		case TYPE_LIST:
		case TYPE_TLIST:
		case TYPE_THREAD:
		case TYPE_USER:
			break;
		case TYPE_BOARDS_ID:
			idString = _uri.getPathSegments().get(1);
		case TYPE_BOARDS:
			break;
		default:
			throw new IllegalArgumentException(
					"update: Unsupported URI: " + _uri);
		}
		if (idString.equals("")) {
			count = mDB.update(table, _values, _selection, _selectionArgs);
		} else {
			count = mDB.update(table, _values,
					KEY_ID  + "=" + idString +
					(!TextUtils.isEmpty(_selection) ?
							" AND (" + _selection + ")" : ""),
					_selectionArgs);
		}
		getContext().getContentResolver().notifyChange(_uri, null);
		return count;
	}

	private String getTableName(Uri _uri) {
		int type = sUriMatcher.match(_uri);
		switch (type) {
		case TYPE_LIST_ID:
		case TYPE_TLIST_ID:
		case TYPE_THREAD_ID:
		case TYPE_USER_ID:
		case TYPE_LIST:
		case TYPE_TLIST:
		case TYPE_THREAD:
		case TYPE_USER:
			return _uri.getPathSegments().get(1);
		case TYPE_BOARDS_ID:
		case TYPE_BOARDS:
			return DB_TABLE;
		default:
			Log.e(TAG, "getTableName: Unsupported URI (" + type + "): " +
					_uri);
			return null;
		}
	}

	private Uri getContentUriWithoutId(Uri _uri) {
		List<String> segments = _uri.getPathSegments();
		int length = segments.size();
		String uriString = CONTENT_URI_BASE;
		switch (sUriMatcher.match(_uri)) {
		case TYPE_LIST_ID:
		case TYPE_TLIST_ID:
		case TYPE_THREAD_ID:
		case TYPE_USER_ID:
			if (length != 3) {
				Log.e(TAG, "getContentUriWithoutId: Unsupported URI: " + _uri);
				return null;
			}
			uriString += segments.get(0) + "/" + segments.get(1);
			break;
		case TYPE_LIST:
		case TYPE_TLIST:
		case TYPE_THREAD:
		case TYPE_USER:
			if (length != 2) {
				Log.e(TAG, "getConentUriWithoutId: Unsupported URI: " + _uri);
				return null;
			}
			uriString += segments.get(0) + "/" + segments.get(1);
			break;
		case TYPE_BOARDS_ID:
		case TYPE_BOARDS:
			uriString += segments.get(0);
			break;
		default:
			Log.e(TAG, "getContentUriWithoutId: Unsupported URI: " + _uri);
			return null;
		}
		return Uri.parse(uriString);
	}
	
	private static final String getThreadedTabName(String _tabname) {
		return _tabname + "_threaded";
	}

	// Common ID...
	public static final String KEY_ID = "_id";
	private static final String KEY_ID_DEF =
			KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT";

	// Board table
	public static final String KEYB_TABNAME = "tabname";
	private static final String KEYB_TABNAME_DEF =
			KEYB_TABNAME + " CHAR(36) NOT NULL";
	public static final String KEYB_TITLE = "title";
	private static final String KEYB_TITLE_DEF =
			KEYB_TITLE + " VARCHAR(40) NOT NULL";
	public static final String KEYB_LASTTIME = "lasttime";
	private static final String KEYB_LASTTIME_DEF =
			KEYB_LASTTIME + " DATETIME";
	public static final String KEYB_NEWCOUNT = "newcount";
	private static final String KEYB_NEWCOUNT_DEF =
			KEYB_NEWCOUNT + " INTEGER DEFAULT 0";

	// Article table
	public static final String KEYA_SEQ = "seq";
	private static final String KEYA_SEQ_DEF =
			KEYA_SEQ + " INTEGER NOT NULL";
	public static final String KEYA_AUTHOR = "author";
	private static final String KEYA_AUTHOR_DEF =
			KEYA_AUTHOR + " VARCHAR(40) NOT NULL";
	public static final String KEYA_DATE = "date";
	private static final String KEYA_DATE_DEF =
			KEYA_DATE + " DATETIME NOT NULL";
	public static final String KEYA_TITLE = "title";
	private static final String KEYA_TITLE_DEF =
			KEYA_TITLE + " VARCHAR(40) NOT NULL";
	public static final String KEYA_THREAD = "thread";
	private static final String KEYA_THREAD_DEF =
			KEYA_THREAD + " CHAR(32) NOT NULL";
	public static final String KEYA_BODY = "body";
	private static final String KEYA_BODY_DEF =
			KEYA_BODY + " TEXT";
	public static final String KEYA_READ = "read";
	private static final String KEYA_READ_DEF =
			KEYA_READ + " BOOLEAN DEFAULT FALSE";
	
	public static final String KEYT_COUNT = "count";
	private static final String KEYT_COUNT_DEF =
			KEYT_COUNT + " INTEGER NOT NULL";

	private static final String DB_NAME = "kidsbbs.db";
	private static final int DB_VERSION = 1;
	private static final String DB_TABLE = "boards";

	private SQLiteDatabase mDB;

	private static class DBHelper extends SQLiteOpenHelper {
		private final String[] FIELDS1 = { KEYB_TITLE };   
		
		private String[] mTabnames;
		private String[] mTypeNames;
		private String[] mNameMapKeys;
		private String[] mNameMapValues;
		private HashMap<String, String> mNameMap = new HashMap<String, String>();
		
		public DBHelper(Context _context, String _name, CursorFactory _factory,
				int _version) {
			super(_context, _name, _factory, _version);
			
			// Board table...
			mTabnames = _context.getResources().getStringArray(
					R.array.board_table_names);
			mTypeNames = _context.getResources().getStringArray(
					R.array.board_type_names);
			mNameMapKeys = _context.getResources().getStringArray(
					R.array.board_name_map_in);
			mNameMapValues = _context.getResources().getStringArray(
					R.array.board_name_map_out);
			for (int i = 0; i < mNameMapKeys.length; ++i) {
				mNameMap.put(mNameMapKeys[i], mNameMapValues[i]);
			}
		}

		@Override
		public void onCreate(SQLiteDatabase _db) {
			createMainTable(_db);
			
			// Populate...
			for (int i = 0; i < mTabnames.length; ++i) {
				String[] p = BoardInfo.parseTabName(mTabnames[i]);
				int type = Integer.parseInt(p[0]);
				String name = p[1];
				String title;
				if (type > 0 && type < mTypeNames.length) {
					title = mTypeNames[type] + " ";
				} else {
					title = "";
				}
				String mapped = mNameMap.get(name);
				if (mapped != null) {
					title += mapped;
				} else {
					title += name;
				}
				addBoard(_db, new BoardInfo(mTabnames[i], title));
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase _db, int _old, int _new) {
			Log.w(TAG, "Upgrading database from version " + _old + " to " +
					_new + ", which will destroy all old data");
			
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(DB_TABLE);
			Cursor c = qb.query(_db, FIELDS1, null, null, null, null, null);
			if (c != null && c.moveToFirst()) {
				do {
					String tabname = c.getString(c.getColumnIndex(KEYB_TABNAME));
					dropArticleTable(_db, tabname);
				} while (c.moveToNext());
			}
			c.close();

			dropMainTable(_db);
			onCreate(_db);
		}
		
		private void addBoard(SQLiteDatabase _db, BoardInfo _info) {
			ContentValues values = new ContentValues();
			values.put(KEYB_TABNAME, _info.getTabName());
			values.put(KEYB_TITLE, _info.getTitle());
			values.put(KEYB_LASTTIME, "0000-00-00 00:00:00");
			values.put(KEYB_NEWCOUNT, "0");
			if (_db.insert(DB_TABLE, null, values) < 0) {
				throw new SQLException(
						"addBoard: Failed to insert row into " + DB_TABLE);
			}
			createArticleTable(_db, _info.getTabName());
		}
		
		private void createMainTable(SQLiteDatabase _db) {
			_db.execSQL("CREATE TABLE " + DB_TABLE + " (" +
					KEY_ID_DEF + "," +
					KEYB_TABNAME_DEF + "," +
					KEYB_TITLE_DEF + "," +
					KEYB_LASTTIME_DEF + "," +
					KEYB_NEWCOUNT_DEF + ");");
		}
		
		private void dropMainTable(SQLiteDatabase _db) {
			_db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
		}

		private void createArticleTable(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("CREATE TABLE " + _tabname + " (" +
					KEY_ID_DEF + "," +
					KEYA_SEQ_DEF + "," +
					KEYA_AUTHOR_DEF + "," +
					KEYA_DATE_DEF + "," +
					KEYA_TITLE_DEF + "," +
					KEYA_THREAD_DEF + "," +
					KEYA_BODY_DEF + "," +
					KEYA_READ_DEF + ");");
			_db.execSQL("CREATE TABLE " + getThreadedTabName(_tabname) + " (" +
					KEY_ID_DEF + "," +
					KEYA_SEQ_DEF + "," +
					KEYA_AUTHOR_DEF + "," +
					KEYA_DATE_DEF + "," +
					KEYA_TITLE_DEF + "," +
					KEYA_THREAD_DEF + "," +
					KEYA_BODY_DEF + "," +
					KEYT_COUNT_DEF + ");");
		}
		
		private void dropArticleTable(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("DROP TABLE IF EXISTS " + _tabname);
			_db.execSQL("DROP TABLE IF EXISTS " +
					getThreadedTabName(_tabname));
		}
	}
}
