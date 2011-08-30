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
package org.sori.kidsbbs;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.sori.kidsbbs.data.ArticleInfo;
import org.sori.kidsbbs.data.BoardInfo;
import org.sori.kidsbbs.provider.ArticleDatabase;
import org.sori.kidsbbs.provider.ArticleProvider;
import org.sori.kidsbbs.service.UpdateService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class KidsBbs {
	private static final String TAG = "KidsBbs";

	public static final String PKG_BASE = "org.sori.kidsbbs.";
	public static final String BCAST_BASE = PKG_BASE + "broadcast.";
	public static final String PARAM_BASE = PKG_BASE + "param.";
	public static final String ALARM_BASE = PKG_BASE + "alarm.";

	public static final String BOARD_UPDATED = BCAST_BASE + "BoardUpdated";
	public static final String ARTICLE_UPDATED = BCAST_BASE + "ArticleUpdated";
	public static final String UPDATE_ERROR = BCAST_BASE + "UpdateError";

	public interface UrlString {
		String BASE = "http://sori.org/kids/kids.php?_o=1&";
		String BLIST = BASE;
		String PLIST = BASE + "m=plist&";
		String LIST = BASE + "m=list&";
		String TLIST = BASE + "m=tlist&";
		String THREAD = BASE + "m=thread&";
		String USER = BASE + "m=user&";
	}

	public interface IntentUri {
		String BASE_STRING = "content:/kidsbbs/";
		Uri TLIST = Uri.parse(BASE_STRING + "tlist");
		Uri THREAD = Uri.parse(BASE_STRING + "thread");
		Uri USER = Uri.parse(BASE_STRING + "user");
		Uri TVIEW = Uri.parse(BASE_STRING + "tview");
	}

	public interface ParamName {
		String BTITLE = "bt";
		String BOARD = "b";
		String TYPE = "t";
		String THREAD = "id";
		String USER = "u";
		String SEQ = "p";
		String START = "s";
		String COUNT = "n";
		String TABNAME = "tn";
		String TTITLE = "tt";
		String VTITLE = "vt";
	}

	private static final String DATE_INVALID = "0000-00-00 00:00:00";
	private static final String DATESHORT_INVALID = "0000-00-00";
	// private static final String DATE_FORMAT = "MMM dd, yyyy h:mm:ss aa";
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

	private static final DateFormat DF_TIME =
		DateFormat.getTimeInstance(DateFormat.SHORT);
	private static final DateFormat DF_DATE =
		DateFormat.getDateInstance(DateFormat.SHORT);
	private static final DateFormat DF_FULL =
		DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
	private static final DateFormat DF_MEDIUM =
		DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
	private static final DateFormat DF_KIDS;
	static {
		DF_KIDS = new SimpleDateFormat(DATE_FORMAT);
		DF_KIDS.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public interface NotificationType {
		int NEW_ARTICLE = 0;
	}

	private static final int CONN_TIMEOUT = 30 * 1000; // 30 seconds
	private static final int MAX_DAYS = 7;
	public static final int MIN_ARTICLES = 10;
	public static final int MAX_ARTICLES = 200;
	public static final int MAX_FIRST_ARTICLES = 100;
	public static final String KST_DIFF = "'-9 hours'";
	public static final String MAX_TIME = "'-" + MAX_DAYS + " days'";

	private interface Projection {
		String[] BOARD_TOTAL_COUNT = {
				"TOTAL(" + ArticleDatabase.BoardColumn.COUNT + ")",
		};
		String[] SEQ = {
				ArticleDatabase.ArticleColumn.SEQ,
		};
		String[] ARTICLE_TITLE = {
				ArticleDatabase.ArticleColumn.TITLE,
		};
		String[] ARTICLE_CNT = {
				ArticleDatabase.ArticleField.CNT,
		};
		String[] READ = {
				ArticleDatabase.ArticleColumn.READ,
		};
		String[] ALLREAD = {
				ArticleDatabase.ArticleField.ALLREAD,
		};
	}

	public static final Date KidsToLocalDate(String _dateString) {
		try {
			return DF_FULL.parse(KidsToLocalDateString(_dateString));
		} catch (Exception e) {
			return null;
		}
	}

	public static final String GetShortDateString(String _dateString) {
		try {
			final Date local = DF_FULL.parse(_dateString);
			final Date now = new Date();
			if (now.getYear() == local.getYear()
					&& now.getMonth() == local.getMonth()
					&& now.getDate() == local.getDate()) {
				return DF_TIME.format(local);
			} else {
				return DF_DATE.format(local);
			}
		} catch (Exception e) {
			return DATESHORT_INVALID;
		}
	}
	
	public static final String GetLongDateString(String _dateString) {
		try {
			final Date local = DF_FULL.parse(_dateString);
			return DF_MEDIUM.format(local);
		} catch (Exception e) {
			return DATE_INVALID;
		}
	}

	public static final String KidsToLocalDateString(String _dateString) {
		try {
			Date date = DF_KIDS.parse(_dateString);
			return DF_FULL.format(date);
		} catch (Exception e) {
			return DATE_INVALID;
		}
	}

	public static final String LocalToKidsDateString(String _dateString) {
		try {
			final Date date = DF_FULL.parse(_dateString);
			return DF_KIDS.format(date);
		} catch (Exception e) {
			return DATE_INVALID;
		}
	}

	public static final String trimText(String _s) {
		if (_s != null && _s.length() > 0) {
			final Pattern PATTERNS[] = {
					Pattern.compile("^\\s+", Pattern.CASE_INSENSITIVE),
					Pattern.compile("\\s+$", Pattern.CASE_INSENSITIVE),
			};
			for (int i = 0; i < PATTERNS.length; ++i) {
				Matcher m = PATTERNS[i].matcher(_s);
				_s = m.replaceAll("");
			}
		}
		return _s;
	}

	public static final String getThreadTitle(String _s) {
		if (_s != null && _s.length() > 0) {
			final Pattern PATTERN =
				Pattern.compile("^Re:\\s*", Pattern.CASE_INSENSITIVE);
			final Matcher m = PATTERN.matcher(_s);
			if (m.find()) {
				return m.replaceFirst("");
			}
		}
		return _s;
	}

	public static final String generateSummary(String _s) {
		if (_s == null) {
			return null;
		}
		// Remove quoted texts
		final Pattern[] P_QUOTES = {
				Pattern.compile(
						"^\\s*\\d{4}\\S+ \\d{2}\\S+ \\d{2}\\S+ \\(\\S+\\)"
						+ " \\S+ \\d{2}\\S+ \\d{2}\\S+ \\d{2}\\S+"
						+ " .+:\\s*$",
						Pattern.MULTILINE),
				Pattern.compile("^\\s*>.*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*-{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*={3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*_{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*/{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*#{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\*{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\+{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\.{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\\\{3,}\\s*$", Pattern.MULTILINE),
		};
		for (int i = 0; i < P_QUOTES.length; ++i) {
			Matcher m = P_QUOTES[i].matcher(_s);
			_s = m.replaceAll("");
		}
		// Remove white spaces.
		final Pattern[] P_SPACES = {
				Pattern.compile("\n+"),
				Pattern.compile("\\s+"),
				Pattern.compile("^\\s+"),
		};
		final String[] R_SPACES = { " ", " ", "", };
		for (int i = 0; i < P_SPACES.length; ++i) {
			Matcher m = P_SPACES[i].matcher(_s);
			_s = m.replaceAll(R_SPACES[i]);
		}
		return _s;
	}

	public static final void updateBoardTable(Context _context, String _tabname) {
		Intent intent = new Intent(_context, UpdateService.class);
		if (_tabname != null && _tabname.length() > 0) {
			intent.putExtra(PARAM_BASE + ParamName.TABNAME, _tabname);
		}
		_context.startService(intent);
	}

	public static final ArticleInfo parseArticle(String _tabname, Element _item) {
		NodeList nl;
		Node n;
		try {
			nl = _item.getElementsByTagName("THREAD");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: THREAD");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: THREAD");
			}
			final String thread = n != null ? n.getNodeValue() : null;

			nl = _item.getElementsByTagName("TITLE");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: TITLE");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			final String title = n != null ? n.getNodeValue() : "";

			nl = _item.getElementsByTagName("SEQ");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: SEQ");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: SEQ");
			}
			final int seq = Integer.parseInt(n.getNodeValue());

			nl = _item.getElementsByTagName("DATE");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: DATE");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: DATE");
			}
			final String date = n.getNodeValue();

			nl = _item.getElementsByTagName("USER");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: USER");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: USER");
			}
			final String user = n.getNodeValue();

			nl = _item.getElementsByTagName("AUTHOR");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: AUTHOR");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: AUTHOR");
			}
			final String author = n != null ? n.getNodeValue() : null;

			String desc = "";
			nl = _item.getElementsByTagName("DESCRIPTION");
			if (nl != null && nl.getLength() > 0) {
				n = ((Element) nl.item(0)).getFirstChild();
				desc = n != null ? n.getNodeValue() : "";
			}

			return new ArticleInfo(_tabname, seq, user, author, date, title,
					thread, desc, 1, false);
		} catch (Exception e) {
			Log.w(TAG, e);
			return null;
		}
	}

	public static final ArrayList<ArticleInfo> getArticles(String _board,
			int _type, int _start) throws Exception {
		final ArrayList<ArticleInfo> articles = new ArrayList<ArticleInfo>();
		final String tabname = BoardInfo.buildTabname(_board, _type);
		final String urlString = UrlString.PLIST
			+ ParamName.BOARD + "=" + _board
			+ "&" + ParamName.TYPE + "=" + _type
			+ "&" + ParamName.SEQ + "=" + _start;
		final HttpClient client = new DefaultHttpClient();
		client.getParams().setParameter(
				HttpConnectionParams.CONNECTION_TIMEOUT, CONN_TIMEOUT);
		final HttpGet get = new HttpGet(urlString);
		final HttpResponse response = client.execute(get);
		final HttpEntity entity = response.getEntity();
		if (entity == null) {
			// ???
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
			final InputStream is = entity.getContent();
			final DocumentBuilder db =
				DocumentBuilderFactory.newInstance().newDocumentBuilder();

			// Parse the article list.
			final Document dom = db.parse(is);
			final Element docEle = dom.getDocumentElement();
			NodeList nl;

			nl = docEle.getElementsByTagName("ITEMS");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: ITEMS");
			}
			final Element items = (Element) nl.item(0);

			// Get a board item
			nl = items.getElementsByTagName("ITEM");
			if (nl != null && nl.getLength() > 0) {
				for (int i = 0; i < nl.getLength(); ++i) {
					final ArticleInfo info = parseArticle(tabname,
							(Element) nl.item(i));
					if (info != null) {
						articles.add(info);
					}
				}
			}
		}
		return articles;
	}

	public static final int getArticlesLastSeq(String _board, int _type) {
		final String tabname = BoardInfo.buildTabname(_board, _type);
		final String urlString = UrlString.LIST
			+ ParamName.BOARD + "=" + _board
			+ "&" + ParamName.TYPE + "=" + _type
			+ "&" + ParamName.START + "=0"
			+ "&" + ParamName.COUNT + "=1";
		final HttpClient client = new DefaultHttpClient();
		client.getParams().setParameter(
				HttpConnectionParams.CONNECTION_TIMEOUT, CONN_TIMEOUT);
		final HttpGet get = new HttpGet(urlString);
		try {
			final HttpResponse response = client.execute(get);
			final HttpEntity entity = response.getEntity();
			if (entity == null) {
				// ???
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				final InputStream is = entity.getContent();
				final DocumentBuilder db =
					DocumentBuilderFactory.newInstance().newDocumentBuilder();

				// Parse the article list.
				final Document dom = db.parse(is);
				final Element docEle = dom.getDocumentElement();
				NodeList nl;

				nl = docEle.getElementsByTagName("ITEMS");
				if (nl == null || nl.getLength() <= 0) {
					throw new KidsParseException("ParseException: ITEMS");
				}
				final Element items = (Element) nl.item(0);

				// Get a board item
				nl = items.getElementsByTagName("ITEM");
				if (nl != null && nl.getLength() > 0) {
					final ArticleInfo info = parseArticle(tabname,
							(Element) nl.item(0));
					if (info != null) {
						return info.getSeq();
					}
				}
			}
		} catch (Exception e) {
		}
		return 0;
	}

	public static final int getBoardLastSeq(ContentResolver _cr, String _tabname) {
		int seq = 0;
		final Uri uri = Uri.parse(ArticleProvider.ContentUriString.LIST
				+ _tabname);
		final Cursor c = _cr.query(uri, Projection.SEQ, null, null,
				ArticleProvider.OrderBy.SEQ_DESC);
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
		final Cursor c = _cr.query(ArticleProvider.ContentUri.BOARDS,
				Projection.ARTICLE_TITLE, ArticleProvider.Selection.TABNAME,
				new String[] { _tabname }, null);
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
		final Uri uri = Uri.parse(ArticleProvider.ContentUriString.LIST
				+ _tabname);
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
		return getTableCount(_cr, ArticleProvider.ContentUriString.LIST,
				_tabname, ArticleProvider.Selection.UNREAD);
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
		final Cursor c = _cr.query(ArticleProvider.ContentUri.BOARDS,
				Projection.BOARD_TOTAL_COUNT,
				ArticleProvider.Selection.STATE_ACTIVE, null, null);
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
		values.put(ArticleDatabase.BoardColumn.COUNT,
				getBoardUnreadCount(_cr, _tabname));
		_cr.update(ArticleProvider.ContentUri.BOARDS, values,
				ArticleProvider.Selection.TABNAME, new String[] { _tabname });
	}

	public static final boolean updateArticleRead(ContentResolver _cr,
			String _tabname, int _seq, boolean _read) {
		final Uri uri = Uri.parse(ArticleProvider.ContentUriString.LIST
				+ _tabname);
		final String[] args = new String[] { Integer.toString(_seq) };

		boolean readOld = false;
		final Cursor c = _cr.query(uri, Projection.READ,
				ArticleProvider.Selection.SEQ, args, null);
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
		values.put(ArticleDatabase.ArticleColumn.READ, _read ? 1 : 0);
		final int count = _cr.update(uri, values,
				ArticleProvider.Selection.SEQ, args);
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

	public static final boolean isRecent(String _dateString) {
		boolean result = true;
		final Date local = KidsToLocalDate(_dateString);
		if (local != null) {
			final Calendar calLocal = new GregorianCalendar();
			final Calendar calRecent = new GregorianCalendar();
			calLocal.setTime(local);
			calRecent.setTime(new Date());
			// "Recent" one is marked unread.
			calRecent.add(Calendar.DATE, -MAX_DAYS);
			if (calLocal.before(calRecent)) {
				result = false;
			}
		} else {
			Log.e(TAG, "isRecent: parsing failed: " + _dateString);
		}
		return result;
	}

	public static final void announceUpdateError(Context _context) {
		_context.sendBroadcast(new Intent(UPDATE_ERROR));
	}

	public static final void announceBoardUpdated(Context _context,
			String _tabname) {
		updateBoardCount(_context.getContentResolver(), _tabname);

		final Intent intent = new Intent(BOARD_UPDATED);
		intent.putExtra(PARAM_BASE + ArticleDatabase.BoardColumn.TABNAME,
				_tabname);
		_context.sendBroadcast(intent);
	}

	public static void announceArticleUpdated(Context _context,
			String _tabname, int _seq, String _user, String _thread) {
		updateBoardCount(_context.getContentResolver(), _tabname);

		final Intent intent = new Intent(ARTICLE_UPDATED);
		intent.putExtra(PARAM_BASE + ArticleDatabase.BoardColumn.TABNAME,
				_tabname);
		intent.putExtra(PARAM_BASE + ArticleDatabase.ArticleColumn.SEQ,
				_seq);
		intent.putExtra(PARAM_BASE + ArticleDatabase.ArticleColumn.USER,
				_user);
		intent.putExtra(PARAM_BASE + ArticleDatabase.ArticleColumn.THREAD,
				_thread);
		_context.sendBroadcast(intent);
	}

	@SuppressWarnings("serial")
	private static class KidsParseException extends Exception {
		public KidsParseException(String _message) {
			super(_message);
		}
	}
}