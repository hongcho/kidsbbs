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
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.sori.kidsbbs.KidsBbs.ParseMode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

public abstract class KidsBbsAList extends ListActivity implements
		ListView.OnScrollListener {
	protected static final int MENU_REFRESH = Menu.FIRST;
	protected static final int MENU_SHOW = Menu.FIRST + 1;
	protected static final int MENU_PREFERENCES = Menu.FIRST + 2;
	protected static final int MENU_LAST = MENU_PREFERENCES;

	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";

	private ArrayList<ArticleInfo> mList = new ArrayList<ArticleInfo>();
	private int mItemTotal = 0;
	
	private ContentResolver mCR;

	private String mUrlBaseString;
	private Uri mUri;
	private String mWhereBase;
	private ParseMode mMode;

	private String mBoardTitle;
	private String mBoardName;
	private String mBoardType;
	private String mTabname;

	private TextView mStatusView;
	
	private ContextMenu mContextMenu;

	private ErrUtils mErrUtils;
	private UpdateTask mLastUpdate = null;

	// First call setQueryBase(), and all refreshListCommon().
	abstract protected void refreshList();

	// Update title...
	abstract protected void updateTitle(String _extra);

	// Call showItemCommon() with custom parameters.
	abstract protected void showItem(int _index);

	protected final String getBoardTitle() {
		return mBoardTitle;
	}

	protected final ArticleInfo getItem(int _index) {
		return (ArticleInfo)getListView().getItemAtPosition(_index);
	}

	@Override
	public void onCreate(Bundle _state) {
		super.onCreate(_state);
		setContentView(R.layout.article_list);
		
		mErrUtils = new ErrUtils(this, R.array.err_strings);

		Uri data = getIntent().getData();
		mTabname = data.getQueryParameter(KidsBbs.PARAM_N_TABNAME);
		mBoardTitle = data.getQueryParameter(KidsBbs.PARAM_N_TITLE);
		String[] parsed = BoardInfo.parseTabname(mTabname);
		mBoardType = parsed[0];
		mBoardName = parsed[1];

		mStatusView = (TextView)findViewById(R.id.status);
		mStatusView.setVisibility(View.GONE);

		setListAdapter(new AListAdapter(this, R.layout.article_info_item,
				mList));
		getListView().setOnScrollListener(this);
		
		mCR = getContentResolver();

		mReceiverNew = new NewArticlesReceiver();
		IntentFilter filterNew = new IntentFilter(KidsBbs.NEW_ARTICLES);
		registerReceiver(mReceiverNew, filterNew);
		mReceiverUpdated = new ArticleUpdatedReceiver();
		IntentFilter filterUpdated = new IntentFilter(KidsBbs.ARTICLE_UPDATED);
		registerReceiver(mReceiverUpdated, filterUpdated);
	}

	@Override
	protected void onListItemClick(ListView _l, View _v, int _position,
			long _id) {
		super.onListItemClick(_l, _v, _position, _id);
		showItem(_position);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu _menu) {
		super.onCreateOptionsMenu(_menu);

		MenuItem itemUpdate = _menu.add(0, MENU_REFRESH, Menu.NONE,
				R.string.menu_refresh);
		itemUpdate.setIcon(getResources().getIdentifier(
				"android:drawable/ic_menu_refresh", null, null));
		itemUpdate.setShortcut('0', 'r');

		MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
				R.string.menu_preferences);
		itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
		itemPreferences.setShortcut('1', 'p');

		return true;
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
	
	protected final void setContextMenuTitle(String _title) {
		if (mContextMenu != null) {
			mContextMenu.setHeaderTitle(_title);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu _menu, View _v,
			ContextMenu.ContextMenuInfo _menuInfo) {
		super.onCreateOptionsMenu(_menu);

		mContextMenu = _menu;
		setContextMenuTitle(getResources().getString(
				R.string.alist_cm_header));
		mContextMenu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
	}

	@Override
	public boolean onContextItemSelected(MenuItem _item) {
		super.onContextItemSelected(_item);
		switch (_item.getItemId()) {
		case MENU_SHOW:
			showItem(((AdapterView.AdapterContextMenuInfo)
					_item.getMenuInfo()).position);
			return true;
		}
		return false;
	}

	private boolean isUpdating() {
		return mLastUpdate != null &&
			!mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	private class UpdateTask extends AsyncTask<String, Object, Integer> {
		private ArrayList<ArticleInfo> mTList = new ArrayList<ArticleInfo>();
		private int mStart = 0;
		private boolean mIsAppend = false;
		private int mTotalCount = 0;

		@Override
		protected void onPreExecute() {
			mStatusView.setText(getResources().getString(
					R.string.update_text));
			mStatusView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Integer doInBackground(String... _args) {
			int result = 0;
			String _urlString = _args[0];
			mStart = Integer.parseInt(_args[1]);
			mIsAppend = mStart > 0;
			if (mIsAppend) {
				_urlString += "&" + KidsBbs.PARAM_N_START + "=" + mStart;
			}
			HttpClient client = new DefaultHttpClient();
			HttpGet get = new HttpGet(_urlString);
			try {
				HttpResponse response = client.execute(get); // IOException
				HttpEntity entity = response.getEntity();
				if (entity == null) {
					// Do something?
				} else if (response.getStatusLine().getStatusCode() ==
						HttpStatus.SC_OK) {
					InputStream is = entity.getContent();
					DocumentBuilder db = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder(); // ParserConfigurationException
					
					// Parse the board list.
					Document dom = db.parse(is); // IOException, SAXException
					Element doc = dom.getDocumentElement();
					NodeList nl;
					Node n;
					
					nl = doc.getElementsByTagName("ITEMS");
					if (nl == null || nl.getLength() <= 0) {
						return ErrUtils.ERR_XMLPARSING;
					}
					Element items = (Element)nl.item(0);
					
					nl = items.getElementsByTagName("TOTALCOUNT");
					if (nl == null || nl.getLength() <= 0) {
						return ErrUtils.ERR_XMLPARSING;
					}
					n = ((Element)nl.item(0)).getFirstChild();
					mTotalCount = n != null ?
							Integer.parseInt(n.getNodeValue()) : 0;
							
					// Get a board item
					nl = items.getElementsByTagName("ITEM");
					if (nl != null && nl.getLength() > 0) {
						for (int i = 0; i < nl.getLength(); ++i) {
							ArticleInfo info = KidsBbs.parseArticle(
									mMode, mTabname, (Element)nl.item(i));
							if (info == null) {
								return ErrUtils.ERR_XMLPARSING;
							}
							info.setRead(getReadStatus(info));
							mTList.add(info);
							publishProgress(mStart + mTList.size());
						}
					}
				}
				result = mTList.size();
			} catch(IOException e) {
				result = ErrUtils.ERR_IO;
			} catch(ParserConfigurationException e) {
				result = ErrUtils.ERR_PARSER;
			} catch(SAXException e) {
				result = ErrUtils.ERR_SAX;
			}
			return result;
		}

		@Override
		protected void onProgressUpdate(Object... _args) {
			String text = getResources().getString(R.string.update_text);
			text += " (" + _args[0] + ")";
			mStatusView.setText(text);
		}

		@Override
		protected void onPostExecute(Integer _result) {
			if (_result >= 0) {
				mItemTotal = mTotalCount;
				updateView(mTList, mIsAppend);
			} else {
				mStatusView.setText(mErrUtils.getErrString(_result));
			}
		}
	}

	private void updateView(ArrayList<ArticleInfo> _list, boolean _append) {
		if (!_append) {
			mList.clear();
		}
		mList.addAll(_list);
		((AListAdapter)getListAdapter()).notifyDataSetChanged();

		mStatusView.setVisibility(View.GONE);
		updateTitle(" (" + mList.size() + "/" + mItemTotal + ")");
	}

	private void updateList(boolean _append) {
		if (mUrlBaseString != null && mUri != null && !isUpdating()) {
			mLastUpdate = new UpdateTask();
			mLastUpdate.execute(mUrlBaseString,
					Integer.toString(_append ? mList.size() : 0));
		}
	}

	protected final void setQueryBase(String _urlBase, String _urlExtra,
			String _uriBase, String _whereBase, ParseMode _mode) {
		mUrlBaseString = _urlBase +
				KidsBbs.PARAM_N_BOARD + "=" + mBoardName +
				"&" + KidsBbs.PARAM_N_TYPE + "=" + mBoardType +
				_urlExtra;
		mUri = Uri.parse(_uriBase + mTabname);
		mWhereBase = _whereBase;
		mMode = _mode;
	}

	protected final void refreshListCommon() {
		updateList(false);
	}

	protected final void showItemCommon(Context _from, Class<?> _to,
			String _base, String _extra) {
		String uriString = _base +
				KidsBbs.PARAM_N_TABNAME + "=" + mTabname +
				"&" + KidsBbs.PARAM_N_TITLE + "=" + mBoardTitle +
				_extra;
		Intent intent = new Intent(_from, _to);
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.setAction(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(uriString));
		startActivity(intent);
	}
    
    protected void showPreference() {
		startActivity(new Intent(this, Preferences.class));
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

	public void onScroll(AbsListView _v, int _first, int _nVisible,
			int _nTotal) {
		if (_nTotal > 0 && _first + _nVisible >= _nTotal - 1 &&
				_nTotal == mList.size() && mList.size() < mItemTotal) {
			updateList(true);
		}
	}

	public void onScrollStateChanged(AbsListView _v, int _state) {
	}

	private class SavedStates {
		ArrayList<ArticleInfo> list;
		int total;
	}

	// Saving state for rotation changes...
	public Object onRetainNonConfigurationInstance() {
		SavedStates save = new SavedStates();
		save.list = mList;
		save.total = mItemTotal;
		return save;
	}

	protected final void initializeStates() {
		SavedStates save = (SavedStates)getLastNonConfigurationInstance();
		if (save == null) {
			refreshList();
		} else {
			mItemTotal = save.total;
			updateView(save.list, false);
		}
	}
	
	private boolean getReadStatus(ArticleInfo _info) {
		switch (mMode) {
		case TLIST:
			return KidsBbs.getArticleRead(mCR, mUri, mWhereBase,
					new String[] { _info.getThread() }, _info);
		default:
			return KidsBbs.getArticleRead(mCR, mUri, mWhereBase,
					new String[] { Integer.toString(_info.getSeq()) }, _info);
		}
	}
	
	private boolean updateReadStatus(int _seq) {
		ArticleInfo info;
		for (int i = 0; i < mList.size(); ++i) {
			info = mList.get(i);
			if (info.getSeq() == _seq) {
				info.setRead(getReadStatus(info));
				mList.set(i, info);
				return true;
			}
		}
		return false;
	}
	
	private class NewArticlesReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			if (mTabname != null && tabname != null &&
					tabname.equals(mTabname)) {
				//refreshList();
			}
		}
	}
	
	private class ArticleUpdatedReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			String tabname = _intent.getStringExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYB_TABNAME);
			int seq = _intent.getIntExtra(
					KidsBbs.PARAM_BASE + KidsBbsProvider.KEYA_SEQ, -1);
			if (mTabname != null && tabname != null &&
					tabname.equals(mTabname) && seq != -1 &&
					updateReadStatus(seq)) {
				((AListAdapter)getListAdapter()).notifyDataSetChanged();
			}
		}
	}
	
	private NewArticlesReceiver mReceiverNew;
	private ArticleUpdatedReceiver mReceiverUpdated;
}
