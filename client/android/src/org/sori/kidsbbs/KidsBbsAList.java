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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public abstract class KidsBbsAList extends ListActivity
								implements ListView.OnScrollListener {
		protected static final int MENU_REFRESH = Menu.FIRST;
		protected static final int MENU_SHOW = Menu.FIRST + 1;
		protected static final int MENU_PREFERENCES = Menu.FIRST + 2;
		protected static final int MENU_LAST = MENU_PREFERENCES;
		
		protected static final int SHOW_PREFERENCES = 1;
		
		private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";
		
		private ArrayList<ArticleInfo> mList = new ArrayList<ArticleInfo>();
		private  int mItemStart;
		private  int mItemCount;
		private int mItemTotal;
		
		private String mBoardTitle;
		private String mBoardName;
		private String mBoardType;
		
		private ListView mListView;
		private TextView mStatusView;
		
		private ErrUtils mErrUtils;
		private UpdateTask mLastUpdate = null;
	    
	    // Call refreshListCommon() with custom parameters.
	    abstract protected void refreshList();
	    
	    // Update title...
	    abstract protected void updateTitle(String _extra);
	    
	    // Call showItemCommon() with custom parameters.
	    abstract protected void showItem(int _index);
	    
	    // Handle preference stuff.
	    abstract protected void showPreference();
	    
	    protected String getBoardTitle() { return mBoardTitle; }
	    protected ArticleInfo getItem(int _index) { return mList.get(_index); }
		
	    @Override
	    public void onCreate(Bundle _state) {
	        super.onCreate(_state);
	        setContentView(R.layout.article_list);
	        
	        mErrUtils = new ErrUtils(this, R.array.err_strings); 
	        
	        Uri data = getIntent().getData();
	        mBoardName = data.getQueryParameter(KidsBbs.PARAM_N_BOARD);
	        mBoardType = data.getQueryParameter(KidsBbs.PARAM_N_TYPE);
	        mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
	        
	        mListView = getListView();
	        mStatusView = (TextView)findViewById(R.id.status);
	        mStatusView.setVisibility(View.GONE);
	        
	        setListAdapter(new AListAdapter(this, R.layout.article_info_item, mList));
	        mListView.setOnScrollListener(this);
	    }
	    
	    @Override
	    protected void onListItemClick(ListView _l, View _v, int _position, long _id) {
	    	super.onListItemClick(_l, _v, _position, _id);
	    	showItem(_position);
	    }
	    
	    @Override
	    public boolean onCreateOptionsMenu(Menu _menu) {
	    	super.onCreateOptionsMenu(_menu);
	    	
	    	MenuItem itemUpdate = _menu.add(0, MENU_REFRESH, Menu.NONE,
	    			R.string.menu_refresh);
	    	itemUpdate.setIcon(
	    			getResources().getIdentifier("android:drawable/ic_menu_refresh",
	    			null, null));
	    	itemUpdate.setShortcut('0', 'r');
	    	
	    	MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
	    			R.string.menu_preferences);
	    	itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
	    	itemPreferences.setShortcut('1', 'p');
	    	
	    	return false;
	    }
	    
	    @Override
	    public boolean onOptionsItemSelected(MenuItem item) {
	    	super.onOptionsItemSelected(item);
	    	switch (item.getItemId()) {
	    	case MENU_REFRESH:
	    		refreshList();
	    		return true;
	    	case MENU_PREFERENCES:
	    		showPreference();
	    		return true;
	    	}
	    	return false;
	    }
	    
	    @Override
	    public void onCreateContextMenu(ContextMenu _menu, View _v,
	    		ContextMenu.ContextMenuInfo _menuInfo) {
	    	super.onCreateOptionsMenu(_menu);
	    	
	    	_menu.setHeaderTitle(getResources().getString(R.string.alist_cm_header));
	    	_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
	    }
	    
	    @Override
	    public boolean onContextItemSelected(MenuItem _item) {
	    	super.onContextItemSelected(_item);
	    	switch (_item.getItemId()) {
	    	case MENU_SHOW:
	    		showItem(((AdapterView.AdapterContextMenuInfo)_item.getMenuInfo()).position);
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
	        			NodeList nl;
	        			
	        			// Clear the old list.
	        			mList.clear();
	        			mItemStart = 0;
	        			mItemCount = 0;
	        			mItemTotal = 0;
	        			
	        			nl = docEle.getElementsByTagName("ITEMS");
	        			if (nl == null || nl.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			Element items = (Element)nl.item(0);
	        			
	        			nl = items.getElementsByTagName("START");
	        			if (nl == null || nl.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			mItemStart = Integer.parseInt(((Element)nl.item(0)).getFirstChild().getNodeValue());
	        			
	        			nl = items.getElementsByTagName("COUNT");
	        			if (nl == null || nl.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			mItemCount = Integer.parseInt(((Element)nl.item(0)).getFirstChild().getNodeValue());
	        			
	        			nl = items.getElementsByTagName("TOTALCOUNT");
	        			if (nl == null || nl.getLength() <= 0) {
	        				return ErrUtils.ERR_XMLPARSING;
	        			}
	        			mItemTotal = Integer.parseInt(((Element)nl.item(0)).getFirstChild().getNodeValue());
	        			
	        			// Get a board item
	        			nl = items.getElementsByTagName("ITEM");
	        			if (nl != null && nl.getLength() > 0) {
	        				for (int i = 0; i < nl.getLength(); ++i) {
	        					NodeList nl2;
	        					Element item = (Element)nl.item(i);
	        					
	        					String thread;
	        					nl2 = item.getElementsByTagName("THREAD");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				thread = "";
	    	        			} else {
	    	        				thread = ((Element)nl2.item(0)).getFirstChild().getNodeValue();
	    	        			}
	    	        			
	    	        			int cnt;
	    	        			nl2 = item.getElementsByTagName("COUNT");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				cnt = 1;
	    	        			} else {
	    	        				cnt = Integer.parseInt(((Element)nl2.item(0)).getFirstChild().getNodeValue());
	    	        			}
	    	        			
	    	        			nl2 = item.getElementsByTagName("TITLE");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				return ErrUtils.ERR_XMLPARSING;
	    	        			}
	    	        			String title =
	    	        				((Element)nl2.item(0)).getFirstChild().getNodeValue();
	    	        			
	    	        			nl2 = item.getElementsByTagName("SEQ");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				return ErrUtils.ERR_XMLPARSING;
	    	        			}
	    	        			int seq =
	    	        				Integer.parseInt(((Element)nl2.item(0)).getFirstChild().getNodeValue());
	    	        			
	    	        			nl2 = item.getElementsByTagName("DATE");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				return ErrUtils.ERR_XMLPARSING;
	    	        			}
	    	        			String date =
	    	        				((Element)nl2.item(0)).getFirstChild().getNodeValue();
	    	        			
	    	        			nl2 = item.getElementsByTagName("USER");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				return ErrUtils.ERR_XMLPARSING;
	    	        			}
	    	        			String user =
	    	        				((Element)nl2.item(0)).getFirstChild().getNodeValue();
	    	        			
	    	        			nl2 = item.getElementsByTagName("DESCRIPTION");
	    	        			if (nl2 == null || nl2.getLength() <= 0) {
	    	        				return ErrUtils.ERR_XMLPARSING;
	    	        			}
	    	        			String desc =
	    	        				((Element)nl2.item(0)).getFirstChild().getNodeValue();
	        					
	        					mList.add(new ArticleInfo(seq, user, date, title, thread, desc, cnt));
	        					++count;
	        					if (count % 10 == 0) {
	        						publishProgress(count);
	        					}
	        				}
	        			}
	        		}
	        	} catch (MalformedURLException e) {
	        		count = ErrUtils.ERR_BAD_URL;
	        	} catch (IOException e) {
	        		count = ErrUtils.ERR_IO;
	        	} catch (ParserConfigurationException e) {
	        		count = ErrUtils.ERR_PARSER;
	        	} catch (SAXException e) {
	        		count = ErrUtils.ERR_SAX;
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
				((AListAdapter)KidsBbsAList.this.getListAdapter()).notifyDataSetChanged();
				if (_count >= 0) {
		    		mStatusView.setVisibility(View.GONE);
		    		updateTitle(" (" + Integer.toString(mItemStart + mItemCount) +
		    				"/" + Integer.toString(mItemTotal) + ")");
				} else {
					mStatusView.setText(mErrUtils.getErrString(_count));
				}
	        }
	    }
	    
	    protected void refreshListCommon(int _base, String _extra) {
	    	if (mLastUpdate == null ||
	    			mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
	    		String urlString = getString(_base) +
	    			KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
	    			"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType
	    			+ _extra;
	    		Toast.makeText(this, urlString, Toast.LENGTH_LONG);
	    		mLastUpdate = new UpdateTask();
	    		mLastUpdate.execute(urlString);
	    	}
	    }
	    
	    protected void showItemCommon(Context _from, Class<?> _to,
	    		int _base, String _extra) {
	    	String uriString = getResources().getString(_base) +
	    		KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
	    		"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType +
	    		"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
	    		_extra;
    		Toast.makeText(this, uriString, Toast.LENGTH_LONG);
	    	Intent intent = new Intent(_from, _to); 
	    	intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    	intent.setAction(Intent.ACTION_VIEW);
	    	intent.setData(Uri.parse(uriString));
	    	startActivity(intent);
	    }
	    
	    @Override
	    public void onSaveInstanceState(Bundle _state) {
	    	super.onSaveInstanceState(_state);
	    	_state.putInt(KEY_SELECTED_ITEM, getSelectedItemPosition());
	    }
	    
	    @Override
	    public void onRestoreInstanceState(Bundle _state) {
	    	super.onRestoreInstanceState(_state);
	    	int pos = -1;
	    	if (_state != null) {
	    		if (_state.containsKey(KEY_SELECTED_ITEM)) {
	    			pos = _state.getInt(KEY_SELECTED_ITEM, -1);
	    		}
	    	}
	    	setSelection(pos);
	    }
	    
	    public void onScroll(AbsListView _v, int _first, int _nVisible, int _nTotal) {
	    	if (_nTotal > 0 && _first + _nVisible >= _nTotal) {
	    		Toast.makeText(this, "At the End", Toast.LENGTH_SHORT).show();
	    	}
	    }
	    
	    public void onScrollStateChanged(AbsListView _v, int _state) {
	    }
}
