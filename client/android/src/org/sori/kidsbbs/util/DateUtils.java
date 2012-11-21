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
package org.sori.kidsbbs.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.sori.kidsbbs.KidsBbs.Settings;

import android.util.Log;

public class DateUtils {
	private static final String TAG = "DateUtils";

	private static final String DATE_INVALID = "0000-00-00 00:00:00";
	private static final String DATESHORT_INVALID = "0000-00-00";
	// private static final String DATE_FORMAT = "MMM dd, yyyy h:mm:ss aa";
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

	private static final DateFormat DF_TIME =
		DateFormat.getTimeInstance(DateFormat.SHORT);
	private static final DateFormat DF_DATE =
		DateFormat.getDateInstance(DateFormat.SHORT);
	private static final DateFormat DF_FULL =
		DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
	private static final DateFormat DF_MEDIUM =
		DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
	private static final DateFormat DF_KIDS;
	static {
		DF_KIDS = new SimpleDateFormat(DATE_FORMAT, Locale.US);
		DF_KIDS.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static final Date KidsToLocalDate(final String _dateString) {
		try {
			return DF_FULL.parse(KidsToLocalDateString(_dateString));
		} catch (Exception e) {
			return null;
		}
	}

	public static final String GetShortDateString(final String _dateString) {
		try {
			final Date local = DF_FULL.parse(_dateString);
			final Calendar calLocal = new GregorianCalendar();
			final Calendar calNow = Calendar.getInstance();
			calLocal.setTime(local);
			if (calNow.get(Calendar.YEAR) == calLocal.get(Calendar.YEAR)
					&& calNow.get(Calendar.DAY_OF_YEAR) == calLocal.get(Calendar.DAY_OF_YEAR)) {
				return DF_TIME.format(local);
			} else {
				return DF_DATE.format(local);
			}
		} catch (Exception e) {
			return DATESHORT_INVALID;
		}
	}
	
	public static final String GetLongDateString(final String _dateString) {
		try {
			final Date local = DF_FULL.parse(_dateString);
			return DF_MEDIUM.format(local);
		} catch (Exception e) {
			return DATE_INVALID;
		}
	}

	public static final String KidsToLocalDateString(final String _dateString) {
		try {
			Date date = DF_KIDS.parse(_dateString);
			return DF_FULL.format(date);
		} catch (Exception e) {
			return DATE_INVALID;
		}
	}

	public static final String LocalToKidsDateString(final String _dateString) {
		try {
			final Date date = DF_FULL.parse(_dateString);
			return DF_KIDS.format(date);
		} catch (Exception e) {
			return DATE_INVALID;
		}
	}

	public static final boolean isRecent(final String _dateString) {
		boolean result = true;
		final Date local = DateUtils.KidsToLocalDate(_dateString);
		if (local != null) {
			final Calendar calLocal = new GregorianCalendar();
			final Calendar calRecent = new GregorianCalendar();
			calLocal.setTime(local);
			calRecent.setTime(new Date());
			// "Recent" one is marked unread.
			calRecent.add(Calendar.DATE, -Settings.MAX_DAYS);
			if (calLocal.before(calRecent)) {
				result = false;
			}
		} else {
			Log.e(TAG, "isRecent: parsing failed: " + _dateString);
		}
		return result;
	}
}
