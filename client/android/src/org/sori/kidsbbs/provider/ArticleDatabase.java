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

import org.sori.kidsbbs.R;
import org.sori.kidsbbs.data.BoardInfo;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.util.Log;

public class ArticleDatabase extends SQLiteOpenHelper {
	private static final String TAG = "ArticleDatabase";

	private static final String DATABASE_NAME = "kidsbbs.db";

	private interface Version {
		int FIRST = 1;
		int BODY_AUTHOR_ADDED = 2;
		int ARTICLE_VIEW_ADDED = 3;
		int BOARD_COUNT_ADDED = 4;
	}
	private static final int DATABASE_VERSION = Version.BOARD_COUNT_ADDED;

	public interface Table {
		String BOARDS = "boards";
	}

	public interface BoardColumn {
		String TABNAME = "tabname";
		String TITLE = "title";
		String COUNT = "count";
		String STATE = "state";
	}

	public interface BoardState {
		int PAUSED = 0;	// no table or not updating
		int SELECTED = 1;	// selected for updates
	}

	public interface ArticleColumn {
		String SEQ = "seq";
		String USER = "user";
		String AUTHOR = "author";
		String DATE = "date";
		String TITLE = "title";
		String THREAD = "thread";
		String BODY = "body";
		String READ = "read";

		// Aggregate columns.
		String ALLREAD = "allread";
		String CNT = "cnt";
	}

	public interface ArticleField {
		String ALLREAD =
			"MIN(" + ArticleColumn.READ + ") AS " + ArticleColumn.ALLREAD;
		String READ_ALLREAD = ArticleColumn.READ +  " AS " + ArticleColumn.ALLREAD;
		String CNT = "COUNT(*) AS " + ArticleColumn.CNT;
	}

	private interface BaseColumnDef {
		String _ID = BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT";
	}
	private interface BoardColumnDef {
		String TABNAME = BoardColumn.TABNAME + " CHAR(36) NOT NULL UNIQUE";
		String TITLE = BoardColumn.TITLE + " VARCHAR(40) NOT NULL";
		String COUNT = BoardColumn.COUNT + " INTEGER DEFAULT 0";
		String STATE = BoardColumn.STATE + " TINYINT DEFAULT 0";
	}
	private interface ArticleColumnDef {
		String SEQ = ArticleColumn.SEQ + " INTEGER NOT NULL UNIQUE";
		String USER = ArticleColumn.USER + " CHAR(12) NOT NULL";
		String AUTHOR = ArticleColumn.AUTHOR + " VARCHAR(40) NOT NULL";
		String DATE = ArticleColumn.DATE + " DATETIME NOT NULL";
		String TITLE = ArticleColumn.TITLE + " VARCHAR(40) NOT NULL";
		String THREAD = ArticleColumn.THREAD + " CHAR(32) NOT NULL";
		String BODY = ArticleColumn.BODY + " TEXT";
		String READ = ArticleColumn.READ + " TINYINT DEFAULT 0";
	}

	private Resources mResources;

