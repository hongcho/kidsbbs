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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;

public class KidsBbsService extends Service {
	
	private UpdateTask mLastUpdate = null;
	
	@Override
	public void onCreate() {
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
			int result = 0;
			String _urlString = _args[0];
			try {
				URL url = new URL(_urlString);
				HttpURLConnection httpConnection =
					(HttpURLConnection)url.openConnection();
			} catch (MalformedURLException e) {
				result = ErrUtils.ERR_BAD_URL;
			} catch (IOException e) {
				result = ErrUtils.ERR_IO;
			} finally {
			}
			return result;
		}
		
		@Override
		protected void onProgressUpdate(ArticleInfo... _infos) {
		}
		
		@Override
		protected void onPostExecute(Integer _result) {
			stopSelf();
		}
	}
	
	private static final String[] FIELDS1 = {
		KidsBbsProvider.KEY_ID,
		KidsBbsProvider.KEYA_SEQ,
		KidsBbsProvider.KEYA_AUTHOR,
		KidsBbsProvider.KEYA_DATE,
		KidsBbsProvider.KEYA_TITLE,
	};
	
	private boolean addArticle(String _tabname, ArticleInfo _info) {
		boolean result = true;
		Uri uri = Uri.parse(KidsBbsProvider.CONTENT_URISTR_LIST + _tabname);
		ContentResolver cr = getContentResolver();
		String where = KidsBbsProvider.KEYA_SEQ + "=" + _info.getSeq();
		Cursor c = cr.query(uri, FIELDS1, where, null, null);
		if (c.getCount() == 0) {
			ContentValues values = new ContentValues();
			values.put(KidsBbsProvider.KEYA_SEQ, _info.getSeq());
			values.put(KidsBbsProvider.KEYA_AUTHOR, _info.getUsername());
			values.put(KidsBbsProvider.KEYA_DATE, _info.getDateString());
			values.put(KidsBbsProvider.KEYA_TITLE, _info.getTitle());
			values.put(KidsBbsProvider.KEYA_THREAD, _info.getThread());
			values.put(KidsBbsProvider.KEYA_BODY, _info.getBody());
			values.put(KidsBbsProvider.KEYA_READ, _info.getRead());
			cr.insert(uri, values);
		} else {
			result = false;
		}
		c.close();
		return result;
	}
	
	private void refreshArticles(String _board, int _type, int _start) {
		if (mLastUpdate == null ||
				mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute(KidsBbs.URL_LIST +
					KidsBbs.PARAM_N_BOARD + "=" + _board +
					"&" + KidsBbs.PARAM_N_TYPE + "=" + _type +
					"&" + KidsBbs.PARAM_N_START + "=" + _start);
		}
	}
	
	private void refreshBoards() {
		
	}
}
