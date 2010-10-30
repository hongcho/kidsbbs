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

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class KidsBbs extends Activity {
	public static final String TAG = "KidsBbs";
	public static final String PKG_BASE = "org.sori.kidsbbs.";
	public static final String BCAST_BASE = PKG_BASE + "broadcast.";
	public static final String PARAM_BASE = PKG_BASE + "param.";
	
	public static final String NEW_ARTICLES = BCAST_BASE + "NewArticles";
	public static final String ARTICLE_UPDATED = BCAST_BASE + "ArticleUpdated";

	private static final String URL_BASE = "http://sori.org/kids/kids.php?_o=1&";
	public static final String URL_BLIST = URL_BASE; 
	public static final String URL_LIST = URL_BASE + "m=list&";
	public static final String URL_TLIST = URL_BASE + "m=tlist&";
	public static final String URL_THREAD = URL_BASE + "m=thread&";
	public static final String URL_USER = URL_BASE + "m=user&";
	public static final String URL_VIEW = URL_BASE + "m=view&";
	
	private static final String URI_BASE = "content:/kidsbbs/";
	public static final String URI_INTENT_TLIST = URI_BASE + "tlist?";
	public static final String URI_INTENT_THREAD = URI_BASE + "thread?";
	public static final String URI_INTENT_USER = URI_BASE + "user?";
	public static final String URI_INTENT_VIEW = URI_BASE + "view?";

	public static final String PARAM_N_TITLE = "bt";
	public static final String PARAM_N_BOARD = "b";
	public static final String PARAM_N_TYPE = "t";
	public static final String PARAM_N_THREAD = "id";
	public static final String PARAM_N_USER = "u";
	public static final String PARAM_N_SEQ = "p";
	public static final String PARAM_N_START = "s";
	public static final String PARAM_N_COUNT = "n";
	public static final String PARAM_N_TABNAME = "tn";
	
	private static final String DATE_INVALID = "0000-00-00 00:00:00";
	private static final String DATESHORT_INVALID = "0000-00-00";
	//private static final String DATE_FORMAT = "MMM dd, yyyy h:mm:ss aa";
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

	private static final DateFormat DF_TIME =
		DateFormat.getTimeInstance(DateFormat.SHORT);
	private static final DateFormat DF_DATE =
		DateFormat.getDateInstance(DateFormat.SHORT);
	private static final DateFormat DF_FULL =
		DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
	private static final DateFormat DF_KIDS;
	static {
		DF_KIDS = new SimpleDateFormat(DATE_FORMAT);
		DF_KIDS.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static final int MAX_DAYS = 14;
	
	public static final Date KidsToLocalDate(String _dateString) {
		try {
			Date date = DF_FULL.parse(KidsToLocalDateString(_dateString));
			return date;
		} catch (ParseException e) {
			return null;
		}
	}
	
	public static final String GetShortDateString(String _dateString) {
		try {
			Date local = DF_FULL.parse(_dateString);
			Date now = new Date();
			if (now.getYear() == local.getYear()
					&& now.getMonth() == local.getMonth()
					&& now.getDate() == local.getDate()) {
				return DF_TIME.format(local);
			} else {
				return DF_DATE.format(local);
			}
		} catch (ParseException e) {
			return DATESHORT_INVALID;
		}
	}
	
	public static final String KidsToLocalDateString(String _dateString) {
		try {
			Date date = DF_KIDS.parse(_dateString);
			return DF_FULL.format(date);
		} catch (ParseException e) {
			return DATE_INVALID;
		}
	}
	
	public static final String LocalToKidsDateString(String _dateString) {
		try {
			Date date = DF_FULL.parse(_dateString);
			return DF_KIDS.format(date);
		} catch (ParseException e) {
			return DATE_INVALID;
		}
	}

	public static enum ParseMode {
		VIEW,
		ALIST,
		TLIST,
		LIST,
	};
	
	public static final ArticleInfo parseArticle(ParseMode _mode,
			String _tabname, Element _item) {
		NodeList nl;
		Node n;
		try {
			String thread = null;
			if (_mode != ParseMode.ALIST) {
				nl = _item.getElementsByTagName("THREAD");
				if (nl == null || nl.getLength() <= 0) {
					throw new KidsParseException("ParseException: THREAD");
				}
				n = ((Element)nl.item(0)).getFirstChild();
				if (n == null) {
					throw new KidsParseException("ParseException: THREAD");
				}
				thread = n != null ? n.getNodeValue() : null;
			}
			
			int cnt = 1;
			if (_mode == ParseMode.ALIST ||
					_mode == ParseMode.TLIST) {
				nl = _item.getElementsByTagName("COUNT");
				if (nl == null || nl.getLength() <= 0) {
					throw new KidsParseException("ParseException: COUNT");
				}
				n = ((Element)nl.item(0)).getFirstChild();
				if (n == null) {
					throw new KidsParseException("ParseException: COUNT");
				}
				cnt = n != null ? Integer.parseInt(n.getNodeValue()) : 1;
			}

			nl = _item.getElementsByTagName("TITLE");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: TITLE");
			}
			n = ((Element)nl.item(0)).getFirstChild();
			String title = n != null ? n.getNodeValue() : "";

			nl = _item.getElementsByTagName("SEQ");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: SEQ");
			}
			n = ((Element)nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: SEQ");
			}
			int seq = Integer.parseInt(n.getNodeValue());

			nl = _item.getElementsByTagName("DATE");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: DATE");
			}
			n = ((Element)nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: DATE");
			}
			String date = n.getNodeValue();

			nl = _item.getElementsByTagName("USER");
			if (nl == null || nl.getLength() <= 0) {
				throw new KidsParseException("ParseException: USER");
			}
			n = ((Element)nl.item(0)).getFirstChild();
			if (n == null) {
				throw new KidsParseException("ParseException: USER");
			}
			String user = n.getNodeValue();

			String author = null;
			if (_mode == ParseMode.VIEW) {
				nl = _item.getElementsByTagName("AUTHOR");
				if (nl == null || nl.getLength() <= 0) {
					throw new KidsParseException("ParseException: AUTHOR");
				}
				n = ((Element)nl.item(0)).getFirstChild();
				if (n == null) {
					throw new KidsParseException("ParseException: AUTHOR");
				}
				author = n != null ? n.getNodeValue() : null;
			}

			String desc = "";
			nl = _item.getElementsByTagName("DESCRIPTION");
			if (nl != null && nl.getLength() > 0) {
				n = ((Element)nl.item(0)).getFirstChild();
				desc = n != null ? n.getNodeValue() : "";
			}

			return new ArticleInfo(_tabname, seq, user, author, date, title,
					thread, desc, cnt, false);
		} catch (KidsParseException e) {
			Log.w(TAG, e);
			return null;
		}
	}
	
	public static final ArrayList<ArticleInfo> getArticles(String _base,
			String _board, int _type, int _start) {
		ArrayList<ArticleInfo> articles = new ArrayList<ArticleInfo>();
		String tabname = BoardInfo.buildTabname(_board, _type);
		String urlString = _base +
				KidsBbs.PARAM_N_BOARD + "=" + _board +
				"&" + KidsBbs.PARAM_N_TYPE + "=" + _type +
				"&" + KidsBbs.PARAM_N_START + "=" + _start;
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(urlString);
		try {
			HttpResponse response = client.execute(get); // IOException
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				// ???
			} else if (response.getStatusLine().getStatusCode() ==
					HttpStatus.SC_OK) {
				InputStream is = entity.getContent(); // IOException
				DocumentBuilder db =
					DocumentBuilderFactory.newInstance().newDocumentBuilder(); // ParserConfigurationException

				// Parse the board list.
				Document dom = db.parse(is); // IOException, SAXException
				Element docEle = dom.getDocumentElement();
				NodeList nl;

				nl = docEle.getElementsByTagName("ITEMS");
				if (nl == null || nl.getLength() <= 0) {
					throw new KidsParseException("ParseException: ITEMS");
				}
				Element items = (Element)nl.item(0);

				// Get a board item
				nl = items.getElementsByTagName("ITEM");
				if (nl != null && nl.getLength() > 0) {
					for (int i = 0; i < nl.getLength(); ++i) {
						ArticleInfo info = parseArticle(ParseMode.LIST,
								tabname, (Element)nl.item(i));
						if (info != null) {
							articles.add(info);
						}
					}
				}
			}
		} catch (IOException e) {
			Log.w(TAG, e);
		} catch (ParserConfigurationException e) {
			Log.w(TAG, e);
		} catch (SAXException e) {
			Log.w(TAG, e);
		} catch (KidsParseException e) {
			Log.w(TAG, e);
		}
		return articles;
	}
	
	public static boolean updateArticleRead(ContentResolver _cr,
			ArticleInfo _info) {
		final String[] FIELDS = {
				KidsBbsProvider.KEYA_READ,
		};
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST +
				_info.getTabname());
		String[] args = new String[] { Integer.toString(_info.getSeq()) };
		
		boolean readOld = false;
		Cursor c = _cr.query(uri, FIELDS, KidsBbsProvider.SELECTION_SEQ, args,
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				readOld = c.getInt(c.getColumnIndex(
						KidsBbsProvider.KEYA_READ)) != 0;
			}
			c.close();
		}
		if (readOld == _info.getRead()) {
			return false;
		}
		
		ContentValues values = new ContentValues();
		values.put(KidsBbsProvider.KEYA_READ, _info.getRead() ? 1 : 0);
		int count = _cr.update(uri, values, KidsBbsProvider.SELECTION_SEQ,
				args);
		return (count > 0);
	}
	
	public static boolean getArticleRead(ContentResolver _cr, Uri _uri,
			String _where, String[] _whereArgs, ArticleInfo _info) {
		final String[] FIELDS = {
				KidsBbsProvider.KEYA_ALLREAD_FIELD,
		};
		boolean read = false;
		Cursor c = _cr.query(_uri, FIELDS, _where, _whereArgs, null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				read = c.getInt(c.getColumnIndex(
						KidsBbsProvider.KEYA_ALLREAD)) != 0;
			}
			c.close();
		}
		return read;
	}
	
	public static void announceNewArticles(Context _context,
			String _tabname) {
		Intent intent = new Intent(KidsBbs.NEW_ARTICLES);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME,
				_tabname);
		_context.sendBroadcast(intent);
	}
	
	public static void announceArticleUpdated(Context _context,
			ArticleInfo _info) {
		Intent intent = new Intent(KidsBbs.ARTICLE_UPDATED);
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME,
				_info.getTabname());
		intent.putExtra(KidsBbs.PARAM_BASE + KidsBbsProvider.KEYA_SEQ,
				_info.getSeq());
		_context.sendBroadcast(intent);
	}
	
	private static class KidsParseException extends Exception {
		public KidsParseException(String _message) {
			super(_message);
		}
	}

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);

		startService(new Intent(this, KidsBbsService.class));

		startActivity(new Intent(this, KidsBbsBlist.class));
		finish();
	}
}