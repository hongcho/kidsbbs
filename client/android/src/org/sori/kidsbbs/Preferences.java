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

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;

public class Preferences extends PreferenceActivity {
	public static final String PREF_UPDATE_FREQ = "PREF_UPDATE_FREQ";
	public static final String PREF_ABOUT_KIDSBBS = "PREF_ABOUT_KIDSBBS";
	public static final String PREF_ABOUT_APP = "PREF_ABOUT_APP";
	
	public static final String DEFAULT_UPDATE_FREQ = "15";
	
	private static final int ABOUT_APP_ID = 0;
	private static final int ABOUT_KIDSBBS_ID = 1;
	
	//private SharedPreferences prefs;
	private LayoutInflater mInflater;

	private String mAboutKidsbbsString;
	private String mAboutAppString;
	
	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		addPreferencesFromResource(R.xml.userpreferences);
		
		mInflater = LayoutInflater.from(this);
		
		findPreference(PREF_ABOUT_KIDSBBS).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						showDialog(ABOUT_KIDSBBS_ID);
						return true;
					}
				});
		
		findPreference(PREF_ABOUT_APP).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						showDialog(ABOUT_APP_ID);
						return true;
					}
				});
	}
	
	@Override
	protected Dialog onCreateDialog(int _id) {
		AlertDialog.Builder builder;
		View v;
		WebView tv;
		switch(_id) {
		case ABOUT_KIDSBBS_ID:
			mAboutKidsbbsString = getResources().getString(
					R.string.about_kidsbbs_text);
			builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.about_kidsbbs_title);
			v = mInflater.inflate(R.layout.about_dialog, null);
			builder.setView(v);
			tv = (WebView)v.findViewById(R.id.about_text);
			tv.loadData(mAboutKidsbbsString, "text/html", "utf-8");
			return builder.create();
		case ABOUT_APP_ID:
			mAboutAppString = getResources().getString(
					R.string.about_app_text);
			builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.about_app_title);
			v = mInflater.inflate(R.layout.about_dialog, null);
			builder.setView(v);
			tv = (WebView)v.findViewById(R.id.about_text);
			tv.loadData(mAboutAppString, "text/html", "utf-8");;
			return builder.create();
		}
		return null;
	}
}
