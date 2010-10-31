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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
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

	private static final int MIN_ARTICLES = 15;
	private static final String KST_DIFF = "'-9 hours'";
	private static final String MAX_TIME = "'-" + KidsBbs.MAX_DAYS + " days'";
	
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

	// Matching KidsBbsProvider STATE_*.
	private enum TABLE_STATE {
		PAUSED, // no table or not updating
		CREATED, // table is created, not updated
		UPDATED , // update is done
	};

	private TABLE_STATE getTableState(String _tabname) {
		final String[] FIELDS = {
				KidsBbsProvider.KEYB_STATE,
		};
		ContentResolver cr = getContentResolver();
		Cursor c = cr.query(KidsBbsProvider.CONTENT_URI_BOARDS, FIELDS,
				KidsBbsProvider.SELECTION_TABNAME, new String[] { _tabname },
				null);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				int state = c.getInt(c.getColumnIndex(
						KidsBbsProvider.KEYB_STATE));
				switch (state) {
				case KidsBbsProvider.STATE_CREATED:
					return TABLE_STATE.CREATED;
				case KidsBbsProvider.STATE_UPDATED:
					return TABLE_STATE.UPDATED;
				default:
					return TABLE_STATE.PAUSED;
				}
			}
			c.close();
		}
		return TABLE_STATE.PAUSED;
	}

	private boolean setTableState(String _tabname, TABLE_STATE _state) {
		int state;
		switch (_state) {
		case CREATED:
			state = KidsBbsProvider.STATE_CREATED;
			break;
		case UPDATED:
			state = KidsBbsProvider.STATE_UPDATED;
			break;
		default:
			state = KidsBbsProvider.STATE_PAUSED;
			break;
		}
		
		ContentResolver cr = getContentResolver();
		ContentValues values = new ContentValues();
		values.put(KidsBbsProvider.KEYB_STATE, state);
		int count = cr.update(KidsBbsProvider.CONTENT_URI_BOARDS, values,
				KidsBbsProvider.SELECTION_TABNAME, new String[] { _tabname });
		return count > 0;
	}

	private enum UPDATE_STATE {
		DONE,
		INSERT,
		UPDATE,
	};

	private int refreshTable(String _tabname) {
		final String[] FIELDS = {
			KidsBbsProvider.KEYA_SEQ,
			KidsBbsProvider.KEYA_USER,
			KidsBbsProvider.KEYA_DATE,
			KidsBbsProvider.KEYA_TITLE,
			KidsBbsProvider.KEYA_READ,
		};
		
		TABLE_STATE tabState = getTableState(_tabname);
		switch (tabState) {
		case CREATED:
		case UPDATED:
			break;
		default:
			return 0;
		}
		Log.i(TAG, _tabname + ": updating...");
		
		int count = 0;
		String[] parsed = BoardInfo.parseTabname(_tabname);
		String board = parsed[1];
		int type = Integer.parseInt(parsed[0]);
		int start = 0;
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		
		trimBoardTable(_tabname);

		ContentResolver cr = getContentResolver();
		UPDATE_STATE state = UPDATE_STATE.INSERT;
		while (state != UPDATE_STATE.DONE) {
			ArrayList<ArticleInfo> articles =
				KidsBbs.getArticles(KidsBbs.URL_LIST, board, type, start);
			if (articles.isEmpty()) {
				state = UPDATE_STATE.DONE;
				break;
			}
			for (int i = 0; state != UPDATE_STATE.DONE && i < articles.size();
					++i) {
				ArticleInfo info = articles.get(i);
				if (count >= MIN_ARTICLES && !isRecent(info.getKidsDateString())) {
					Log.i(TAG, _tabname +
							": done updating: reached old articles: " +
							info.getSeq());
					state = UPDATE_STATE.DONE;
					break;
				}
				String[] args = new String[] {
						Integer.toString(info.getSeq())
				};
				ArticleInfo old = null;
				Cursor c = cr.query(uri, FIELDS, KidsBbsProvider.SELECTION_SEQ,
						args, null);
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
								date, title, null, null, false, 1, read);
					}
					c.close();
				} else {
					// Unexpected...
					Log.e(TAG, _tabname + ": query failed: " + info.getSeq());
					state = UPDATE_STATE.DONE;
					break;
				}
				
				ContentValues values = new ContentValues();
				values.put(KidsBbsProvider.KEYA_SEQ, info.getSeq());
				values.put(KidsBbsProvider.KEYA_USER, info.getUser());
				values.put(KidsBbsProvider.KEYA_AUTHOR, info.getAuthor());
				values.put(KidsBbsProvider.KEYA_DATE, info.getKidsDateString());
				values.put(KidsBbsProvider.KEYA_TITLE, info.getTitle());
				values.put(KidsBbsProvider.KEYA_THREAD, info.getThread());
				values.put(KidsBbsProvider.KEYA_BODY, info.getBody());
				boolean read = info.getRead();
				if (state == UPDATE_STATE.UPDATE) {
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
						case INSERT:
							cr.insert(uri, values);
							break;
						case UPDATE:
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
						if (tabState != TABLE_STATE.CREATED) {
							Log.i(TAG, _tabname +
									": done updating: reached same article: " +
									info.getSeq());
							state = UPDATE_STATE.DONE;
							break;
						}
					} else {
						if (state == UPDATE_STATE.INSERT) {
							Log.i(TAG, _tabname +
									": switching to update mode: " +
									info.getSeq() + ": (" +
									old.getUser() + "," +
									old.getDateString() + "," +
									old.getTitle() + ") -> (" +
									info.getUser() + "," +
									info.getDateString() + "," +
									info.getTitle() + ")");
							state = UPDATE_STATE.UPDATE;
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
					KidsBbs.announceBoardUpdated(KidsBbsService.this, _tabname);
				}
			}
			start += articles.size();
		}
		if (count > 0 && tabState == TABLE_STATE.UPDATED) {
			KidsBbs.announceNewArticles(KidsBbsService.this, _tabname);
		}
		if (tabState == TABLE_STATE.CREATED) {
			setTableState(_tabname, TABLE_STATE.UPDATED);
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
	
	private void trimBoardTable(String _tabname) {
		final String[] FIELDS = {
				KidsBbsProvider.KEYA_SEQ,
				KidsBbsProvider.KEYA_CNT_FIELD,
			};
		final String WHERE = "DATE(" + KidsBbsProvider.KEYA_DATE +
				")!='' AND JULIANDAY(" + KidsBbsProvider.KEYA_DATE +
				")<=JULIANDAY('now'," + KST_DIFF + "," + MAX_TIME + ")";
		ContentResolver cr = getContentResolver();
		
		// At least 15...
		int limit = KidsBbs.getBoardTableSize(cr, _tabname) - MIN_ARTICLES;
		if (limit <= 0) {
			return;
		}
		
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		
		// Find the trim point.
		int seq = 0;
		Cursor c = cr.query(uri, FIELDS, WHERE, null,
				KidsBbsProvider.ORDER_BY_SEQ);
		if (c != null) {
			if (c.getCount() > 0) {
				c.moveToFirst();
				seq = c.getInt(c.getColumnIndex(KidsBbsProvider.KEYA_SEQ));
			}
			c.close();
		}
		
		// Now delete old stuff...
		if (seq > 0) {
			// A "hack" to add "LIMIT" to the SQL statement.
			String where = KidsBbsProvider.KEYA_SEQ + "<=" + seq +
					" LIMIT " + limit;
			cr.delete(uri, where, null);
		}
	}
	
	private static final boolean isRecent(String _dateString) {
		boolean result = true;
		Date local = KidsBbs.KidsToLocalDate(_dateString);
		if (local != null) {
			Calendar calLocal = new GregorianCalendar();
			Calendar calRecent = new GregorianCalendar();
			calLocal.setTime(local);
			calRecent.setTime(new Date());
			// "Recent" one is marked unread.
			calRecent.add(Calendar.DATE, -KidsBbs.MAX_DAYS);
			if (calLocal.before(calRecent)) {
				result = false;
			}
		} else {
			Log.e(TAG, "isRecent: parsing failed: " + _dateString);
		}
		return result;
	}
}
