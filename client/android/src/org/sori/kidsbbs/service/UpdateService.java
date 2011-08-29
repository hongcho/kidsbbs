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
package org.sori.kidsbbs.service;

import java.util.ArrayList;

import org.sori.kidsbbs.KidsBbs;
import org.sori.kidsbbs.R;
import org.sori.kidsbbs.data.ArticleInfo;
import org.sori.kidsbbs.data.BoardInfo;
import org.sori.kidsbbs.provider.ArticleProvider;
import org.sori.kidsbbs.ui.BoardListActivity;
import org.sori.kidsbbs.ui.Preferences;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

public class UpdateService extends Service
		implements OnSharedPreferenceChangeListener {
	private static final String TAG = "KidsBbsService";

	private int mUpdateFreq;
	private UpdateTask mLastUpdate = null;

	ConnectivityManager mConnectivities;
	AlarmManager mAlarms;
	PendingIntent mAlarmIntent;

	private Integer mIsPausedSync = 0;
	private boolean mIsPaused = false;
	private boolean mBgDataEnabled = true;
	private boolean mNoConnectivity = false;

	private ContentResolver mResolver;
	private NotificationManager mNotificationManager;
	private Notification mNewArticlesNotification;
	private boolean mNotificationOn = true;
	private int mNotificationDefaults = Notification.DEFAULT_LIGHTS
			| Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
	private String mNotificationTitleString;
	private String mNotificationMessage;

	// Update to onStartCommand when min SDK becomes >= 5...
	@Override
	public void onStart(Intent _intent, int _startId) {
		setupAlarm(mUpdateFreq,
				_intent.getStringExtra(
						KidsBbs.PARAM_BASE + KidsBbs.PARAM_N_TABNAME));
	}

	public void onSharedPreferenceChanged(SharedPreferences _prefs, String _key) {
		if (_key.equals(Preferences.PREF_UPDATE_FREQ)) {
			final int updateFreqNew = Integer.parseInt(_prefs.getString(_key,
					Preferences.getDefaultUpdateFreq(this)));
			if (updateFreqNew != mUpdateFreq) {
				mUpdateFreq = updateFreqNew;
				setupAlarm(mUpdateFreq, null);
			}
		} else if (_key.equals(Preferences.PREF_NOTIFICATION)) {
			mNotificationOn = _prefs.getBoolean(_key, true);
		} else if (_key.equals(Preferences.PREF_NOTIFICATION_LIGHTS)) {
			if (_prefs.getBoolean(_key, true)) {
				mNotificationDefaults |= Notification.DEFAULT_LIGHTS;
			} else {
				mNotificationDefaults &= ~Notification.DEFAULT_LIGHTS;
			}
		} else if (_key.equals(Preferences.PREF_NOTIFICATION_SOUND)) {
			if (_prefs.getBoolean(_key, true)) {
				mNotificationDefaults |= Notification.DEFAULT_SOUND;
			} else {
				mNotificationDefaults &= ~Notification.DEFAULT_SOUND;
			}
		} else if (_key.equals(Preferences.PREF_NOTIFICATION_VIBRATE)) {
			if (_prefs.getBoolean(_key, true)) {
				mNotificationDefaults |= Notification.DEFAULT_VIBRATE;
			} else {
				mNotificationDefaults &= ~Notification.DEFAULT_VIBRATE;
			}
		}
	}

	private void setupAlarm(long _period, String _tabname) {
		if (_period > 0) {
			final long msPeriod = _period * 60 * 1000;
			mAlarms.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime() + msPeriod, msPeriod,
					mAlarmIntent);
		} else {
			mAlarms.cancel(mAlarmIntent);
		}
		refreshArticles(_tabname);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		final Resources resources = getResources();
		mNotificationTitleString =
			resources.getString(R.string.notification_title_text);
		mNotificationMessage =
			resources.getString(R.string.notification_message);

		mResolver = getContentResolver();

		mConnectivities = (ConnectivityManager) getSystemService(
				Context.CONNECTIVITY_SERVICE);

		mNotificationManager = (NotificationManager) getSystemService(
				Context.NOTIFICATION_SERVICE);
		mNewArticlesNotification = new Notification(R.drawable.icon,
				mNotificationTitleString, System.currentTimeMillis());
		mNewArticlesNotification.flags |= Notification.FLAG_AUTO_CANCEL;
		mNewArticlesNotification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
		mNotificationDefaults |= mNewArticlesNotification.defaults;

		mAlarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		mAlarmIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				AlarmReceiver.UPDATE_BOARDS_ALARM), 0);

		final SharedPreferences prefs =
			PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		mUpdateFreq = Integer.parseInt(prefs.getString(
				Preferences.PREF_UPDATE_FREQ,
				Preferences.getDefaultUpdateFreq(this)));
		mNotificationOn = prefs.getBoolean(Preferences.PREF_NOTIFICATION, true);
		mNotificationDefaults = 0;
		if (prefs.getBoolean(Preferences.PREF_NOTIFICATION_LIGHTS, true)) {
			mNotificationDefaults |= Notification.DEFAULT_LIGHTS;
		}
		if (prefs.getBoolean(Preferences.PREF_NOTIFICATION_SOUND, true)) {
			mNotificationDefaults |= Notification.DEFAULT_SOUND;
		}
		if (prefs.getBoolean(Preferences.PREF_NOTIFICATION_VIBRATE, true)) {
			mNotificationDefaults |= Notification.DEFAULT_VIBRATE;
		}
		prefs.registerOnSharedPreferenceChangeListener(this);

		registerReceivers();
	}

	@Override
	public void onDestroy() {
		unregisterReceivers();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent _intent) {
		return null;
	}

	private class UpdateTask extends AsyncTask<String, Void, Integer> {
		@Override
		protected Integer doInBackground(String... _args) {
			return refreshTables(_args[0]);
		}

		@Override
		protected void onPostExecute(Integer _result) {
			stopSelf();
		}

		private int getTableState(String _tabname) {
			final String[] FIELDS = { ArticleProvider.KEYB_STATE, };
			int result = ArticleProvider.STATE_PAUSED;
			final Cursor c = mResolver.query(
					ArticleProvider.CONTENT_URI_BOARDS, FIELDS,
					ArticleProvider.SELECTION_TABNAME,
					new String[] { _tabname }, null);
			if (c != null) {
				if (c.getCount() > 0) {
					c.moveToFirst();
					result = c.getInt(
							c.getColumnIndex(ArticleProvider.KEYB_STATE));
				}
				c.close();
			}
			return result;
		}

		private boolean setTableState(String _tabname, int _state) {
			final ContentValues values = new ContentValues();
			values.put(ArticleProvider.KEYB_STATE, _state);
			final int count = mResolver.update(
					ArticleProvider.CONTENT_URI_BOARDS, values,
					ArticleProvider.SELECTION_TABNAME,
					new String[] { _tabname });
			return count > 0;
		}

		private synchronized int refreshTable(String _tabname) {
			final String[] FIELDS = { ArticleProvider.KEYA_SEQ,
					ArticleProvider.KEYA_USER, ArticleProvider.KEYA_DATE,
					ArticleProvider.KEYA_TITLE, ArticleProvider.KEYA_READ, };

			final int tabState = getTableState(_tabname);
			if (tabState == ArticleProvider.STATE_PAUSED) {
				return 0;
			}

			int error = 0;
			int count = 0;
			final String[] parsed = BoardInfo.parseTabname(_tabname);
			final String board = parsed[1];
			final int type = Integer.parseInt(parsed[0]);
			final Uri uri = Uri.parse(ArticleProvider.CONTENT_URISTR_LIST
					+ _tabname);

			// Where to begin
			final int latest = KidsBbs.getArticlesLastSeq(board, type);
			final int start_first = latest - KidsBbs.MAX_FIRST_ARTICLES;
			final int start_max = latest - KidsBbs.MAX_ARTICLES;
			int start = KidsBbs.getBoardLastSeq(mResolver, _tabname) - 10;
			if (start <= 0) {
				start = start_first;
			} else if (start < start_max) {
				start = start_max;
			}
			if (start < 0) {
				start = 0;
			}
			Log.d(TAG, _tabname + ": (" + tabState + ") updating from "
					+ start);

			boolean fDone = false;
			while (!fDone) {
				ArrayList<ArticleInfo> articles;
				try {
					articles = KidsBbs.getArticles(board, type, start);
				} catch (Exception e) {
					Log.e(TAG, _tabname + ": article retrieval failed", e);
					fDone = true;
					++error;
					break;
				}
				if (articles.isEmpty()) {
					Log.d(TAG, _tabname + ": no more articles");
					fDone = true;
					break;
				}
				for (int i = 0; !fDone && i < articles.size(); ++i) {
					final ArticleInfo info = articles.get(i);
					if (!KidsBbs.isRecent(info.getDateString())) {
						continue;
					}
					final String[] args = new String[] { Integer.toString(
							info.getSeq()) };
					ArticleInfo old = null;
					final Cursor c = mResolver.query(uri, FIELDS,
							ArticleProvider.SELECTION_SEQ, args, null);
					if (c != null) {
						if (c.getCount() > 0) {
							c.moveToFirst();
							// Cache the old entry.
							final int seq = c.getInt(
									c.getColumnIndex(ArticleProvider.KEYA_SEQ));
							final String user = c.getString(
									c.getColumnIndex(ArticleProvider.KEYA_USER));
							final String date = c.getString(
									c.getColumnIndex(ArticleProvider.KEYA_DATE));
							final String title = c.getString(
									c.getColumnIndex(ArticleProvider.KEYA_TITLE));
							final boolean read = c.getInt(
									c.getColumnIndex(ArticleProvider.KEYA_READ))
									!= 0;
							old = new ArticleInfo(_tabname, seq, user, null,
									date, title, null, null, 1, read);
						}
						c.close();
					} else {
						// Unexpected...
						Log.e(TAG, _tabname + ": query failed: "
								+ info.getSeq());
						fDone = true;
						++error;
						break;
					}

					final ContentValues values = new ContentValues();
					values.put(ArticleProvider.KEYA_SEQ, info.getSeq());
					values.put(ArticleProvider.KEYA_USER, info.getUser());
					values.put(ArticleProvider.KEYA_AUTHOR, info.getAuthor());
					values.put(ArticleProvider.KEYA_DATE, info.getDateString());
					values.put(ArticleProvider.KEYA_TITLE, info.getTitle());
					values.put(ArticleProvider.KEYA_THREAD, info.getThread());
					values.put(ArticleProvider.KEYA_BODY, info.getBody());
					boolean read = info.getRead();
					if (old != null && old.getRead()) {
						read = true;
					}
					values.put(ArticleProvider.KEYA_READ, read ? 1 : 0);

					boolean result = true;
					if (old == null) {
						// Not there...
						try {
							mResolver.insert(uri, values);
						} catch (SQLException e) {
							result = false;
						}
					} else {
						// Hmm... already there...
						if (info.getUser().equals(old.getUser())
								&& info.getDateString().equals(
										old.getDateString())
								&& info.getTitle().equals(old.getTitle())) {
							result = false;
						} else {
							try {
								mResolver.update(uri, values,
										ArticleProvider.SELECTION_SEQ, args);
							} catch (SQLException e) {
								result = false;
							}
						}
					}
					if (result) {
						++count;
						KidsBbs.announceBoardUpdated(UpdateService.this,
								_tabname);
					}
				}
				start = ((ArticleInfo) articles.get(articles.size() - 1))
						.getSeq() + 1;
				Log.d(TAG, _tabname + ": next from " + start);
			}
			final int trimmed = trimBoardTable(_tabname);
			Log.d(TAG, _tabname + ": trimed " + trimmed + " articles");
			if (count > 0) {
				notifyNewArticles(_tabname, count);
			}
			KidsBbs.announceBoardUpdated(UpdateService.this, _tabname);
			if (error > 0) {
				Log.e(TAG, _tabname + ": error after updating " + count
						+ " articles");
				KidsBbs.announceUpdateError(UpdateService.this);
			}
			setTableState(_tabname, ArticleProvider.STATE_SELECTED);
			return count;
		}

		private void notifyNewArticles(String _tabname, int _count) {
			if (!mNotificationOn) {
				return;
			}

			// Prepare pending intent for notification
			final String title = KidsBbs.getBoardTitle(mResolver, _tabname);
			final PendingIntent pendingIntent = PendingIntent.getActivity(
					UpdateService.this, 0, new Intent(UpdateService.this,
							BoardListActivity.class), 0);

			// Notify new articles.
			mNewArticlesNotification.tickerText = title + " (" + _count + ")";
			mNewArticlesNotification.when = System.currentTimeMillis();
			mNewArticlesNotification.defaults = mNotificationDefaults;
			if ((mNotificationDefaults & Notification.DEFAULT_LIGHTS) != 0) {
				mNewArticlesNotification.flags |= Notification.FLAG_SHOW_LIGHTS;
			} else {
				mNewArticlesNotification.flags &= ~Notification.FLAG_SHOW_LIGHTS;
			}
			//mNewArticlesNotification.number =
			//	KidsBbs.getTotalUnreadCount(mResolver);
			mNewArticlesNotification.setLatestEventInfo(UpdateService.this,
					mNotificationTitleString, mNotificationMessage,
					pendingIntent);

			mNotificationManager.notify(KidsBbs.NOTIFICATION_NEW_ARTICLE,
					mNewArticlesNotification);
		}

		private int refreshTables(String _tabname) {
			final String[] FIELDS = { ArticleProvider.KEYB_TABNAME };
			final String WHERE = ArticleProvider.KEYB_STATE + "!="
					+ ArticleProvider.STATE_PAUSED;
			final String ORDERBY = ArticleProvider.ORDER_BY_STATE_ASC + ","
					+ ArticleProvider.ORDER_BY_ID;

			int total_count = 0;
			final ArrayList<String> tabnames = new ArrayList<String>();
			if (TextUtils.isEmpty(_tabname)) {
				// Get all the boards...
				final Cursor c = mResolver.query(
						ArticleProvider.CONTENT_URI_BOARDS, FIELDS, WHERE, null,
						ORDERBY);
				if (c != null) {
					if (c.getCount() > 0) {
						c.moveToFirst();
						do {
							tabnames.add(c.getString(c
									.getColumnIndex(ArticleProvider.KEYB_TABNAME)));
						} while (c.moveToNext());
					}
					c.close();
				}
			} else {
				tabnames.add(_tabname);
			}

			// Update each board in the list.
			int i = 0;
			int nTries = 0;
			while (i < tabnames.size()) {
				synchronized (mIsPausedSync) {
					while (mIsPaused) {
						try {
							mIsPausedSync.wait();
						} catch (Exception e) {
						}
					}
				}
				final String tabname = tabnames.get(i);
				try {
					final int count = refreshTable(tabname);
					Log.d(TAG, tabname + ": updated " + count + " articles");
					total_count += count;
					++i;
					nTries = 0;
				} catch (Exception e) {
					Log.d(TAG, tabname + ": exception while updating (#"
							+ nTries + ")");
					if (++nTries > 2) {
						break;
					}
				}
			}
			return total_count;
		}
	}

	private void refreshArticles(String _tabname) {
		if (mLastUpdate == null
				|| mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute(_tabname);
		}
	}

	private int deleteArticles(Uri _uri, Cursor _c, int _max) {
		final int col_index = _c.getColumnIndex(ArticleProvider.KEYA_SEQ);
		int count = 0;
		_c.moveToFirst();
		do {
			final int seq = _c.getInt(col_index);
			if (seq > 0) {
				count += mResolver.delete(_uri, ArticleProvider.SELECTION_SEQ,
						new String[] { Integer.toString(seq) });
			}
		} while (--_max > 0 && _c.moveToNext());
		return count;
	}

	private int trimBoardTable(String _tabname) {
		final String[] FIELDS = { ArticleProvider.KEYA_SEQ, };
		final String WHERE = "DATE(" + ArticleProvider.KEYA_DATE
				+ ")!='' AND JULIANDAY(" + ArticleProvider.KEYA_DATE
				+ ")<=JULIANDAY('now'," + KidsBbs.KST_DIFF + ","
				+ KidsBbs.MAX_TIME + ")";

		// At least 15...
		int size = KidsBbs.getBoardTableSize(mResolver, _tabname);
		if (size <= KidsBbs.MIN_ARTICLES) {
			return 0;
		}

		final Uri uri = Uri.parse(ArticleProvider.CONTENT_URISTR_LIST
				+ _tabname);

		// Find the trim point.
		int seq = 0;
		Cursor c = mResolver.query(uri, FIELDS, WHERE, null,
				ArticleProvider.ORDER_BY_SEQ_DESC);
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
			c = mResolver.query(uri, FIELDS, ArticleProvider.KEYA_SEQ + "<="
					+ seq, null, ArticleProvider.ORDER_BY_SEQ_ASC);
			if (c != null) {
				count += deleteArticles(uri, c, size - KidsBbs.MIN_ARTICLES);
				c.close();
			}
		}

		// Reduce the size to a manageable size.
		size = KidsBbs.getBoardTableSize(mResolver, _tabname);
		if (size > KidsBbs.MAX_ARTICLES) {
			c = mResolver.query(uri, FIELDS, null, null,
					ArticleProvider.ORDER_BY_SEQ_ASC);
			if (c != null) {
				count += deleteArticles(uri, c, size - KidsBbs.MAX_ARTICLES);
				c.close();
			}
		}

		return count;
	}

	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String tabname = _intent.getStringExtra(KidsBbs.PARAM_BASE
					+ ArticleProvider.KEYB_TABNAME);
			KidsBbs.updateBoardCount(mResolver, tabname);
		}
	}

	private ArticleUpdatedReceiver mUpdateReceiver;

	private class ConnectivityReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			final String action = _intent.getAction();
			if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				mNoConnectivity = _intent.getBooleanExtra(
						ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			} else if (action.equals(
					ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED)) {
				mBgDataEnabled = mConnectivities.getBackgroundDataSetting();
			} else {
				return;
			}
			final boolean isPaused = mNoConnectivity || !mBgDataEnabled;
			synchronized (mIsPausedSync) {
				if (isPaused == mIsPaused) {
					return;
				}
				mIsPaused = isPaused;
				Log.d(TAG, mIsPaused ? "Update DISABLED" : "Update ENABLED");
				if (!mIsPaused) {
					mIsPausedSync.notify();
				}
			}
		}
	}

	private ConnectivityReceiver mConnReceiver;

	private void registerReceivers() {
		IntentFilter filter;
		mConnReceiver = new ConnectivityReceiver();
		filter = new IntentFilter(
				ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED);
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mConnReceiver, filter);
		mUpdateReceiver = new ArticleUpdatedReceiver();
		filter = new IntentFilter(KidsBbs.ARTICLE_UPDATED);
		registerReceiver(mUpdateReceiver, filter);
	}

	private void unregisterReceivers() {
		unregisterReceiver(mConnReceiver);
		unregisterReceiver(mUpdateReceiver);
	}
}
