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

import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleField;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardColumn;
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

public class DBUtils {

	private interface Projection {
		String[] BOARD_TOTAL_COUNT = { "TOTAL(" + BoardColumn.COUNT + ")" };
		String[] SEQ = { ArticleColumn.SEQ };
		String[] ARTICLE_TITLE = { ArticleColumn.TITLE };
		String[] ARTICLE_CNT = { ArticleField.CNT };
		String[] READ = { ArticleColumn.READ };
		String[] ALLREAD = { ArticleField.ALLREAD };
	}

	public static final void updateBoardTable(Context _context, String _tabname) {
		Intent intent = new Intent(_context, UpdateService.class);
		if (_tabname != null && _tabname.length() > 0) {
			intent.putExtra(PackageBase.PARAM + ParamName.TABNAME, _tabname);
		}
		_context.startService(intent);
	}

	public static final int getBoardLastSeq(ContentResolver _cr, String _tabname) {
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

	public static final String getBoardTitle(ContentResolver _cr,
			String _tabname) {
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

	public static final int getBoardTableSize(ContentResolver _cr,
			String _tabname) {
		int cnt = 0;
		final Uri uri = Uri.parse(ContentUriString.LIST + _tabname);
		final Cursor c = _cr.query(uri, Projection.ARTICLE_CNT, null, null,
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				cnt = c.getInt(0);
			}
			c.close();
		}
		return cnt;
	}

	public static final int getBoardUnreadCount(ContentResolver _cr,
			String _tabname) {
		return getTableCount(_cr, ContentUriString.LIST, _tabname,
				Selection.UNREAD);
	}

	public static final int getTableCount(ContentResolver _cr, String _uriBase,
			String _tabname, String _where) {
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

	public static final int getTotalUnreadCount(ContentResolver _cr) {
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

	public static final void updateBoardCount(ContentResolver _cr,
			String _tabname) {
		final ContentValues values = new ContentValues();
		values.put(BoardColumn.COUNT, getBoardUnreadCount(_cr, _tabname));
		_cr.update(ContentUri.BOARDS, values, Selection.TABNAME,
				new String[] { _tabname });
	}

	public static final boolean updateArticleRead(ContentResolver _cr,
			String _tabname, int _seq, boolean _read) {
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

	public static final boolean getArticleRead(ContentResolver _cr, Uri _uri,
			String _where, String[] _whereArgs) {
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
}
