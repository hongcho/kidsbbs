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
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class KidsBbsService extends Service {
	public static final String NEW_ARTICLES_FOUND = "NEW_ARTICLES_FOUND";
	
	private UpdateTask mLastUpdate = null;
	private Timer mUpdateTimer;
	
	// Update to onStartCommand when min SDK becomes >= 5...
	@Override
	public void onStart(Intent _intent, int _startId) {
    	SharedPreferences prefs =
    			PreferenceManager.getDefaultSharedPreferences(
    					getApplicationContext());
    	int updateFreq = Integer.parseInt(prefs.getString(
    			Preferences.PREF_UPDATE_FREQ, "0"));
    	
    	mUpdateTimer.cancel();
    	if (updateFreq > 0) {
    		mUpdateTimer = new Timer("KidsBbsUpdates");
    		mUpdateTimer.scheduleAtFixedRate(doRefresh, 0, updateFreq*60*1000);
    	} else {
    		refreshArticles();
    	}
	}
	
	private TimerTask doRefresh = new TimerTask() {
		public void run() {
			refreshArticles();
		}
	};
	
	@Override
	public void onCreate() {
		mUpdateTimer = new Timer("KidsBbsUpdates");
	}
	
	@Override
	public IBinder onBind(Intent _intent) {
		return null;
	}
	
	private class UpdateTask extends AsyncTask<String, ArticleInfo, Integer> {
		@Override
		protected void onPreExecute() {
		}
		
		@Override
		protected Integer doInBackground(String... _args) {
			int total_count = 0;
			String _urlString = _args[0];
			String _tabname = _args[1];
			ArrayList<String> tabnames = new ArrayList<String>(); 

			// Which boards to update?
			if (_tabname == null || _tabname == "") {
				// Get all the boards...
				final String[] FIELDS = {
					KidsBbsProvider.KEYB_TABNAME
				};
				ContentResolver cr = getContentResolver();
				Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
						null, null, null);
				if (c.moveToFirst()) {
					do {
						tabnames.add(c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYB_TABNAME)));
					} while (c.moveToNext());
				}
				c.close();
			} else {
				tabnames.add(_tabname);
			}
			
			final String[] FIELDS = {
				KidsBbsProvider.KEY_ID,
				KidsBbsProvider.KEYA_SEQ,
				KidsBbsProvider.KEYA_USER,
				KidsBbsProvider.KEYA_DATE,
				KidsBbsProvider.KEYA_TITLE,
			};
			final int ST_DONE = 0;
			final int ST_INSERT = 1;
			final int ST_UPDATE = 2;

			// Update each board in the list.
			ContentResolver cr = getContentResolver();
			for (int i = 0; i < tabnames.size(); ++i) {
				String tabname = tabnames.get(i);
				String[] parsed = BoardInfo.parseTabname(tabname);
				String board = parsed[1];
				int type = Integer.parseInt(parsed[0]);
				int start = 0;
				Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + tabname);
				int state = ST_INSERT;
				while (state != ST_DONE) {
					ArrayList<ArticleInfo> articles = getArticles(_urlString,
							board, type, start);
					if (articles.isEmpty()) {
						break;
					}
					for (int j = 0; j < articles.size(); ++j) {
						ArticleInfo info = articles.get(j);
						String where = KidsBbsProvider.KEYA_SEQ + "=" + info.getSeq();
						
						ContentValues values = new ContentValues();
						values.put(KidsBbsProvider.KEYA_SEQ, info.getSeq());
						values.put(KidsBbsProvider.KEYA_USER, info.getUser());
						values.put(KidsBbsProvider.KEYA_AUTHOR, info.getAuthor());
						values.put(KidsBbsProvider.KEYA_DATE, info.getDateString());
						values.put(KidsBbsProvider.KEYA_TITLE, info.getTitle());
						values.put(KidsBbsProvider.KEYA_THREAD, info.getThread());
						values.put(KidsBbsProvider.KEYA_BODY, info.getBody());
						values.put(KidsBbsProvider.KEYA_READ, info.getRead());

						// Try inserting or updating...
						boolean result = true;
						try {
							switch (state) {
							case ST_INSERT:
								cr.insert(uri, values);
								break;
							case ST_UPDATE:
								cr.update(uri, values, where, null);
								break;
							default:
								state = ST_DONE;
								break;
							}
						} catch (NullPointerException e) {
							result = false;
						} catch (SQLException e) {
							result = false;
						} finally {
						}
						
						if (result) {
							++total_count;
						} else {
							// It didn't work...
							switch (state) {
							case ST_INSERT:
								Cursor c = cr.query(uri, FIELDS, where, null,
										null);
								if (c == null || c.getCount() != 1) {
									if (c != null) {
										c.close();
									}
									state = ST_DONE;
								} else {
									// Get the existing article information out.
									int seq = c.getInt(c.getColumnIndex(
											KidsBbsProvider.KEYA_SEQ));
									String user = c.getString(c.getColumnIndex(
											KidsBbsProvider.KEYA_USER));
									String date = c.getString(c.getColumnIndex(
											KidsBbsProvider.KEYA_DATE));
									String title = c.getString(c.getColumnIndex(
											KidsBbsProvider.KEYA_TITLE));
									c.close();

									if (info.getUser() != user ||
											!(info.getDateString() == ArticleInfo.DATE_INVALID ||
													info.getDateString() == date) ||
											info.getTitle() != title) {
										state = ST_UPDATE;
										result = true;
										try {
											cr.update(uri, values, where, null);
										} catch (NullPointerException e) {
											result = false;
										} catch (SQLException e) {
											result = false;
										} finally {
										}
										if (!result) {
											// Still didn't work...
											state = ST_DONE;
										}
									} else {
										// It's the same, so stop.
										state = ST_DONE;
									}
								}
								break;
							case ST_UPDATE:
								// TODO: Compare...
								state = ST_DONE;
								break;
							default:
								break;
							}
						}
					}
					start += articles.size();
				};
			}
			return total_count;
		}
		
		@Override
		protected void onProgressUpdate(ArticleInfo... _infos) {
		}
		
		@Override
		protected void onPostExecute(Integer _result) {
		}
	}
	
	private void announceNewArticle(String _tabname, ArticleInfo _info) {
		Intent intent = new Intent(NEW_ARTICLES_FOUND);
		intent.putExtra(KidsBbsProvider.KEYB_TABNAME, _tabname);
		intent.putExtra(KidsBbsProvider.KEYA_AUTHOR, _info.getAuthor());
		intent.putExtra(KidsBbsProvider.KEYA_DATE, _info.getDateString());
		intent.putExtra(KidsBbsProvider.KEYA_TITLE, _info.getTitle());
		sendBroadcast(intent);
	}
	
	private void refreshArticles() {
		if (mLastUpdate == null ||
				mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute(KidsBbs.URL_LIST, null);
		}
	}
	
	private ArrayList<ArticleInfo> getArticles(String _base, String _board, int _type,
			int _start) {
		ArrayList<ArticleInfo> articles = new ArrayList<ArticleInfo>();
		String urlString = _base +
				KidsBbs.PARAM_N_BOARD + "=" + _board +
				"&" + KidsBbs.PARAM_N_TYPE + "=" + _type +
				"&" + KidsBbs.PARAM_N_START + "=" + _start;
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(urlString);
		try {
			HttpResponse response = client.execute(get);
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				// ???
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				InputStream is = entity.getContent(); 
				DocumentBuilder db =
					DocumentBuilderFactory.newInstance().newDocumentBuilder();

				// Parse the board list.
				Document dom = db.parse(is);
				Element docEle = dom.getDocumentElement();
				NodeList nl;

				nl = docEle.getElementsByTagName("ITEMS");
				if (nl == null || nl.getLength() <= 0) {
					throw new ParserConfigurationException(
							"XMLParser failed: ITEMS");
				}
				Element items = (Element)nl.item(0);

				// Get a board item
				nl = items.getElementsByTagName("ITEM");
				if (nl != null && nl.getLength() > 0) {
					for (int i = 0; i < nl.getLength(); ++i) {
						NodeList nl2;
						Node n2;
						Element item = (Element)nl.item(i);

						nl2 = item.getElementsByTagName("THREAD");
						String thread;
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: THREAD");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: THREAD");
						}
						thread = n2.getNodeValue();

						nl2 = item.getElementsByTagName("TITLE");
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: TITLE");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: TITLE");
						}
						String title = n2.getNodeValue();

						nl2 = item.getElementsByTagName("SEQ");
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: SEQ");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: SEQ");
						}
						int seq = Integer.parseInt(n2.getNodeValue());

						nl2 = item.getElementsByTagName("DATE");
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: DATE");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: DATE");
						}
						String date = n2.getNodeValue();

						nl2 = item.getElementsByTagName("USER");
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: USER");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: USER");
						}
						String user = n2.getNodeValue();

						nl2 = item.getElementsByTagName("AUTHOR");
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: AUTHOR");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: AUTHOR");
						}
						String author = n2.getNodeValue();

						nl2 = item.getElementsByTagName("DESCRIPTION");
						if (nl2 == null || nl2.getLength() <= 0) {
							throw new ParserConfigurationException(
									"XMLParser failed: DESCRIPTION");
						}
						n2 = ((Element)nl2.item(0)).getFirstChild();
						if (n2 == null) {
							throw new ParserConfigurationException(
									"XMLParser failed: DESCRIPTION");
						}
						String desc = n2.getNodeValue();

						articles.add(new ArticleInfo(seq, user, author, date,
								title, thread, desc, 1, false));
					}
				}
			}
		} catch (IOException e) {
		} catch (ParserConfigurationException e) {
		} catch (SAXException e) {
		} finally {
		}
		return articles;
	}
}
