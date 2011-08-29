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

import java.util.HashMap;
import java.util.List;

import org.sori.kidsbbs.KidsBbs;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.data.BoardInfo;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ArticleProvider extends ContentProvider {
	private static final String TAG = "ArticleProvider";
	private static final String PROVIDER = KidsBbs.PKG_BASE + "provider";
	private static final String CONTENT_URI_BASE = "content://" + PROVIDER
			+ "/";

	public static final Uri CONTENT_URI_BOARDS = Uri.parse(
			CONTENT_URI_BASE + "boards");
	public static final String CONTENT_URISTR_LIST = CONTENT_URI_BASE + "list/";
	public static final String CONTENT_URISTR_TLIST = CONTENT_URI_BASE + "tlist/";

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

	// Common ID
	public static final String KEY_ID = "_id";
	// Board meta table
	public static final String KEYB_TABNAME = "tabname";
	public static final String KEYB_TITLE = "title";
	public static final String KEYB_COUNT = "count";
	public static final String KEYB_STATE = "state";

	// Board states
	public static final int STATE_PAUSED = 0; // no table or not updating
	public static final int STATE_SELECTED = 1; // selected for updates

	// Article table
	public static final String KEYA_SEQ = "seq";
	public static final String KEYA_USER = "user";
	public static final String KEYA_AUTHOR = "author";
	public static final String KEYA_DATE = "date";
	public static final String KEYA_TITLE = "title";
	public static final String KEYA_THREAD = "thread";
	public static final String KEYA_BODY = "body";
	public static final String KEYA_READ = "read";

	// Aggregate columns
	public static final String KEYA_ALLREAD = "allread";
	public static final String KEYA_ALLREAD_FIELD =
		"MIN(" + KEYA_READ + ") AS " + KEYA_ALLREAD;
	public static final String KEYA_READ_ALLREAD_FIELD =
		KEYA_READ + " AS " + KEYA_ALLREAD;
	public static final String KEYA_CNT = "cnt";
	public static final String KEYA_CNT_FIELD = "COUNT(*) AS " + KEYA_CNT;

	// Selections
	public static final String SELECTION_TABNAME = KEYB_TABNAME + "=?";
	public static final String SELECTION_STATE_ACTIVE =
		KEYB_STATE + "!=" + STATE_PAUSED;
	public static final String SELECTION_SEQ = KEYA_SEQ + "=?";
	public static final String SELECTION_UNREAD = KEYA_READ + "=0";
	public static final String SELECTION_ALLUNREAD = KEYA_ALLREAD + "=0";

	// Orderings
	public static final String ORDER_BY_ID = KEY_ID + " ASC";
	public static final String ORDER_BY_SEQ_DESC = KEYA_SEQ + " DESC";
	public static final String ORDER_BY_SEQ_ASC = KEYA_SEQ + " ASC";
	public static final String ORDER_BY_COUNT_DESC = KEYB_COUNT + " DESC";
	public static final String ORDER_BY_STATE_ASC = KEYB_STATE + " ASC";
	public static final String ORDER_BY_STATE_DESC = KEYB_STATE + " DESC";
	public static final String ORDER_BY_TITLE = "LOWER(" + KEYB_TITLE + ")";

	ContentResolver mResolver;

	@Override
	public boolean onCreate() {
		mResolver = getContext().getContentResolver();

		final DBHelper dbHelper = new DBHelper(getContext(), DB_NAME, null,
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
		final String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("query: Unsupported URI: "
					+ _uri);
		}
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		String orderby = ORDER_BY_SEQ_DESC;
		int type = sUriMatcher.match(_uri);
		switch (type) {
		case TYPE_LIST:
		case TYPE_TLIST:
			break;
		case TYPE_BOARDS:
			orderby = ORDER_BY_TITLE;
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
		case TYPE_LIST:
			if (segments.size() == 2) {
				return segments.get(1);
			}
			break;
		case TYPE_TLIST:
			if (segments.size() == 2) {
				return getViewname(segments.get(1));
			}
			break;
		case TYPE_BOARDS:
			return DB_TABLE;
		}
		Log.e(TAG, "getTableName: Unsupported URI (" + type + "): " + _uri);
		return null;
	}

	private static final String getViewname(String _tabname) {
		return _tabname + "_view";
	}

	private static final int getUnreadCount(SQLiteDatabase _db, String _tabname) {
		final String[] FIELDS1 = { KEYA_CNT_FIELD, };
		int count = 0;
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(_tabname);
		final Cursor c = qb.query(_db, FIELDS1, SELECTION_UNREAD, null, null,
				null, null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				count = c.getInt(0);
			}
			c.close();
		}
		return count;
	}

	private static final int setMainUnreadCount(SQLiteDatabase _db,
			String _tabname, int count) {
		final ContentValues values = new ContentValues();
		values.put(KEYB_COUNT, count);
		return _db.update(DB_TABLE, values, SELECTION_TABNAME,
				new String[] { _tabname });
	}

	// Common ID
	private static final String KEY_ID_DEF =
		KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT";

	// Board table
	private static final String KEYB_TABNAME_DEF =
		KEYB_TABNAME + " CHAR(36) NOT NULL UNIQUE";
	private static final String KEYB_TITLE_DEF =
		KEYB_TITLE + " VARCHAR(40) NOT NULL";
	private static final String KEYB_COUNT_DEF =
		KEYB_COUNT + " INTEGER DEFAULT 0";
	private static final String KEYB_STATE_DEF =
		KEYB_STATE + " TINYINT DEFAULT 0";

	// Article table
	private static final String KEYA_SEQ_DEF =
		KEYA_SEQ + " INTEGER NOT NULL UNIQUE";
	private static final String KEYA_USER_DEF =
		KEYA_USER + " CHAR(12) NOT NULL";
	private static final String KEYA_AUTHOR_DEF =
		KEYA_AUTHOR + " VARCHAR(40) NOT NULL";
	private static final String KEYA_DATE_DEF =
		KEYA_DATE + " DATETIME NOT NULL";
	private static final String KEYA_TITLE_DEF =
		KEYA_TITLE + " VARCHAR(40) NOT NULL";
	private static final String KEYA_THREAD_DEF =
		KEYA_THREAD + " CHAR(32) NOT NULL";
	private static final String KEYA_BODY_DEF =
		KEYA_BODY + " TEXT";
	private static final String KEYA_READ_DEF =
		KEYA_READ + " TINYINT DEFAULT 0";

	private static final String DB_NAME = "kidsbbs.db";
	private static final int DB_VERSION = 4;
	private static final String DB_TABLE = "boards";

	private SQLiteDatabase mDB;

	private static class DBHelper extends SQLiteOpenHelper {
		private Resources mResources;

		public DBHelper(Context _context, String _name, CursorFactory _factory,
				int _version) {
			super(_context, _name, _factory, _version);
			mResources = _context.getResources();
		}

		@Override
		public void onCreate(SQLiteDatabase _db) {
			createMainTable(_db);

			// Board table...
			final String[] tabnames =
				mResources.getStringArray(R.array.board_table_names);
			final String[] typeNames =
				mResources.getStringArray(R.array.board_type_names);
			final String[] nameMapKeys =
				mResources.getStringArray(R.array.board_name_map_in);
			final String[] nameMapValues =
				mResources.getStringArray(R.array.board_name_map_out);
			final String[] defaultMapKeys =
				mResources.getStringArray(R.array.default_board_tables);

			final HashMap<String, String> nameMap = new HashMap<String, String>();
			for (int i = 0; i < nameMapKeys.length; ++i) {
				nameMap.put(nameMapKeys[i], nameMapValues[i]);
			}

			final HashMap<String, Boolean> updateMap = new HashMap<String, Boolean>();
			for (int i = 0; i < tabnames.length; ++i) {
				updateMap.put(tabnames[i], false);
			}
			for (int i = 0; i < defaultMapKeys.length; ++i) {
				updateMap.put(defaultMapKeys[i], true);
			}

			// Populate...
			for (int i = 0; i < tabnames.length; ++i) {
				final String[] p = BoardInfo.parseTabname(tabnames[i]);
				final int type = Integer.parseInt(p[0]);
				final String name = p[1];
				String title;
				if (type > 0 && type < typeNames.length) {
					title = typeNames[type] + " ";
				} else {
					title = "";
				}
				final String mapped = nameMap.get(name);
				if (mapped != null) {
					title += mapped;
				} else {
					title += name;
				}
				addBoard(_db, new BoardInfo(tabnames[i], title),
						updateMap.get(tabnames[i])
							? STATE_SELECTED : STATE_PAUSED);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase _db, int _old, int _new) {
			final String[] FIELDS = {
				KEYB_TABNAME,
			};
			Log.w(TAG, "Upgrading database from version " + _old + " to "
					+ _new + ", which may destroy all old data");

			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(DB_TABLE);
			try {
				final Cursor c = qb.query(_db, FIELDS, null, null, null, null,
						null);
				if (c != null) {
					if (c.getCount() > 0) {
						c.moveToFirst();
						do {
							final String tabname = c.getString(0);
							upgradeArticleDB(_db, tabname, _old);
						} while (c.moveToNext());
					}
					c.close();
				}

				upgradeBoardDB(_db, _old);
			} catch (SQLiteException e) {
				// For whatever the reason, the table seems to have disappeared
				createMainTable(_db);
			}
		}

		private void upgradeArticleDB(SQLiteDatabase _db, String _tabname,
				int _old) {
			if (_old < 2) {
				// We need to recreate the tables from scratch.
				dropArticleTable(_db, _tabname);
				createArticleTable(_db, _tabname);
			} else if (_old < 3) {
				// We can upgrade views.
				dropArticleViews(_db, _tabname);
				createArticleViews(_db, _tabname);
			} else {
				// Nothing to do.
			}
		}

		private void upgradeBoardDB(SQLiteDatabase _db, int _old) {
			if (_old < 4) {
				// New column in DB_VERSION 4.
				_db.execSQL("ALTER TABLE " + DB_TABLE + " ADD COLUMN "
						+ KEYB_COUNT_DEF + ";");

				// Fill it out.
				final String[] FIELDS = {
					KEYB_TABNAME,
				};
				final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
				qb.setTables(DB_TABLE);
				final Cursor c = qb.query(_db, FIELDS, null, null, null, null,
						null);
				if (c != null) {
					if (c.getCount() > 0) {
						c.moveToFirst();
						do {
							final String tabname = c.getString(0);
							final int unreadCount = getUnreadCount(_db, tabname);
							if (unreadCount > 0) {
								setMainUnreadCount(_db, tabname, unreadCount);
							}
						} while (c.moveToNext());
					}
					c.close();
				}
			}
		}

		private void addBoard(SQLiteDatabase _db, BoardInfo _info, int _state) {
			final ContentValues values = new ContentValues();
			values.put(KEYB_TABNAME, _info.getTabname());
			values.put(KEYB_TITLE, _info.getTitle());
			values.put(KEYB_STATE, _state);
			values.put(KEYB_COUNT, 0);
			if (_db.insert(DB_TABLE, null, values) < 0) {
				throw new SQLException("addBoard: Failed to insert row into "
						+ DB_TABLE);
			}
			createArticleTable(_db, _info.getTabname());
		}

		private void createMainTable(SQLiteDatabase _db) {
			_db.execSQL("CREATE TABLE IF NOT EXISTS " + DB_TABLE + " ("
					+ KEY_ID_DEF + "," + KEYB_TABNAME_DEF + ","
					+ KEYB_TITLE_DEF + "," + KEYB_STATE_DEF + ","
					+ KEYB_COUNT_DEF + ");");
		}

		// private void dropMainTable(SQLiteDatabase _db) {
		// _db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
		// }

		private void createArticleViews(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("CREATE VIEW IF NOT EXISTS "
					+ getViewname(_tabname) + " AS SELECT " + KEY_ID
					+ "," + KEYA_SEQ + "," + KEYA_USER + ","
					+ KEYA_AUTHOR + "," + KEYA_DATE + "," + KEYA_TITLE
					+ "," + KEYA_THREAD + "," + KEYA_BODY + ","
					+ KEYA_ALLREAD_FIELD + "," + KEYA_CNT_FIELD
					+ " FROM (SELECT * FROM " + _tabname + " ORDER BY "
					+ ORDER_BY_SEQ_ASC + ") AS t GROUP BY "
					+ KEYA_THREAD + ";");
		}

		private void createArticleTable(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("CREATE TABLE IF NOT EXISTS " + _tabname + " ("
					+ KEY_ID_DEF + "," + KEYA_SEQ_DEF + "," + KEYA_USER_DEF
					+ "," + KEYA_AUTHOR_DEF + "," + KEYA_DATE_DEF + ","
					+ KEYA_TITLE_DEF + "," + KEYA_THREAD_DEF + ","
					+ KEYA_BODY_DEF + "," + KEYA_READ_DEF + ");");
			_db.execSQL("CREATE INDEX IF NOT EXISTS " + _tabname + "_I"
					+ KEYA_SEQ + " ON " + _tabname + " (" + KEYA_SEQ + ")");
			createArticleViews(_db, _tabname);
		}

		private void dropArticleViews(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("DROP VIEW IF EXISTS " + getViewname(_tabname));
		}

		private void dropArticleTable(SQLiteDatabase _db, String _tabname) {
			_db.execSQL("DROP TABLE IF EXISTS " + _tabname);
			_db.execSQL("DROP INDEX IF EXISTS " + _tabname + "_I" + KEYA_SEQ);
			dropArticleViews(_db, _tabname);
		}
	}
}
