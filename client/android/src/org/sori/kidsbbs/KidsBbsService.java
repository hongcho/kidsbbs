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

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;

public class KidsBbsService extends Service {
	
	private UpdateTask mLastUpdate = null;
	
	@Override
	public void onCreate() {
	}
	
	@Override
	public IBinder onBind(Intent _intent) {
		return null;
	}
	
	private class UpdateTask extends AsyncTask<String, ArticleInfo, Integer> {
		private int mTotalCount = 0;
		
		@Override
		protected void onPreExecute() {
		}
		
		@Override
		protected Integer doInBackground(String... _args) {
			int result = 0;
			String _urlString = _args[0];
			String _board = _args[1];
			String _type = _args[2];
			String tabname = _type + "_" + _board;
			HttpClient client = new DefaultHttpClient();
			HttpGet get = new HttpGet(_urlString);
			try {
				HttpResponse response = client.execute(get);
				HttpEntity entity = response.getEntity();
				if (entity == null) {
				} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					InputStream is = entity.getContent(); 
					DocumentBuilder db =
						DocumentBuilderFactory.newInstance().newDocumentBuilder();

					// Parse the board list.
					Document dom = db.parse(is);
					Element docEle = dom.getDocumentElement();
					NodeList nl;
					Node n;
	
					nl = docEle.getElementsByTagName("ITEMS");
					if (nl == null || nl.getLength() <= 0) {
						return ErrUtils.ERR_XMLPARSING;
					}
					Element items = (Element) nl.item(0);
	
					nl = items.getElementsByTagName("TOTALCOUNT");
					if (nl == null || nl.getLength() <= 0) {
						return ErrUtils.ERR_XMLPARSING;
					}
					n = ((Element) nl.item(0)).getFirstChild();
					mTotalCount = n != null ?
							Integer.parseInt(n.getNodeValue()) : 0;
	
					// Get a board item
					nl = items.getElementsByTagName("ITEM");
					if (nl != null && nl.getLength() > 0) {
						for (int i = 0; i < nl.getLength(); ++i) {
							NodeList nl2;
							Node n2;
							Element item = (Element) nl.item(i);
	
							nl2 = item.getElementsByTagName("THREAD");
							String thread;
							if (nl2 == null || nl2.getLength() <= 0) {
								thread = "";
							} else {
								n2 = ((Element) nl2.item(0)).getFirstChild();
								thread = n2 != null ? n2.getNodeValue() : "";
							}
	
							nl2 = item.getElementsByTagName("COUNT");
							int cnt;
							if (nl2 == null || nl2.getLength() <= 0) {
								cnt = 0;
							} else {
								n2 = ((Element) nl2.item(0)).getFirstChild();
								cnt = n2 != null ?
										Integer.parseInt(n2.getNodeValue()) : 0;
							}
	
							nl2 = item.getElementsByTagName("TITLE");
							if (nl2 == null || nl2.getLength() <= 0) {
								return ErrUtils.ERR_XMLPARSING;
							}
							n2 = ((Element) nl2.item(0)).getFirstChild();
							String title = n2 != null ? n2.getNodeValue() : "";
	
							nl2 = item.getElementsByTagName("SEQ");
							if (nl2 == null || nl2.getLength() <= 0) {
								return ErrUtils.ERR_XMLPARSING;
							}
							n2 = ((Element) nl2.item(0)).getFirstChild();
							int seq = n2 != null ? Integer.parseInt(n2
									.getNodeValue()) : 0;
	
							nl2 = item.getElementsByTagName("DATE");
							if (nl2 == null || nl2.getLength() <= 0) {
								return ErrUtils.ERR_XMLPARSING;
							}
							n2 = ((Element) nl2.item(0)).getFirstChild();
							String date = n2 != null ? n2.getNodeValue() : "";
	
							nl2 = item.getElementsByTagName("AUTHOR");
							if (nl2 == null || nl2.getLength() <= 0) {
								return ErrUtils.ERR_XMLPARSING;
							}
							n2 = ((Element) nl2.item(0)).getFirstChild();
							String user = n2 != null ? n2.getNodeValue() : "";
	
							nl2 = item.getElementsByTagName("DESCRIPTION");
							if (nl2 == null || nl2.getLength() <= 0) {
								return ErrUtils.ERR_XMLPARSING;
							}
							n2 = ((Element) nl2.item(0)).getFirstChild();
							String desc = n2 != null ? n2.getNodeValue() : "";
	
							ArticleInfo info = new ArticleInfo(seq, user, date, title,
									thread, desc, cnt, false);
							addArticle(tabname, info);
							publishProgress(info);
							++result;
						}
					}
				}
			} catch (IOException e) {
				result = ErrUtils.ERR_IO;
			} catch (ParserConfigurationException e) {
				result = ErrUtils.ERR_PARSER;
			} catch (SAXException e) {
				result = ErrUtils.ERR_SAX;
			} finally {
			}
			return result;
		}
		
		@Override
		protected void onProgressUpdate(ArticleInfo... _infos) {
		}
		
		@Override
		protected void onPostExecute(Integer _result) {
			stopSelf();
		}
	}
	
	private static final String[] FIELDS1 = {
		KidsBbsProvider.KEY_ID,
		KidsBbsProvider.KEYA_SEQ,
		KidsBbsProvider.KEYA_AUTHOR,
		KidsBbsProvider.KEYA_DATE,
		KidsBbsProvider.KEYA_TITLE,
	};
	
	private boolean addArticle(String _tabname, ArticleInfo _info) {
		boolean result = true;
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		ContentResolver cr = getContentResolver();
		String where = KidsBbsProvider.KEYA_SEQ + "=" + _info.getSeq();
		Cursor c = cr.query(uri, FIELDS1, where, null, null);
		if (c.getCount() == 0) {
			ContentValues values = new ContentValues();
			values.put(KidsBbsProvider.KEYA_SEQ, _info.getSeq());
			values.put(KidsBbsProvider.KEYA_AUTHOR, _info.getUsername());
			values.put(KidsBbsProvider.KEYA_DATE, _info.getDateString());
			values.put(KidsBbsProvider.KEYA_TITLE, _info.getTitle());
			values.put(KidsBbsProvider.KEYA_THREAD, _info.getThread());
			values.put(KidsBbsProvider.KEYA_BODY, _info.getBody());
			values.put(KidsBbsProvider.KEYA_READ, _info.getRead());
			cr.insert(uri, values);
		} else {
			result = false;
		}
		c.close();
		return result;
	}
	
	private void refreshArticles(String _board, int _type, int _start) {
		if (mLastUpdate == null ||
				mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute(KidsBbs.URL_LIST +
					KidsBbs.PARAM_N_BOARD + "=" + _board +
					"&" + KidsBbs.PARAM_N_TYPE + "=" + _type +
					"&" + KidsBbs.PARAM_N_START + "=" + _start,
					_board, Integer.toString(_type));
		}
	}
	
	private void refreshBoards() {
		
	}
}
