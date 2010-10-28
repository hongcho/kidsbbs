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
	
	private static final int TYPE_BOARDS = 0;
	private static final int TYPE_LIST = 1;
	private static final int TYPE_TLIST = 2;

	private static final UriMatcher sUriMatcher;
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(PROVIDER, "boards", TYPE_BOARDS);
		sUriMatcher.addURI(PROVIDER, "list/*", TYPE_LIST);
		sUriMatcher.addURI(PROVIDER, "tlist/*", TYPE_TLIST);
	}
	
	private static final String TYPESTR_BASE = "vnd.sori.cursor.";
	private static final String TYPESTR_DIR_BASE =
			TYPESTR_BASE + "dir/vnd.sori.";

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
		case TYPE_LIST:
			return TYPESTR_DIR_BASE + "list";
		case TYPE_TLIST:
			return TYPESTR_DIR_BASE + "tlist";
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
		String orderby = KEYA_SEQ + " DESC";
		String groupby = null;
		int type = sUriMatcher.match(_uri);
		switch (type) {
		case TYPE_LIST:
		case TYPE_TLIST:
			break;
		case TYPE_BOARDS:
			orderby = "LOWER(" + KEYB_TITLE + ")";
			break;
		default:
			throw new IllegalArgumentException(
					"query: Unsupported URI: " + _uri);
		}
		qb.setTables(table);

		if (!TextUtils.isEmpty(_sortOrder)) {
			orderby = _sortOrder;
		}

		Cursor c = qb.query(mDB, _projection, _selection, _selectionArgs, groupby,
				null, orderby);
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
			Uri uri = ContentUris.withAppendedId(_uri, row);
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
		int count = mDB.delete(table, _selection, _selectionArgs);
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
		int count = mDB.update(table, _values, _selection, _selectionArgs);
		getContext().getContentResolver().notifyChange(_uri, null);
		return count;
	}

	private String getTableName(Uri _uri) {
		int type = sUriMatcher.match(_uri);
		switch (type) {
		case TYPE_LIST:
		case TYPE_TLIST:
			List<String> segments = _uri.getPathSegments();
			if (segments.size() == 2) {
				return segments.get(1);
			}
			break;
		case TYPE_BOARDS:
			return DB_TABLE;
		}
		Log.e(TAG, "getTableName: Unsupported URI (" + type + "): " +
				_uri);
		return null;
	}

	private static final String getViewname(String _tabname) {
		return _tabname + "_view";
	}

	// Common ID...
	public static final String KEY_ID = "_id";
	private static final String KEY_ID_DEF =
			KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT";

	// Board table
	public static final String KEYB_TABNAME = "tabname";
	private static final String KEYB_TABNAME_DEF =
			KEYB_TABNAME + " CHAR(36) NOT NULL UNIQUE";
	public static final String KEYB_TITLE = "title";
	private static final String KEYB_TITLE_DEF =
			KEYB_TITLE + " VARCHAR(40) NOT NULL";
	public static final String KEYB_STATE = "state";
	private static final String KEYB_STATE_DEF =
			KEYB_STATE + " TINYINT DEFAULT 0";

	public static final int STATE_UNDEF = 0; // first update is not done yet
	public static final int STATE_INITIALIZE = 1; // table is created, not updated
	public static final int STATE_INITIALIZING = 2; // first updating is going on
	public static final int STATE_DONE = 3; // update is done
	public static final int STATE_UPDATING = 4; // incremental update is going on
	public static final int STATE_PAUSED = 5; // no longer updating

	// Article table
	public static final String KEYA_SEQ = "seq";
	private static final String KEYA_SEQ_DEF =
			KEYA_SEQ + " INTEGER NOT NULL UNIQUE";
	public static final String KEYA_USER = "user";
	private static final String KEYA_USER_DEF =
			KEYA_USER + " CHAR(12) NOT NULL";
	public static final String KEYA_DATE = "date";
	private static final String KEYA_DATE_DEF =
			KEYA_DATE + " DATETIME NOT NULL";
	public static final String KEYA_TITLE = "title";
	private static final String KEYA_TITLE_DEF =
			KEYA_TITLE + " VARCHAR(40) NOT NULL";
	public static final String KEYA_THREAD = "thread";
	private static final String KEYA_THREAD_DEF =
			KEYA_THREAD + " CHAR(32) NOT NULL";
	public static final String KEYA_READ = "read";
	private static final String KEYA_READ_DEF =
			KEYA_READ + " TINYINT DEFAULT 0";
	
	public static final String KEYA_ALLREAD = "allread";
	public static final String KEYA_ALLREAD_FIELD =
			"MIN(" + KEYA_READ + ") AS " + KEYA_ALLREAD;

	public static final String KEYA_CNT = "cnt";
	public static final String KEYA_CNT_FIELD = "COUNT(*) AS " + KEYA_CNT;
	
	public static final String ORDER_BY_ID = KEY_ID + " ASC";

	private static final String DB_NAME = "kidsbbs.db";
	private static final int DB_VERSION = 1;
	private static final String DB_TABLE = "boards";

	private SQLiteDatabase mDB;

	private static class DBHelper extends SQLiteOpenHelper {
		private Context mContext;
		private HashMap<String, Boolean> mUpdateMap =
				new HashMap<String, Boolean>();
		
		public DBHelper(Context _context, String _name, CursorFactory _factory,
				int _version) {
			super(_context, _name, _factory, _version);
			mContext = _context;
		}

		@Override
		public void onCreate(SQLiteDatabase _db) {
			createMainTable(_db);
			
			// Board table...
			String[] tabnames = mContext.getResources().getStringArray(
					R.array.board_table_names);
			String[] typeNames = mContext.getResources().getStringArray(
					R.array.board_type_names);
			String[] nameMapKeys = mContext.getResources().getStringArray(
					R.array.board_name_map_in);
			String[] nameMapValues = mContext.getResources().getStringArray(
					R.array.board_name_map_out);
			String[] defaultMapKeys = mContext.getResources().getStringArray(
					R.array.default_board_tables);
			
			HashMap<String, String> nameMap = new HashMap<String, String>();
			for (int i = 0; i < nameMapKeys.length; ++i) {
				nameMap.put(nameMapKeys[i], nameMapValues[i]);
			}
			if (mUpdateMap.isEmpty()) {
				for (int i = 0; i < tabnames.length; ++i) {
					mUpdateMap.put(tabnames[i], false);
				}
				for (int i = 0; i < defaultMapKeys.length; ++i) {
					mUpdateMap.put(defaultMapKeys[i], true);
				}
			}
			
			// Populate...
			for (int i = 0; i < tabnames.length; ++i) {
				String[] p = BoardInfo.parseTabname(tabnames[i]);
				int type = Integer.parseInt(p[0]);
				String name = p[1];
				String title;
				if (type > 0 && type < typeNames.length) {
					title = typeNames[type] + " ";
				} else {
					title = "";
				}
				String mapped = nameMap.get(name);
				if (mapped != null) {
					title += mapped;
				} else {
					title += name;
				}
				addBoard(_db, new BoardInfo(tabnames[i], title,
						mUpdateMap.get(tabnames[i]) ?
								STATE_INITIALIZE : STATE_UNDEF));
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase _db, int _old, int _new) {
			final String[] FIELDS = {
				KEYB_TITLE,
				KEYB_STATE,
			};   
			Log.w(TAG, "Upgrading database from version " + _old + " to " +
					_new + ", which will destroy all old data");
			
			mUpdateMap.clear();
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(DB_TABLE);
			Cursor c = qb.query(_db, FIELDS, null, null, null, null, null);
			if (c != null) {
				if (c.getCount() > 0) {
					c.moveToFirst();
					do {
						String tabname = c.getString(c.getColumnIndex(KEYB_TABNAME));
						int state = Integer.parseInt(c.getString(
								c.getColumnIndex(KEYB_STATE)));
						mUpdateMap.put(tabname,
								state != STATE_UNDEF && state != STATE_PAUSED);
						dropArticleTable(_db, tabname);
					} while (c.moveToNext());
				}
				c.close();
			}

			dropMainTable(_db);
			onCreate(_db);
		}
		
		private void addBoard(SQLiteDatabase _db, BoardInfo _info) {
			ContentValues values = new ContentValues();
			values.put(KEYB_TABNAME, _info.getTabname());
			values.put(KEYB_TITLE, _info.getTitle());
			values.put(KEYB_STATE, _info.getState());
			if (_db.insert(DB_TABLE, null, values) < 0) {
				throw new SQLException(
						"addBoard: Failed to insert row into " + DB_TABLE);
			}
			createArticleTable(_db, _info.getTabname());
		}
		
		private void createMainTable(SQLiteDatabase _db) {
			_db.execSQL("CREATE TABLE " + DB_TABLE + " (" +
					KEY_ID_DEF + "," +
					KEYB_TABNAME_DEF + "," +
					KEYB_TITLE_DEF + "," +
					KEYB_STATE_DEF + ");");
		}
		
		private void dropMainTable(SQLiteDatabase _db) {
			_db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
		}

		private void createArticleTable(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("CREATE TABLE " + _tabname + " (" +
					KEY_ID_DEF + "," +
					KEYA_SEQ_DEF + "," +
					KEYA_USER_DEF + "," +
					KEYA_DATE_DEF + "," +
					KEYA_TITLE_DEF + "," +
					KEYA_THREAD_DEF + "," +
					KEYA_READ_DEF + ");");
			_db.execSQL("CREATE VIEW " + getViewname(_tabname) + " AS " +
					"SELECT * FROM " + _tabname +
					" ORDER BY " + KEYA_SEQ + " DESC;");
		}
		
		private void dropArticleTable(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("DROP TABLE IF EXISTS " + _tabname);
			_db.execSQL("DROP VIEW IF EXISTS " + getViewname(_tabname));
		}
	}
}
