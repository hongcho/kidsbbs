// Copyright (c) 2010-2012, Younghong "Hong" Cho <hongcho@sori.org>.
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
package org.sori.kidsbbs.ui.preference;

import org.sori.kidsbbs.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class MainSettings extends PreferenceActivity {

	public interface PrefKey {
		String UPDATE_FREQ = "PREF_UPDATE_FREQ";
		String HIDE_READ = "PREF_HIDE_READ";
		String NOTIFICATION = "PREF_NOTIFICATION";
		String NOTIFICATION_LIGHTS = "PREF_NOTIFICATION_LIGHTS";
		String NOTIFICATION_SOUND = "PREF_NOTIFICATION_SOUND";
		String NOTIFICATION_VIBRATE = "PREF_NOTIFICATION_VIBRATE";
		String ABOUT_KIDSBBS = "PREF_ABOUT_KIDSBBS";
		String ABOUT_COPYRIGHTS = "PREF_ABOUT_COPYRIGHTS";
		String ABOUT_APP = "PREF_ABOUT_APP";
	}

	private interface DialogId {
		int ABOUT_APP = 0;
		int ABOUT_KIDSBBS = 1;
		int ABOUT_COPYRIGHTS = 2;
	}

	private LayoutInflater mInflater;

	private SharedPreferences mPrefs;
	private Preference mPrefNotification;
	private Preference mPrefNotificationLights;
	private Preference mPrefNotificationSound;
	private Preference mPrefNotificationVibrate;

	private static String DEFAULT_UPDATE_FREQ = null;
	private static String[] UPDATE_FREQ_OPTIONS = null;

	public static final String getDefaultUpdateFreq(final Context _context) {
		if (DEFAULT_UPDATE_FREQ == null) {
			DEFAULT_UPDATE_FREQ = _context.getResources().getString(
					R.string.default_update_freq);
		}
		return DEFAULT_UPDATE_FREQ;
	}
	
	private static final String getUpdateFreqString(final Context _context,
			final int _index) {
		Resources resources = _context.getResources();
		if (UPDATE_FREQ_OPTIONS == null) {
			UPDATE_FREQ_OPTIONS = resources.getStringArray(
					R.array.update_freq_options);
		}
		return UPDATE_FREQ_OPTIONS[_index];
	}

	private void notificationEnable(final boolean _on) {
		mPrefNotificationLights.setEnabled(_on);
		mPrefNotificationSound.setEnabled(_on);
		mPrefNotificationVibrate.setEnabled(_on);
	}

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		addPreferencesFromResource(R.xml.main_settings);

		mInflater = LayoutInflater.from(this);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(
				getApplicationContext());
		mPrefNotification = findPreference(PrefKey.NOTIFICATION);
		mPrefNotificationLights = findPreference(PrefKey.NOTIFICATION_LIGHTS);
		mPrefNotificationSound = findPreference(PrefKey.NOTIFICATION_SOUND);
		mPrefNotificationVibrate = findPreference(PrefKey.NOTIFICATION_VIBRATE);

		mPrefNotification.setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference _preference) {
						notificationEnable(mPrefs.getBoolean(
								PrefKey.NOTIFICATION, true));
						return true;
					}
				});

		notificationEnable(mPrefs.getBoolean(PrefKey.NOTIFICATION, true));

		findPreference(PrefKey.ABOUT_KIDSBBS).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference _preference) {
						showDialog(DialogId.ABOUT_KIDSBBS);
						return true;
					}
				});

		findPreference(PrefKey.ABOUT_COPYRIGHTS).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference _preference) {
						showDialog(DialogId.ABOUT_COPYRIGHTS);
						return true;
					}
				});

		findPreference(PrefKey.ABOUT_APP).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference _preference) {
						showDialog(DialogId.ABOUT_APP);
						return true;
					}
				});
		
		final ListPreference updateFreq =
				(ListPreference)findPreference(PrefKey.UPDATE_FREQ);
		final int freqIndex = updateFreq.findIndexOfValue(mPrefs.getString(
				PrefKey.UPDATE_FREQ,
				getDefaultUpdateFreq(getApplicationContext())));
		updateFreq.setSummary(getUpdateFreqString(getApplicationContext(),
				freqIndex));
	}

	private Dialog createAboutDialog(final int _id_title, final int _id_text) {
		final View v = mInflater.inflate(R.layout.about_dialog, null);
		((TextView) v.findViewById(R.id.about_text)).setText(_id_text);
		return new AlertDialog.Builder(this)
			.setTitle(_id_title)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setView(v)
			.setPositiveButton(android.R.string.ok, null)
			.create();
	}

	@Override
	protected Dialog onCreateDialog(int _id) {
		switch (_id) {
		case DialogId.ABOUT_KIDSBBS:
			return createAboutDialog(R.string.about_kidsbbs_title,
					R.string.about_kidsbbs_text);
		case DialogId.ABOUT_COPYRIGHTS:
			return createAboutDialog(R.string.about_copyrights_title,
					R.string.about_copyrights_text);
		case DialogId.ABOUT_APP:
			return createAboutDialog(R.string.about_app_title,
					R.string.about_app_text);
		}
		return null;
	}
}