	public ArticleDatabase(Context _context) {
		super(_context, DATABASE_NAME, null, DATABASE_VERSION);
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
						? BoardState.SELECTED : BoardState.PAUSED);
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase _db, int _old, int _new) {
		final String[] PROJECTION = {
			BoardColumn.TABNAME,
		};
		Log.d(TAG, "Upgrading database from version " + _old + " to " + _new);

		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(Table.BOARDS);
		try {
			final Cursor c = qb.query(_db, PROJECTION, null, null, null, null,
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

	public static final String getViewName(final String _tabname) {
		return _tabname + "_view";
	}

	private static final String getIndexName(final String _tabname,
			final String _index) {
		return _tabname + "_I" + _index;
	}

	private void upgradeArticleDB(final SQLiteDatabase _db, final String _tabname,
			final int _old) {
		int version = _old;

		// Cascading upgrade...
		// Only use "break" to drop and re-create the table.
		switch (version) {
		case Version.FIRST:
			// "body" and "author" were added to the article tables.
			// - Give up.
			break;

		case Version.BODY_AUTHOR_ADDED:
			// Article view was added.
			// - Re-create article view.
			dropArticleViews(_db, _tabname);
			createArticleViews(_db, _tabname);
			version = Version.ARTICLE_VIEW_ADDED;

		case Version.ARTICLE_VIEW_ADDED:
			// "count" was added to the board table.
			// - Nothing to do.
			version = Version.BOARD_COUNT_ADDED;
		}

		// If upgrade fails, re-create the table.
		if (version != DATABASE_VERSION) {
			Log.w(TAG, "Destroying old \"" + _tabname + "\" table during upgrade");
			dropArticleTable(_db, _tabname);
			createArticleTable(_db, _tabname);
		}
	}

	private void upgradeBoardDB(final SQLiteDatabase _db, final int _old) {
		int version = _old;

		// Cascading upgrade...
		// Only use "break" to drop and re-create the table.
		switch (version) {
		case Version.FIRST:
			// "body" and "author" were added to the article tables.
			// - Nothing to do.
			version = Version.BODY_AUTHOR_ADDED;

		case Version.BODY_AUTHOR_ADDED:
			// Article view was added.
			// - Nothing to do.
			version = Version.ARTICLE_VIEW_ADDED;

		case Version.ARTICLE_VIEW_ADDED:
			// "count" was added to the board table.
			// - Add the column and fill it out.
			_db.execSQL("ALTER TABLE " + Table.BOARDS
					+ " ADD COLUMN " + BoardColumnDef.COUNT + ";");

			final String[] PROJECTION = {
				BoardColumn.TABNAME,
			};
			final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(Table.BOARDS);
			final Cursor c = qb.query(_db, PROJECTION, null, null, null, null,
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
			version = Version.BOARD_COUNT_ADDED;
		}

		// If upgrade fails, re-create the table.
		if (version != DATABASE_VERSION) {
			Log.w(TAG, "Destroying old \"" + Table.BOARDS + "\" table during upgrade");
			dropMainTable(_db);
			createMainTable(_db);
		}
	}

	private void addBoard(final SQLiteDatabase _db, final BoardInfo _info,
			final int _state) {
		final ContentValues values = new ContentValues();
		values.put(BoardColumn.TABNAME, _info.getTabname());
		values.put(BoardColumn.TITLE, _info.getTitle());
		values.put(BoardColumn.STATE, _state);
		values.put(BoardColumn.COUNT, 0);
		if (_db.insert(Table.BOARDS, null, values) < 0) {
			throw new SQLException("addBoard: Failed to insert row into "
					+ Table.BOARDS);
		}
		createArticleTable(_db, _info.getTabname());
	}

	private static final int getUnreadCount(final SQLiteDatabase _db,
			final String _tabname) {
		final String[] PROJECTION = {
				ArticleField.CNT,
		};
		int count = 0;
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(_tabname);
		final Cursor c = qb.query(_db, PROJECTION,
				ArticleProvider.Selection.UNREAD, null, null, null, null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				count = c.getInt(0);
			}
			c.close();
		}
		return count;
	}

	private static final int setMainUnreadCount(final SQLiteDatabase _db,
			final String _tabname, final int count) {
		final ContentValues values = new ContentValues();
		values.put(BoardColumn.COUNT, count);
		return _db.update(Table.BOARDS, values,
				ArticleProvider.Selection.TABNAME, new String[] { _tabname });
	}

	private void createMainTable(final SQLiteDatabase _db) {
		_db.execSQL("CREATE TABLE IF NOT EXISTS " + Table.BOARDS + " ("
				+ BaseColumnDef._ID + ","
				+ BoardColumnDef.TABNAME + ","
				+ BoardColumnDef.TITLE + ","
				+ BoardColumnDef.STATE + ","
				+ BoardColumnDef.COUNT + ");");
	}

	private void dropMainTable(final SQLiteDatabase _db) {
		_db.execSQL("DROP TABLE IF EXISTS " + Table.BOARDS);
	}

	private void createArticleViews(final SQLiteDatabase _db,
			final String _tabname) {
		_db.execSQL("CREATE VIEW IF NOT EXISTS "
				+ getViewName(_tabname) + " AS SELECT "
				+ BaseColumns._ID + ","
				+ ArticleColumn.SEQ + ","
				+ ArticleColumn.USER + ","
				+ ArticleColumn.AUTHOR + ","
				+ ArticleColumn.DATE + ","
				+ ArticleColumn.TITLE + ","
				+ ArticleColumn.THREAD + ","
				+ ArticleColumn.BODY + ","
				+ ArticleField.ALLREAD + ","
				+ ArticleField.CNT
				+ " FROM (SELECT * FROM " + _tabname
				+ " ORDER BY " + ArticleProvider.OrderBy.SEQ_ASC
				+ ") AS t GROUP BY " + ArticleColumn.THREAD + ";");
	}

	private void createArticleTable(final SQLiteDatabase _db,
			final String _tabname) {
		_db.execSQL("CREATE TABLE IF NOT EXISTS "
				+ _tabname + " ("
				+ BaseColumnDef._ID + ","
				+ ArticleColumnDef.SEQ + ","
				+ ArticleColumnDef.USER + ","
				+ ArticleColumnDef.AUTHOR + ","
				+ ArticleColumnDef.DATE + ","
				+ ArticleColumnDef.TITLE + ","
				+ ArticleColumnDef.THREAD + ","
				+ ArticleColumnDef.BODY + ","
				+ ArticleColumnDef.READ + ");");
		_db.execSQL("CREATE INDEX IF NOT EXISTS "
				+ getIndexName(_tabname, ArticleColumn.SEQ)
				+ " ON " + _tabname
				+ " (" + ArticleColumn.SEQ + ")");
		createArticleViews(_db, _tabname);
	}

	private void dropArticleViews(final SQLiteDatabase _db,
			final String _tabname) {
		_db.execSQL("DROP VIEW IF EXISTS " + getViewName(_tabname));
	}

	private void dropArticleTable(final SQLiteDatabase _db,
			final String _tabname) {
		_db.execSQL("DROP TABLE IF EXISTS " + _tabname);
		_db.execSQL("DROP INDEX IF EXISTS " + getIndexName(_tabname,
				ArticleColumn.SEQ));
		dropArticleViews(_db, _tabname);
	}
}
