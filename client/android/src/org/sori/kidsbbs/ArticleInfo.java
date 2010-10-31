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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.sori.kidsbbs.KidsBbs.ParseMode;

public class ArticleInfo {
	public static final String DATE_INVALID = "0000-00-00 00:00:00";
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	private static final String DATESHORT_INVALID = "0000-00-00";

	private static final DateFormat mTimeFormat =
		DateFormat.getTimeInstance(DateFormat.SHORT);
	private static final DateFormat mDateFormat =
		DateFormat.getDateInstance(DateFormat.SHORT);
	private static final DateFormat mFullFormat =
		DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

	private String mTabname;
	private int mSeq;
	private String mAuthor;
	private String mUser;
	private String mTitle;
	private String mThread;
	private String mBody;
	private int mCount;
	private String mDateString;
	private String mDateShortString;
	private boolean mRead;

	public final String getTabname() { return mTabname; }
	public final int getSeq() { return mSeq; }
	public final String getAuthor() { return mAuthor; }
	public final String getUser() { return mUser; } 
	public final String getTitle() { return mTitle; }
	public final String getThread() { return mThread; }
	public final String getBody() { return mBody; }
	public final int getCount() { return mCount; }
	public final String getDateString() { return mDateString; }
	public final String getDateShortString() { return mDateShortString;}
	public final boolean getRead() { return mRead; }
	public final void setRead(boolean _read) { mRead = _read; }

	public static final Date toLocalDate(String _dateString) {
		if (_dateString == DATE_INVALID) {
			return null;
		} else {
			// Prepare date/time in the local time zone.
			DateFormat dfKorea = new SimpleDateFormat(DATE_FORMAT);
			dfKorea.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
			try {
				Date date = dfKorea.parse(_dateString);
				_dateString = mFullFormat.format(date);
				return mFullFormat.parse(_dateString);
			} catch (ParseException e) {
				return null;
			}
		}
	}
	
	public static final boolean isRecent(String _dateString) {
		boolean read = true;
		Date local = ArticleInfo.toLocalDate(_dateString);
		if (local != null) {
			Calendar calLocal = new GregorianCalendar();
			Calendar calRecent = new GregorianCalendar();
			calLocal.setTime(local);
			calRecent.setTime(new Date());
			// "Recent" one is marked unread.
			calRecent.add(Calendar.DATE, -7);
			if (calLocal.after(calRecent)) {
				read = false;
			}
		}
		return read;
	}

	public ArticleInfo(String _tabname, int _seq, String _user, String _author,
			String _dateString, String _title, String _thread, String _body,
			int _count, boolean _read) {
		mTabname = _tabname;
		mSeq = _seq;
		mUser = _user;
		mAuthor = _author;
		mTitle = _title;
		mThread = _thread;
		mCount = _count;
		mRead = _read;
		
		Date local = toLocalDate(_dateString);
		if (local == null) {
			mDateString = DATE_INVALID;
			mDateShortString = DATESHORT_INVALID;
		} else {
			mDateString = mFullFormat.format(local);
			Date now = new Date();
			if (now.getYear() == local.getYear()
					&& now.getMonth() == local.getMonth()
					&& now.getDate() == local.getDate()) {
				mDateShortString = mTimeFormat.format(local);
			} else {
				mDateShortString = mDateFormat.format(local);
			}
		}

		mBody = _body;
	}

	@Override
	public String toString() {
		return mTitle + " " + mAuthor + " " + mBody;
	}
}
