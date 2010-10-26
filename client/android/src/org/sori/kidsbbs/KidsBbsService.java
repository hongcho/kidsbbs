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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class KidsBbsService extends Service {
	public static final String NEW_ARTICLE =
			KidsBbs.PKG_BASE + "NewArticle";
	
	private static final String TAG = "KidsBbsService"; 
	
	private Timer mUpdateTimer;
	
	// Update to onStartCommand when min SDK becomes >= 5...
	@Override
	public void onStart(Intent _intent, int _startId) {
    	SharedPreferences prefs =
    			PreferenceManager.getDefaultSharedPreferences(
    					getApplicationContext());
    	int updateFreq = Integer.parseInt(prefs.getString(
    			Preferences.PREF_UPDATE_FREQ, "15"));
    	
    	mUpdateTimer.cancel();
    	if (updateFreq > 0) {
    		mUpdateTimer = new Timer("KidsBbsUpdates");
    		mUpdateTimer.scheduleAtFixedRate(doRefresh, 0, updateFreq*60*1000);
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

	private int refreshTable(String _tabname) {
		final String[] FIELDS = {
			KidsBbsProvider.KEYA_SEQ,
			KidsBbsProvider.KEYA_USER,
			KidsBbsProvider.KEYA_DATE,
			KidsBbsProvider.KEYA_TITLE,
			KidsBbsProvider.KEYA_READ,
		};
		final int ST_DONE = 0;
		final int ST_INSERT = 1;
		final int ST_UPDATE = 2;

		int count = 0;
		String[] parsed = BoardInfo.parseTabname(_tabname);
		String board = parsed[1];
		int type = Integer.parseInt(parsed[0]);
		int start = 0;
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		
		trimBoardTable(_tabname);

		ContentResolver cr = getContentResolver();
		int state = ST_INSERT;
		while (state != ST_DONE) {
			ArrayList<ArticleInfo> articles =
				KidsBbs.getArticles(KidsBbs.URL_LIST, board, type, start);
			if (articles.isEmpty()) {
				state = ST_DONE;
				break;
			}
			for (int j = 0; state != ST_DONE && j < articles.size();
					++j) {
				ArticleInfo info = articles.get(j);
				String where = KidsBbsProvider.KEYA_SEQ + "=" + info.getSeq();
				ArticleInfo old = null;
				Cursor c = cr.query(uri, FIELDS, where, null, null);
				if (c == null) {
					// Unexpected...
					state = ST_DONE;
				} else {
					if (c.getCount() > 0) {
						c.moveToFirst();
						// Cache the old entry.
						int seq = c.getInt(c.getColumnIndex(
								KidsBbsProvider.KEYA_SEQ));
						String user = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_USER));
						String date = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_DATE));
						String title = c.getString(c.getColumnIndex(
								KidsBbsProvider.KEYA_TITLE));
						boolean read = Boolean.parseBoolean(
								c.getString(c.getColumnIndex(
										KidsBbsProvider.KEYA_READ)));
						if (seq == info.getSeq() && user != null &&
								date != null && title != null) {
							old = new ArticleInfo(_tabname, seq, user, null,
									date, title, null, null, 1, read);
						}
					}
					c.close();
				}
				
				ContentValues values = new ContentValues();
				values.put(KidsBbsProvider.KEYA_SEQ, info.getSeq());
				values.put(KidsBbsProvider.KEYA_USER, info.getUser());
				values.put(KidsBbsProvider.KEYA_DATE, info.getDateString());
				values.put(KidsBbsProvider.KEYA_TITLE, info.getTitle());
				values.put(KidsBbsProvider.KEYA_THREAD, info.getThread());

				boolean read = info.getRead();
				if (state == ST_UPDATE) {
					read = false;
				} else if (old != null && old.getRead()) {
					read = true;
				}
				values.put(KidsBbsProvider.KEYA_READ, read);

				boolean result = true;
				if (old == null) {
					// Not there...
					try {
						switch (state) {
						case ST_INSERT:
							cr.insert(uri, values);
							break;
						case ST_UPDATE:
							cr.update(uri, values, where, null);
							break;
						}
					} catch (SQLException e) {
						result = false;
					}
				} else {
					// Hmm... already there...
					if (info.getUser() == old.getUser() &&
							(info.getDateString() != ArticleInfo.DATE_INVALID &&
									info.getDateString() == old.getDateString()) &&
							info.getTitle() == old.getTitle()) {
						// And the same.  Stop...
						state = ST_DONE;
					} else {
						state = ST_UPDATE;
						try {
							cr.update(uri, values, where, null);
						} catch (SQLException e) {
							result = false;
						}
					}
				}
				if (result) {
					if (!read) {
						announceNewArticle(info);
					}
					++count;
				}
			}
			start += articles.size();
		}
		Log.i(TAG, "Updated " + count + " for " + _tabname);
		return count;
	}

	private int refreshTables() {
		final String[] FIELDS = {
			KidsBbsProvider.KEYB_TABNAME
		};
		final String WHERE =
				KidsBbsProvider.KEYB_STATE + "=" +
					KidsBbsProvider.STATE_INITIALIZE + " OR " +
				KidsBbsProvider.KEYB_STATE + "=" +
					KidsBbsProvider.STATE_DONE;
		final String ORDERBY = KidsBbsProvider.KEY_ID + " ASC";

		int total_count = 0;
		ArrayList<String> tabnames = new ArrayList<String>(); 
		ContentResolver cr = getContentResolver();

		// Get all the boards...
		Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
				WHERE, null, ORDERBY);
		if (c.moveToFirst()) {
			do {
				tabnames.add(c.getString(c.getColumnIndex(
						KidsBbsProvider.KEYB_TABNAME)));
			} while (c.moveToNext());
		}
		c.close();
		
		// Update each board in the list.
		for (int i = 0; i < tabnames.size(); ++i) {
			String tabname = tabnames.get(i);
			int count = refreshTable(tabname); 
			Log.i(TAG, "Updated " + count + " for " + tabname);
			total_count += count;
		}
		return total_count;
	}
	
	private void announceNewArticle(ArticleInfo _info) {
		Intent intent = new Intent(NEW_ARTICLE);
		intent.putExtra(KidsBbs.PKG_BASE + KidsBbsProvider.KEYB_TABNAME,
				_info.getTabname());
		intent.putExtra(KidsBbs.PKG_BASE + KidsBbsProvider.KEYA_DATE,
				_info.getDateString());
		intent.putExtra(KidsBbs.PKG_BASE + KidsBbsProvider.KEYA_TITLE,
				_info.getTitle());
		sendBroadcast(intent);
	}
	
	private void refreshArticles() {
		refreshTables();
	}

	private int getBoardTableSize(String _tabname) {
		final String[] FIELDS = {
			KidsBbsProvider.KEYA_CNT_FIELD,
		};
		int cnt = 0;
		ContentResolver cr = getContentResolver();
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		Cursor c = cr.query(uri, FIELDS, null, null, null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				cnt = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_CNT));
			}
			c.close();
		}
		return cnt;
	}
	
	private void trimBoardTable(String _tabname) {
		final String[] FIELDS = {
				KidsBbsProvider.KEYA_SEQ,
				KidsBbsProvider.KEYA_CNT_FIELD,
			};
		// '-9 hours': KST -> UTC
		// '-14 days': anything older than this...
		final String WHERE = "DATE(" + KidsBbsProvider.KEYA_DATE +
				")!='' AND JULIANDAY(" + KidsBbsProvider.KEYA_DATE +
				")<=JULIANDAY('now','-9 hours','-14 days')";
		final String ORDERBY = "seq DESC";
		ContentResolver cr = getContentResolver();
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		
		// Find the trim point.
		int seq = 0;
		Cursor c = cr.query(uri, FIELDS, WHERE, null, ORDERBY);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				seq = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
			}
			c.close();
		}
		
		// Now delete old stuff...
		if (seq > 0) {
			String where = "seq<=" + seq;
			cr.delete(uri, where, null);
		}
	}
}
