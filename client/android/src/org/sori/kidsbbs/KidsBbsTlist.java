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
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

public class KidsBbsTlist extends ListActivity {
	static final private int MENU_UPDATE = Menu.FIRST;
	static final private int MENU_PREFERENCES = Menu.FIRST + 1;
	static final private int MENU_SHOW = Menu.FIRST + 2;
	
	private static final int SHOW_PREFERENCES = 1;
	
	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";
	
	private ArrayList<ArticleInfo> mList = new ArrayList<ArticleInfo>();
	private int mItemStart;
	private int mItemCount;
	private int mItemTotal;
	
	private TextView mStatusView;
	
	private ErrUtils mErrUtils;

	private UpdateTask mLastUpdate = null;
	
	private int mUpdateFreq = 0;
	
	private String mBoardTitle;
	private String mBoardName;
	private String mBoardType;
	
    @Override
    public void onCreate(Bundle _state) {
        super.onCreate(_state);
        setContentView(R.layout.article_list);
        
        mErrUtils = new ErrUtils(this, R.array.err_strings); 
        
        Uri data = getIntent().getData();
        mBoardName = data.getQueryParameter(KidsBbs.PARAM_N_BOARD);
        mBoardType = data.getQueryParameter(KidsBbs.PARAM_N_TYPE);
        mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
        setTitle("[" + mBoardTitle + "] " +
        		getResources().getString(R.string.title_tlist));
        
        mStatusView = (TextView)findViewById(R.id.status);
        mStatusView.setVisibility(View.GONE);
        
        setListAdapter(new AListAdapter(this, R.layout.article_info_item, mList));
        
        registerForContextMenu(getListView());
        updateFromPreferences();
        refreshList();
        restoreUIState();
    }
    
