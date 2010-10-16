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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class KidsBbsView extends Activity {
	static final private int MENU_UPDATE = Menu.FIRST;
	static final private int MENU_PREFERENCES = Menu.FIRST + 1;
	
	private static final int SHOW_PREFERENCES = 1;
	
	private TextView mStatusView;
	private TextView mTitleView;
	private TextView mUserView;
	private TextView mDateView;
	private TextView mBodyView;

	private UpdateTask mLastUpdate = null;
	
	private int mUpdateFreq = 0;
	
	private ErrUtils mErrUtils;
	
	private String mBoardTitle;
	private String mBoardName;
	private String mBoardType;
	private String mBoardSeq;
	private String mBoardUser;
	
	private ArticleInfo mInfo = null;
	
    @Override
    public void onCreate(Bundle _state) {
        super.onCreate(_state);
        setContentView(R.layout.board_view);
        
        mErrUtils = new ErrUtils(this, R.array.err_strings);
        
        Uri data = getIntent().getData();
        mBoardName = data.getQueryParameter(KidsBbs.PARAM_N_BOARD);
        mBoardType = data.getQueryParameter(KidsBbs.PARAM_N_TYPE);
        mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
        mBoardSeq = data.getQueryParameter(KidsBbs.PARAM_N_SEQ);
        setTitle(mBoardSeq + " in [" + mBoardTitle + "]");
        
        mStatusView = (TextView)findViewById(R.id.status);
        mStatusView.setVisibility(View.GONE);
        
        mTitleView = (TextView)findViewById(R.id.title);
        mUserView = (TextView)findViewById(R.id.username);
        mDateView = (TextView)findViewById(R.id.date);
        mBodyView = (TextView)findViewById(R.id.body);
        
        mTitleView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startThreadView();
			}
		});
        mUserView.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				startUserView();
			}
		});
        
        initializeStates();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu _menu) {
    	super.onCreateOptionsMenu(_menu);
    	
    	MenuItem itemUpdate = _menu.add(0, MENU_UPDATE, Menu.NONE,
    			R.string.menu_refresh);
    	itemUpdate.setIcon(
    			getResources().getIdentifier("android:drawable/ic_menu_refresh",
    			null, null));
    	itemUpdate.setShortcut('0', 'r');
    	
    	MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
    			R.string.menu_preferences);
    	itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
    	itemUpdate.setShortcut('1', 'p');
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
    	switch (item.getItemId()) {
    	case MENU_UPDATE:
    		refreshView();
    		return true;
    	case MENU_PREFERENCES:
    		Intent i = new Intent(this, Preferences.class);
    		startActivityForResult(i, SHOW_PREFERENCES);
    		return true;
    	}
    	return false;
    }
    
    private boolean isUpdating() {
    	return mLastUpdate != null &&
    		!mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
    }
    
    private class UpdateTask extends AsyncTask<String, String, Integer> {
    	private String mTUser;
    	private ArticleInfo mTInfo;
    	
    	@Override
    	protected void onPreExecute() {
    		mStatusView.setVisibility(View.VISIBLE);
    	}
    	@Override
        protected Integer doInBackground(String... _args) {
    		int ret = 0;
        	try {
        		URL url = new URL(_args[0]);
        		HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
        		int responseCode = httpConnection.getResponseCode();
        		if (responseCode == HttpURLConnection.HTTP_OK) {
        			InputStream is = httpConnection.getInputStream();
        			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        			
        			// Parse the board list.
        			Document dom = db.parse(is);
        			Element docEle = dom.getDocumentElement();
        			
        			// Get a board item
        			NodeList nl = docEle.getElementsByTagName("ITEM");
        			if (nl != null && nl.getLength() > 0) {
        				NodeList nl2;
        				Node n2;
    					Element item = (Element)nl.item(0);
    					
    					nl2 = item.getElementsByTagName("THREAD");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			String thread = n2 != null ? n2.getNodeValue() : "";
	        			
	        			nl2 = item.getElementsByTagName("TITLE");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			String title = n2 != null ? n2.getNodeValue() : "";
	        			
	        			nl2 = item.getElementsByTagName("SEQ");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			int seq = n2 != null ? Integer.parseInt(n2.getNodeValue()) : 0;
	        			
	        			nl2 = item.getElementsByTagName("DATE");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			String date = n2 != null ? n2.getNodeValue() : "";
	        			
	        			nl2 = item.getElementsByTagName("USER");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			mTUser = n2 != null ? n2.getNodeValue() : "";
	        			
	        			nl2 = item.getElementsByTagName("AUTHOR");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			String author = n2 != null ? n2.getNodeValue() : "";
	        			
	        			nl2 = item.getElementsByTagName("DESCRIPTION");
	        			if (nl2 == null || nl2.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			n2 = ((Element)nl2.item(0)).getFirstChild();
	        			String desc = n2 != null ? n2.getNodeValue() : "";
    					
    					mTInfo = new ArticleInfo(seq, author, date, title, thread, desc, 1);
        			}
        		}
        	} catch (MalformedURLException e) {
        		ret = ErrUtils.ERR_BAD_URL;
        	} catch (IOException e) {
        		ret = ErrUtils.ERR_IO;
        	} catch (ParserConfigurationException e) {
        		ret = ErrUtils.ERR_PARSER;
        	} catch (SAXException e) {
        		ret = ErrUtils.ERR_SAX;
        	} finally {
        	}
        	return ret;
        }
    	@Override
        protected void onPostExecute(Integer _result) {
    		if (_result >= 0) {
    			mBoardUser = mTUser;
    			mInfo = mTInfo;
    			updateView();
    		} else {
				setTitle(mErrUtils.getErrString(_result));
    		}
        }
    }
    
    private void updateView() {
		mStatusView.setVisibility(View.GONE);
		mTitleView.setText(mInfo.getTitle());
		mUserView.setText(mInfo.getUsername());
		mDateView.setText(mInfo.getDateString());
		mBodyView.setText(mInfo.getBody());
    }
    
    private void refreshView() {
    	if (!isUpdating()) {
    		mLastUpdate = new UpdateTask();
    		mLastUpdate.execute(getString(R.string.url_view) +
        			KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
        			"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType +
        			"&" + KidsBbs.PARAM_N_SEQ + "=" + mBoardSeq);
    	}
    }
    
    private void startThreadView() {
    	if (mInfo != null) {
			Uri data = Uri.parse(getResources().getString(R.string.intent_uri_thread) +
					"&" + KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
					"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType +
					"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
					"&" + KidsBbs.PARAM_N_THREAD + "=" + mInfo.getThread());
			Intent i = new Intent(this, KidsBbsThread.class);
	    	i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    	i.setAction(Intent.ACTION_VIEW);
	    	i.setData(data);
	    	startActivity(i);
    	}
    }
    
    private void startUserView() {
		if (mBoardUser != null) {
			Uri data = Uri.parse(getResources().getString(R.string.intent_uri_user) +
					"&" + KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
					"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType +
					"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
					"&" + KidsBbs.PARAM_N_USER + "=" + mBoardUser);
			Intent i = new Intent(this, KidsBbsUser.class);
	    	i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    	i.setAction(Intent.ACTION_VIEW);
	    	i.setData(data);
	    	startActivity(i);
		}
    }
    
    private void updateFromPreferences() {
    	SharedPreferences prefs =
    		PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    	mUpdateFreq = Integer.parseInt(prefs.getString(Preferences.PREF_UPDATE_FREQ, "0"));
    }
    
    @Override
    public void onActivityResult(int _reqCode, int _resCode, Intent _data) {
    	super.onActivityResult(_reqCode, _resCode, _data);
    	if (_reqCode == SHOW_PREFERENCES) {
    		if (_resCode == Activity.RESULT_OK) {
    			updateFromPreferences();
    			//refreshBoardList();
    		}
    	}
    }
    
    private class SavedStates {
    	ArticleInfo info;
    	String user;
    	int updateFreq;
    }
    
    // Saving state for rotation changes...
    public Object onRetainNonConfigurationInstance() {
    	SavedStates save = new SavedStates();
    	save.info = mInfo;
    	save.user = mBoardUser;
    	save.updateFreq = mUpdateFreq;
        return save;
    }
    
    private void initializeStates() {
    	SavedStates save = (SavedStates)getLastNonConfigurationInstance();
    	if (save == null) {
    		updateFromPreferences();
    		refreshView();
    	} else {
    		mInfo = save.info;
    		mBoardUser = save.user;
    		mUpdateFreq = save.updateFreq;
    		updateView();
		}
    }
}