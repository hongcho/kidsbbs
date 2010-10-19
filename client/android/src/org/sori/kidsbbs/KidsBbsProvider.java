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
	private static final String PROVIDER_NAME = "org.sori.provider.kidsbbs";

	public static final Uri CONTENT_URI_BOARDS = Uri.parse(
		"content://" + PROVIDER_NAME + "/boards");
	public static final String CONTENT_URISTR_LIST =
		"content://" + PROVIDER_NAME + "/list/";
	public static final String CONTENT_URISTR_TLIST =
		"content://" + PROVIDER_NAME + "/tlist/";
	public static final String CONTENT_URISTR_THREAD =
		"content://" + PROVIDER_NAME + "/thread/";
	public static final String CONTENT_URISTR_USER =
		"content://" + PROVIDER_NAME + "/user/";
	
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

	private static final UriMatcher sUriMatcher = new UriMatcher(
			UriMatcher.NO_MATCH);
	static {
		sUriMatcher.addURI(PROVIDER_NAME, "/boards", TYPE_BOARDS);
		sUriMatcher.addURI(PROVIDER_NAME, "/boards/#", TYPE_BOARDS_ID);
		sUriMatcher.addURI(PROVIDER_NAME, "/list/*", TYPE_LIST);
		sUriMatcher.addURI(PROVIDER_NAME, "/list/*/#", TYPE_LIST_ID);
		sUriMatcher.addURI(PROVIDER_NAME, "/tlist/*", TYPE_TLIST);
		sUriMatcher.addURI(PROVIDER_NAME, "/tlist/*/#", TYPE_TLIST_ID);
		sUriMatcher.addURI(PROVIDER_NAME, "/thread/*", TYPE_THREAD);
		sUriMatcher.addURI(PROVIDER_NAME, "/thread/*/#", TYPE_THREAD_ID);
		sUriMatcher.addURI(PROVIDER_NAME, "/user/*", TYPE_USER);
		sUriMatcher.addURI(PROVIDER_NAME, "/user/*/#", TYPE_USER_ID);
	}

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
			return "vnd.sori.cursor.dir/vnd.sori.boards";
		case TYPE_BOARDS_ID:
			return "vnd.sori.cursor.item/vnd.sori.boards";
		case TYPE_LIST:
			return "vnd.sori.cursor.dir/vnd.sori.list";
		case TYPE_LIST_ID:
			return "vnd.sori.cursor.item/vnd.sori.list";
		case TYPE_TLIST:
			return "vnd.sori.cursor.dir/vnd.sori.tlist";
		case TYPE_TLIST_ID:
			return "vnd.sori.cursor.item/vnd.sori.tlist";
		case TYPE_THREAD:
			return "vnd.sori.cursor.dir/vnd.sori.thread";
		case TYPE_THREAD_ID:
			return "vnd.sori.cursor.item/vnd.sori.thread";
		case TYPE_USER:
			return "vnd.sori.cursor.dir/vnd.sori.user";
		case TYPE_USER_ID:
			return "vnd.sori.cursor.item/vnd.sori.user";
		}
		Log.w(PROVIDER_NAME, "Unsupported URI: " + _uri);
		return null;
	}

	@Override
	public Cursor query(Uri _uri, String[] _projection, String _selection,
			String[] _selectionArgs, String _sortOrder) {
		String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
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
			order = KEYB_TITLE;
		default:
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
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
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
		}
		long row = mDB.insert(table, null, _values);
		if (row > 0) {
			Uri uri = ContentUris.withAppendedId(getContentUriWithoutId(_uri),
					row);
			getContext().getContentResolver().notifyChange(uri, null);
			return uri;
		}
		throw new SQLException("Failed to insert row into " + _uri);
	}

	@Override
	public int delete(Uri _uri, String _selection, String[] _selectionArgs) {
		String table = getTableName(_uri);
		if (table == null) {
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
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
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
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
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
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
			throw new IllegalArgumentException("Unsupported URI: " + _uri);
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
		switch (sUriMatcher.match(_uri)) {
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
			Log.w(PROVIDER_NAME, "Unsupported URI: " + _uri);
			return null;
		}
	}

	private Uri getContentUriWithoutId(Uri _uri) {
		List<String> segments = _uri.getPathSegments();
		int length = segments.size();
		String uriString = "content://" + PROVIDER_NAME;
		switch (sUriMatcher.match(_uri)) {
		case TYPE_LIST_ID:
		case TYPE_TLIST_ID:
		case TYPE_THREAD_ID:
		case TYPE_USER_ID:
			if (length != 3) {
				Log.w(PROVIDER_NAME, "Unsupported URI: " + _uri);
				return null;
			}
			uriString += "/" + segments.get(0) + "/" + segments.get(1);
			break;
		case TYPE_LIST:
		case TYPE_TLIST:
		case TYPE_THREAD:
		case TYPE_USER:
			if (length != 2) {
				Log.w(PROVIDER_NAME, "Unsupported URI: " + _uri);
				return null;
			}
			uriString += "/" + segments.get(0) + "/" + segments.get(1);
			break;
		case TYPE_BOARDS_ID:
		case TYPE_BOARDS:
			uriString += "/" + segments.get(0);
			break;
		default:
			Log.w(PROVIDER_NAME, "Unsupported URI: " + _uri);
			return null;
		}
		return Uri.parse(uriString);
	}

	public void createArticleTable(String _tabname) {
		mDB.execSQL("CREATE TABLE " + _tabname + " (" +
				KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
				KEYA_SEQ + " INTEGER NOT NULL," +
				KEYA_AUTHOR + " VARCHAR(40) NOT NULL," +
				KEYA_DATE + " DATETIME NOT NULL," +
				KEYA_TITLE + " VARCHAR(40)" +
				KEYA_THREAD + " CHAR(32) NOT NULL," +
				KEYA_BODY + " TEXT," +
				KEYA_READ + " BOOLEAN DEFAULT FALSE);");
	}

	public void dropArticleTable(String _tabname) {
		mDB.execSQL("DROP TABLE IF EXISTS " + _tabname);
	}

	// Common ID...
	public static final String KEY_ID = "_id"; // INTEGER PRIMARY KEY AUTOINCREMENT

	// Board table
	public static final String KEYB_NAME = "name"; // CHAR(32) NOT NULL
	public static final String KEYB_TYPE = "type"; // INTEGER NOT NULL
	public static final String KEYB_TITLE = "title"; // VARCHAR(40) NOT NULL
	public static final String KEYB_LASTTIME = "lasttime"; // DATETIME
	public static final String KEYB_NEWCOUNT = "newcount"; // INTEGER DEFAULT 0

	// Article table
	public static final String KEYA_SEQ = "seq"; // INTEGER NOT NULL
	public static final String KEYA_AUTHOR = "author"; // VARCHAR(40) NOT NULL
	public static final String KEYA_DATE = "date"; // DATETIME NOT NULL
	public static final String KEYA_TITLE = "title"; // VARCHAR(40) NOT NULL
	public static final String KEYA_THREAD = "thread"; // CHAR(32) NOT NULL
	public static final String KEYA_BODY = "body"; // TEXT
	public static final String KEYA_READ = "read"; // BOOLEAN DEFAULT FALSE

	private static final String DB_NAME = "kidsbbs.db";
	private static final int DB_VERSION = 1;
	private static final String DB_TABLE = "boards";

	private SQLiteDatabase mDB;

	private static class DBHelper extends SQLiteOpenHelper {
		public DBHelper(Context _context, String _name, CursorFactory _factory,
				int _version) {
			super(_context, _name, _factory, _version);
		}

		@Override
		public void onCreate(SQLiteDatabase _db) {
			//_db.execSQL("CREATE TABLE " + DB_TABLE + " (" +
			//		KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
			//		KEYB_NAME + " CHAR(32) NOT NULL," +
			//		KEYB_TYPE + " INTEGER NOT NULL," +
			//		KEYB_TITLE + " VARCHAR(40) NOT NULL," +
			//		KEYB_LASTTIME + " DATETIME," +
			//		KEYB_NEWCOUNT + " INTEGER DEFAULT 0);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase _db, int _old, int _new) {
			// TODO: properly upgrade...
			//Log.w(PROVIDER_NAME, "Upgrading database from version " + _old +
			//		" to " + _new + ", which will destroy all old data");
			//_db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
			//onCreate(_db);
		}
	}
}
