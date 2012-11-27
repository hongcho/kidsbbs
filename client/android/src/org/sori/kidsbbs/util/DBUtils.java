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
package org.sori.kidsbbs.util;

import java.util.ArrayList;

import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleField;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardState;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUri;
import org.sori.kidsbbs.provider.ArticleProvider.ContentUriString;
import org.sori.kidsbbs.provider.ArticleProvider.OrderBy;
import org.sori.kidsbbs.provider.ArticleProvider.Selection;
import org.sori.kidsbbs.service.UpdateService;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

public class DBUtils {

	private interface Projection {
		String[] BOARD_TOTAL_COUNT = { "TOTAL(" + BoardColumn.COUNT + ")" };
		String[] SEQ = { ArticleColumn.SEQ };
		String[] ARTICLE_TITLE = { ArticleColumn.TITLE };
		String[] ARTICLE_CNT = { ArticleField.CNT };
		String[] READ = { ArticleColumn.READ };
		String[] ALLREAD = { ArticleField.ALLREAD };
		String[] BOARD_NAME = { BoardColumn.TABNAME };
		String[] BOARD_STATE = { BoardColumn.STATE };
	}

	public static final void updateBoardTable(final Context _context,
			final String _tabname) {
		Intent intent = new Intent(_context, UpdateService.class);
		if (!TextUtils.isEmpty(_tabname)) {
			intent.putExtra(PackageBase.PARAM + ParamName.TABNAME, _tabname);
		} else {
			intent.putExtra(PackageBase.PARAM + ParamName.TABNAME, "");
		}
		_context.startService(intent);
	}

	public static final int getBoardLastSeq(final ContentResolver _cr,
			final String _tabname) {
		int seq = 0;
		final Uri uri = Uri.parse(ContentUriString.LIST + _tabname);
		final Cursor c = _cr.query(uri, Projection.SEQ, null, null,
				OrderBy.SEQ_DESC);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				seq = c.getInt(0);
			}
			c.close();
		}
		return seq;
	}

	public static final String getBoardTitle(final ContentResolver _cr,
			final String _tabname) {
		String title = null;
		final Cursor c = _cr.query(ContentUri.BOARDS, Projection.ARTICLE_TITLE,
				Selection.TABNAME, new String[] { _tabname }, null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				title = c.getString(0);
			}
			c.close();
		}
		return title;
	}

	public static final int getBoardTableSize(final ContentResolver _cr,
			final String _tabname) {
		int count = 0;
		final Uri uri = Uri.parse(ContentUriString.LIST + _tabname);
		final Cursor c = _cr.query(uri, Projection.ARTICLE_CNT, null, null,
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				count = c.getInt(0);
			}
			c.close();
		}
		return count;
	}

	public static final int getBoardUnreadCount(final ContentResolver _cr,
			final String _tabname) {
		return getTableCount(_cr, ContentUriString.LIST, _tabname,
				Selection.UNREAD);
	}

	public static final int getTableCount(final ContentResolver _cr,
			final String _uriBase, final String _tabname, final String _where) {
		int count = 0;
		final Uri uri = Uri.parse(_uriBase + _tabname);
		final Cursor c = _cr.query(uri, Projection.ARTICLE_CNT, _where, null,
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				count = c.getInt(0);
			}
			c.close();
		}
		return count;
	}

	public static final int getTotalUnreadCount(final ContentResolver _cr) {
		int count = 0;
		final Cursor c = _cr.query(ContentUri.BOARDS,
				Projection.BOARD_TOTAL_COUNT, Selection.STATE_ACTIVE, null,
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				count = c.getInt(0);
			}
			c.close();
		}
		return count;
	}

	public static final void updateBoardCount(final ContentResolver _cr,
			final String _tabname) {
		final ContentValues values = new ContentValues();
		values.put(BoardColumn.COUNT, getBoardUnreadCount(_cr, _tabname));
		_cr.update(ContentUri.BOARDS, values, Selection.TABNAME,
				new String[] { _tabname });
	}

	public static final boolean updateArticleRead(final ContentResolver _cr,
			final String _tabname, final int _seq, final boolean _read) {
		final Uri uri = Uri.parse(ContentUriString.LIST + _tabname);
		final String[] args = new String[] { Integer.toString(_seq) };

		boolean readOld = false;
		final Cursor c = _cr.query(uri, Projection.READ, Selection.SEQ, args,
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				readOld = c.getInt(0) != 0;
			}
			c.close();
		}
		if (readOld == _read) {
			return false;
		}

		final ContentValues values = new ContentValues();
		values.put(ArticleColumn.READ, _read ? 1 : 0);
		final int count = _cr.update(uri, values, Selection.SEQ, args);
		return (count > 0);
	}

	public static final boolean getArticleRead(final ContentResolver _cr,
			final Uri _uri, final String _where, final String[] _whereArgs) {
		boolean read = false;
		final Cursor c = _cr.query(_uri, Projection.ALLREAD, _where,
				_whereArgs, null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				read = c.getInt(0) != 0;
			}
			c.close();
		}
		return read;
	}
	
	public static final ArrayList<String> getActiveBoards(
			final ContentResolver _cr) {
		final String ORDERBY = OrderBy.STATE_ASC + "," + OrderBy._ID;
		ArrayList<String> tabnames = new ArrayList<String>();
		
		// Get all the boards...
		final Cursor c = _cr.query(ContentUri.BOARDS, Projection.BOARD_NAME,
				Selection.STATE_ACTIVE, null, ORDERBY);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				do {
					tabnames.add(c.getString(c.getColumnIndex(
							BoardColumn.TABNAME)));
				} while (c.moveToNext());
			}
			c.close();
		}
		return tabnames;
	}
	
	public static final int getBoardState(final ContentResolver _cr,
			final String _tabname) {
		int result = BoardState.PAUSED;
		final Cursor c =
				_cr.query(
						ContentUri.BOARDS,
						Projection.BOARD_STATE,
						Selection.TABNAME,
						new String[] { _tabname },
						null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				result = c.getInt(c.getColumnIndex(BoardColumn.STATE));
			}
			c.close();
		}
		return result;
	}

	public static final boolean setBoardState(final ContentResolver _cr,
			final String _tabname, final int _state) {
		final ContentValues values = new ContentValues();
		values.put(BoardColumn.STATE, _state);
		return _cr.update(
						ContentUri.BOARDS,
						values,
						Selection.TABNAME,
						new String[] { _tabname }) > 0;
	}
}
