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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class KidsBbsService extends Service
				implements OnSharedPreferenceChangeListener {
	private static final String TAG = "KidsBbsService"; 
	
	private int mUpdateFreq;
	private Timer mUpdateTimer;
	
	// Update to onStartCommand when min SDK becomes >= 5...
	@Override
	public void onStart(Intent _intent, int _startId) {
    	SharedPreferences prefs =
    		PreferenceManager.getDefaultSharedPreferences(
    				getApplicationContext());
    	mUpdateFreq = Integer.parseInt(prefs.getString(
    			Preferences.PREF_UPDATE_FREQ,
    			Preferences.DEFAULT_UPDATE_FREQ));
    	setupTimer(0, mUpdateFreq);
	}
	
	public void onSharedPreferenceChanged(SharedPreferences _prefs,
			String _key) {
		if (_key.equals(Preferences.PREF_UPDATE_FREQ)) {
			int updateFreqNew = Integer.parseInt(_prefs.getString(_key,
					Preferences.DEFAULT_UPDATE_FREQ));
			if (updateFreqNew != mUpdateFreq) {
				int delay = mUpdateFreq < updateFreqNew ?
					mUpdateFreq : updateFreqNew;
				mUpdateFreq = updateFreqNew;
				setupTimer(delay, mUpdateFreq);
			}
		}
	}
	
	private void setupTimer(int _delay, int _period) {
    	mUpdateTimer.cancel();
    	if (_period > 0) {
    		mUpdateTimer = new Timer("KidsBbsUpdates");
    		mUpdateTimer.scheduleAtFixedRate(new TimerTask() {
    				public void run() {
    					refreshArticles();
    				}
    			},
    			_delay*60*1000, _period*60*1000);
    	}
	}
	
	@Override
	public void onCreate() {
		mUpdateTimer = new Timer("KidsBbsUpdates");

		SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(
					getApplicationContext());
		prefs.registerOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public IBinder onBind(Intent _intent) {
		return null;
	}

	private int getTableState(String _tabname) {
		final String[] FIELDS = {
			KidsBbsProvider.KEYB_STATE,
		};
		int result = KidsBbsProvider.STATE_PAUSED;
		ContentResolver cr = getContentResolver();
		Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
				KidsBbsProvider.SELECTION_TABNAME, new String[] { _tabname },
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				result = c.getInt(c.getColumnIndex(
						KidsBbsProvider.KEYB_STATE));
			}
			c.close();
		}
		return result;
	}

	private boolean setTableState(String _tabname, int _state) {
		ContentResolver cr = getContentResolver();
		ContentValues values = new ContentValues();
		values.put(KidsBbsProvider.KEYB_STATE, _state);
		int count = cr.update(KidsBbsProvider.CONTENT_URI_BOARDS, values,
				KidsBbsProvider.SELECTION_TABNAME, new String[] { _tabname });
		return count > 0;
	}


	private synchronized int refreshTable(String _tabname) {
		final String[] FIELDS = {
			KidsBbsProvider.KEYA_SEQ,
			KidsBbsProvider.KEYA_USER,
			KidsBbsProvider.KEYA_DATE,
			KidsBbsProvider.KEYA_TITLE,
			KidsBbsProvider.KEYA_READ,
		};
		final int STATE_DONE = 0;
		final int STATE_INSERT = 1;
		final int STATE_UPDATE = 2;
		
		int tabState = getTableState(_tabname);
		switch (tabState) {
		case KidsBbsProvider.STATE_CREATED:
		case KidsBbsProvider.STATE_UPDATED:
			break;
		default:
			return 0;
		}
		Log.i(TAG, _tabname + ": updating...");
		
		int error = 0;
		int count = 0;
		String[] parsed = BoardInfo.parseTabname(_tabname);
		String board = parsed[1];
		int type = Integer.parseInt(parsed[0]);
		int start = 0;
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		ContentResolver cr = getContentResolver();
		
		int tabSize = KidsBbs.getBoardTableSize(cr, _tabname);
		int maxArticles = KidsBbs.MAX_ARTICLES - tabSize;
		if (tabSize > 0) {
			maxArticles += KidsBbs.MIN_ARTICLES;
		}
		if (maxArticles <= 0) {
			maxArticles = KidsBbs.MIN_ARTICLES;
		}
		
		int state = STATE_INSERT;
		while (state != STATE_DONE) {
			ArrayList<ArticleInfo> articles =
				KidsBbs.getArticles(KidsBbs.URL_LIST, board, type, start);
			if (articles.isEmpty()) {
				state = STATE_DONE;
				if (start == 0) {
					++error;
				}
				break;
			}
			for (int i = 0; state != STATE_DONE && i < articles.size(); ++i) {
				ArticleInfo info = articles.get(i);
				if (count >= KidsBbs.MIN_ARTICLES &&
						!KidsBbs.isRecent(info.getDateString())) {
					Log.i(TAG, _tabname +
							": done updating: reached old articles: " +
							info.getSeq());
					state = STATE_DONE;
					break;
				}
				if (count >= maxArticles) {
					Log.i(TAG, _tabname +
							": done updating: too many articles: " +
							info.getSeq());
					state = STATE_DONE;
					break;
				}
				String[] args = new String[] {
						Integer.toString(info.getSeq())
				};
				ArticleInfo old = null;
				Cursor c = cr.query(uri, FIELDS,
						KidsBbsProvider.SELECTION_SEQ, args, null);
				if (c != null) {
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
						boolean read = c.getInt(c.getColumnIndex(
								KidsBbsProvider.KEYA_READ)) != 0;
						old = new ArticleInfo(_tabname, seq, user, null,
								date, title, null, null, 1, read);
					}
					c.close();
				} else {
					// Unexpected...
					Log.e(TAG, _tabname + ": query failed: " + info.getSeq());
					state = STATE_DONE;
					++error;
					break;
				}
				
				ContentValues values = new ContentValues();
				values.put(KidsBbsProvider.KEYA_SEQ, info.getSeq());
				values.put(KidsBbsProvider.KEYA_USER, info.getUser());
				values.put(KidsBbsProvider.KEYA_AUTHOR, info.getAuthor());
				values.put(KidsBbsProvider.KEYA_DATE,
						info.getDateString());
				values.put(KidsBbsProvider.KEYA_TITLE, info.getTitle());
				values.put(KidsBbsProvider.KEYA_THREAD, info.getThread());
				values.put(KidsBbsProvider.KEYA_BODY, info.getBody());
				boolean read = info.getRead();
				if (state == STATE_UPDATE) {
					read = false;
				} else if (old != null && old.getRead()) {
					read = true;
				}
				values.put(KidsBbsProvider.KEYA_READ, read ? 1 : 0);

				boolean result = true;
				if (old == null) {
					// Not there...
					try {
						switch (state) {
						case STATE_INSERT:
							cr.insert(uri, values);
							break;
						case STATE_UPDATE:
							cr.update(uri, values,
									KidsBbsProvider.SELECTION_SEQ, args);
							break;
						}
					} catch (SQLException e) {
						result = false;
					}
				} else {
					// Hmm... already there...
					if (info.getUser().equals(old.getUser()) &&
							info.getDateString().equals(
									old.getDateString()) &&
							info.getTitle().equals(old.getTitle())) {
						result = false;
						if (tabState != KidsBbsProvider.STATE_CREATED) {
							Log.i(TAG, _tabname +
									": done updating: reached same article: " +
									info.getSeq());
							state = STATE_DONE;
							break;
						}
						state = STATE_UPDATE;
					} else {
						if (state == STATE_INSERT) {
							Log.i(TAG, _tabname +
									": switching to update mode: " +
									info.getSeq() + ": (" +
									old.getUser() + "," +
									old.getDateString() + "," +
									old.getTitle() + ") -> (" +
									info.getUser() + "," +
									info.getDateString() + "," +
									info.getTitle() + ")");
							state = STATE_UPDATE;
						}
						try {
							cr.update(uri, values,
									KidsBbsProvider.SELECTION_SEQ, args);
						} catch (SQLException e) {
							result = false;
						}
					}
				}
				if (result) {
					++count;
					KidsBbs.announceBoardUpdated(KidsBbsService.this,
							_tabname);
				}
			}
			start += articles.size();
		}
		trimBoardTable(_tabname);
		if (count > 0 && tabState == KidsBbsProvider.STATE_UPDATED) {
			KidsBbs.announceNewArticles(KidsBbsService.this, _tabname);
		}
		if (error > 0) {
			Log.e(TAG, _tabname + ": error after updating " +
					count + " articles");
		} else if (tabState == KidsBbsProvider.STATE_CREATED) {
			setTableState(_tabname, KidsBbsProvider.STATE_UPDATED);
		}
		return count;
	}

	private int refreshTables() {
		final String[] FIELDS = {
			KidsBbsProvider.KEYB_TABNAME
		};
		final String WHERE = KidsBbsProvider.KEYB_STATE + "!=" +
			KidsBbsProvider.STATE_PAUSED;

		int total_count = 0;
		ArrayList<String> tabnames = new ArrayList<String>(); 
		ContentResolver cr = getContentResolver();

		// Get all the boards...
		Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
				WHERE, null, KidsBbsProvider.ORDER_BY_ID);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				do {
					tabnames.add(c.getString(c.getColumnIndex(
							KidsBbsProvider.KEYB_TABNAME)));
				} while (c.moveToNext());
			}
			c.close();
		}
		
		// Update each board in the list.
		for (int i = 0; i < tabnames.size(); ++i) {
			String tabname = tabnames.get(i);
			int count = refreshTable(tabname); 
			Log.i(TAG, tabname + ": updated " + count + " articles");
			total_count += count;
		}
		return total_count;
	}
	
	private void refreshArticles() {
		refreshTables();
	}
	
	private int deleteArticles(ContentResolver _cr, Uri _uri, Cursor _c,
			int _max) {
		int count = 0;
		_c.moveToFirst();
		do {
			int seq = _c.getInt(0);
			if (seq > 0) {
				count += _cr.delete(_uri, KidsBbsProvider.SELECTION_SEQ,
						new String[] {Integer.toString(seq)});
			}
		} while (--_max > 0 && _c.moveToNext());
		return count;
	}
	
	private void trimBoardTable(String _tabname) {
		final String[] FIELDS = {
			KidsBbsProvider.KEYA_SEQ,
		};
		final String WHERE = "DATE(" + KidsBbsProvider.KEYA_DATE +
			")!='' AND JULIANDAY(" + KidsBbsProvider.KEYA_DATE +
			")<=JULIANDAY('now'," + KidsBbs.KST_DIFF + "," +
			KidsBbs.MAX_TIME + ")";
		ContentResolver cr = getContentResolver();
		
		// At least 15...
		int size = KidsBbs.getBoardTableSize(cr, _tabname);
		if (size <= KidsBbs.MIN_ARTICLES) {
			return;
		}
		
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		
		// Find the trim point.
		int seq = 0;
		Cursor c = cr.query(uri, FIELDS, WHERE, null,
				KidsBbsProvider.ORDER_BY_SEQ_DESC);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				seq = c.getInt(0);
			}
			c.close();
		}
		
		// Now delete old stuff...
		int count = 0;
		if (seq > 0) {
			c = cr.query(uri, FIELDS, KidsBbsProvider.KEYA_SEQ + "<=" + seq,
					null, KidsBbsProvider.ORDER_BY_SEQ_ASC);
			if (c != null) {
				count += deleteArticles(cr, uri, c,
						size - KidsBbs.MIN_ARTICLES); 
				c.close();
			}
		}

		// Reduce the size to a manageable size.
		size = KidsBbs.getBoardTableSize(cr, _tabname);
		if (size > KidsBbs.MAX_ARTICLES) {
			c = cr.query(uri, FIELDS, null, null,
					KidsBbsProvider.ORDER_BY_SEQ_ASC);
			if (c != null) {
				count += deleteArticles(cr, uri, c,
						size - KidsBbs.MAX_ARTICLES);
				c.close();
			}
		}
		
		Log.i(TAG, _tabname + ": trimed " + count + " articles");
	}
}
