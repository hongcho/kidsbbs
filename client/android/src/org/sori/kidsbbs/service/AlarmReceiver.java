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

import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.ui.preference.MainSettings;
import org.sori.kidsbbs.ui.preference.MainSettings.PrefKey;
import org.sori.kidsbbs.util.DBUtils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

public class AlarmReceiver extends BroadcastReceiver {
	public static final String UPDATE_BOARDS_ALARM =
		PackageBase.ALARM + "UpdateBoards";

	@Override
	public void onReceive(Context _context, Intent _intent) {
		// Start server from alarm...
		DBUtils.updateBoardTable(_context, "");
	}
	
	public static final PendingIntent getPendingIntent(Context _context) {
		return PendingIntent.getBroadcast(
				_context,
				0,
				new Intent(AlarmReceiver.UPDATE_BOARDS_ALARM),
				0);
	}
	
	public static final void setupAlarm(Context _context, final long _delay) {
		final SharedPreferences prefs =
				PreferenceManager.getDefaultSharedPreferences(_context);
		final int freq =
				Integer.parseInt(prefs.getString(PrefKey.UPDATE_FREQ,
						MainSettings.getDefaultUpdateFreq(_context)));
		final PendingIntent intent = AlarmReceiver.getPendingIntent(_context);
		AlarmReceiver.setupAlarm(_context, freq, _delay, intent);
	}

	public static final void setupAlarm(Context _context, final long _period,
			final long _delay, PendingIntent _pendingIntent) {
		AlarmManager alarmManager =
				(AlarmManager) _context.getSystemService(Context.ALARM_SERVICE);
		if (_period > 0) {
			final long msPeriod = _period * 60 * 1000;
			final long msDelay = ((_period < _delay) ? _period : _delay) * 60 * 1000;
			alarmManager.setRepeating(
					AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime() + msDelay,
					msPeriod,
					_pendingIntent);
		} else {
			alarmManager.cancel(_pendingIntent);
		}
	}
}