    protected void onListItemClick(ListView _l, View _v, int _position, long _id) {
    	showItem(_position);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu _menu) {
    	super.onCreateOptionsMenu(_menu);
    	
    	MenuItem itemUpdate = _menu.add(0, MENU_UPDATE, Menu.NONE,
    			R.string.menu_update);
    	itemUpdate.setIcon(
    			getResources().getIdentifier("android:drawable/ic_menu_refresh",
    			null, null));
    	itemUpdate.setShortcut('0', 'r');
    	
    	MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
    			R.string.menu_preferences);
    	itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
    	itemPreferences.setShortcut('1', 'p');
    	return true;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu _menu, View _v,
    		ContextMenu.ContextMenuInfo _menuInfo) {
    	super.onCreateOptionsMenu(_menu);
    	
    	_menu.setHeaderTitle(getResources().getString(R.string.tlist_cm_header));
    	_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
    	switch (item.getItemId()) {
    	case MENU_UPDATE:
    		refreshList();
    		return true;
    	case MENU_PREFERENCES:
    		Intent i = new Intent(this, Preferences.class);
    		startActivityForResult(i, SHOW_PREFERENCES);
    		return true;
    	}
    	return false;
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem _item) {
    	super.onContextItemSelected(_item);
    	switch (_item.getItemId()) {
    	case MENU_SHOW:
    		AdapterView.AdapterContextMenuInfo menuInfo =
    			(AdapterView.AdapterContextMenuInfo)_item.getMenuInfo();
    		showItem(menuInfo.position);
    		return true;
    	}
    	return false;
    }
    
    private class UpdateTask extends AsyncTask<String, Integer, Integer> {
    	@Override
    	protected void onPreExecute() {
    		mStatusView.setVisibility(View.VISIBLE);
    	}
    	@Override
        protected Integer doInBackground(String... _args) {
        	int count = 0;
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
        			
        			// Clear the old list.
        			mList.clear();
        			mItemStart = 0;
        			mItemCount = 0;
        			mItemTotal = 0;
        			
        			Element items = (Element)docEle.getElementsByTagName("ITEMS").item(0);
        			Element eStart = (Element)items.getElementsByTagName("START").item(0);
        			Element eNum = (Element)items.getElementsByTagName("COUNT").item(0);
        			Element eTotal = (Element)items.getElementsByTagName("TOTALCOUNT").item(0);
        			
        			mItemStart = Integer.parseInt(eStart.getFirstChild().getNodeValue());
        			mItemCount = Integer.parseInt(eNum.getFirstChild().getNodeValue());
        			mItemTotal = Integer.parseInt(eTotal.getFirstChild().getNodeValue());
        			
        			// Get a board item
        			NodeList nl = items.getElementsByTagName("ITEM");
        			if (nl != null && nl.getLength() > 0) {
        				for (int i = 0; i < nl.getLength(); ++i) {
        					Element item = (Element)nl.item(i);
        					Element eThread = (Element)item.getElementsByTagName("THREAD").item(0);
        					Element eCount = (Element)item.getElementsByTagName("COUNT").item(0);
        					Element eTitle = (Element)item.getElementsByTagName("TITLE").item(0);
        					Element eSeq = (Element)item.getElementsByTagName("SEQ").item(0);
        					Element eDate = (Element)item.getElementsByTagName("DATE").item(0);
        					Element eUser = (Element)item.getElementsByTagName("USER").item(0);
        					Element eDesc = (Element)item.getElementsByTagName("DESCRIPTION").item(0);

        					String thread = eThread.getFirstChild().getNodeValue();
        					int cnt = Integer.parseInt(eCount.getFirstChild().getNodeValue());
        					int seq = Integer.parseInt(eSeq.getFirstChild().getNodeValue());
        					String title = eTitle.getFirstChild().getNodeValue();
        					String date = eDate.getFirstChild().getNodeValue();
        					String user = eUser.getFirstChild().getNodeValue();
        					String desc = eDesc.getFirstChild().getNodeValue();
        					
        					mList.add(new ArticleInfo(seq, user, date, title, thread, desc, cnt));
        					++count;
        					if (count % 10 == 0) {
        						publishProgress(count);
        					}
        				}
        			}
        		}
        	} catch (MalformedURLException e) {
        		count = KidsBbs.ERR_BAD_URL;
        	} catch (IOException e) {
        		count = KidsBbs.ERR_IO;
        	} catch (ParserConfigurationException e) {
        		count = KidsBbs.ERR_PARSER;
        	} catch (SAXException e) {
        		count = KidsBbs.ERR_SAX;
        	} finally {
        	}
        	return count;
        }
    	@Override
    	protected void onProgressUpdate(Integer... _args) {
    		String text = getResources().getString(R.string.update_text);
    		text += " (" + _args[0].toString() + ")";
    		mStatusView.setText(text);
    	}
    	@Override
        protected void onPostExecute(Integer _count) {
			((AListAdapter)KidsBbsTlist.this.getListAdapter()).notifyDataSetChanged();
			if (_count >= 0) {
	    		mStatusView.setVisibility(View.GONE);
	    		setTitle("[" + mBoardTitle + "] " +
	            		getResources().getString(R.string.title_tlist) +
	            		" (" + Integer.toString(mItemStart + mItemCount) + "/" +
	            		Integer.toString(mItemTotal) + ")");
			} else {
				mStatusView.setText(mErrUtils.getErrString(_count));
			}
        }
    }
    
    private void refreshList() {
    	if (mLastUpdate == null ||
    			mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
    		mLastUpdate = new UpdateTask();
    		mLastUpdate.execute(getString(R.string.url_tlist) +
        			KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
        			"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType);
    	}
    }
    
    private void showItem(int _index) {
		ArticleInfo info = mList.get(_index);
		String uriString;
		String uriParamBase = KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
			"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType +
			"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle;
		Intent i;
		if (info.getCount() > 1) {
			uriString = getResources().getString(R.string.intent_uri_thread) +
				uriParamBase +
				"&" + KidsBbs.PARAM_N_THREAD + "=" + info.getThread();
			i = new Intent(this, KidsBbsThread.class);
		} else {
			uriString = getResources().getString(R.string.intent_uri_view) +
				uriParamBase +
				"&" + KidsBbs.PARAM_N_SEQ + "=" + Integer.toString(info.getSeq());
			i = new Intent(this, KidsBbsView.class);
		}
		Uri data = Uri.parse(uriString);
    	i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    	i.setAction(Intent.ACTION_VIEW);
    	i.setData(data);
    	startActivity(i);
    }
    
    private void updateFromPreferences() {
    	SharedPreferences prefs =
    		PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    	mUpdateFreq = Integer.parseInt(prefs.getString(Preferences.PREF_UPDATE_FREQ, "0"));
    }
    
    private void restoreUIState() {
    	//SharedPreferences prefs = getPreferences(Activity.MODE_PRIVATE);
    }
    
    @Override
    public void onSaveInstanceState(Bundle _state) {
    	_state.putInt(KEY_SELECTED_ITEM, getSelectedItemPosition());
    	super.onSaveInstanceState(_state);
    }
    
    @Override
    public void onRestoreInstanceState(Bundle _state) {
    	int pos = -1;
    	if (_state != null) {
    		if (_state.containsKey(KEY_SELECTED_ITEM)) {
    			pos = _state.getInt(KEY_SELECTED_ITEM, -1);
    		}
    	}
    	setSelection(pos);
    }
}
