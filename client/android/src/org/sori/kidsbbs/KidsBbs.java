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

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
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
import android.widget.AdapterView.OnItemClickListener;

public class KidsBbs extends Activity {
	static final public String PARAM_N_TITLE = "bt";
	static final public String PARAM_N_BOARD = "b";
	static final public String PARAM_N_TYPE = "t";
	static final public String PARAM_N_THREAD = "id";
	static final public String PARAM_N_USER = "u";
	static final public String PARAM_N_SEQ = "p";
	static final public String PARAM_N_START = "s";
	static final public String PARAM_N_COUNT = "n";
	
	static final public int ERR_BAD_URL = -1;
	static final public int ERR_IO = -2;
	static final public int ERR_PARSER = -3;
	static final public int ERR_SAX = -4;
	
	static final private int MENU_PREFERENCES = Menu.FIRST;
	static final private int MENU_SHOW = Menu.FIRST + 1;
	
	private static final int SHOW_PREFERENCES = 1;
	
	private static final String KEY_SELECTED_ITEM = "KEY_SELECTED_ITEM";
	
	private ArrayList<BoardInfo> mList = new ArrayList<BoardInfo>();
	private BListAdapter mAa;

	private ListView mListView;
	private TextView mStatusView;
	
	private UpdateTask mLastUpdate = null;
	
	private int mUpdateFreq = 0;
	
	private void setupNameMaps() {
 	}
	
    @Override
    public void onCreate(Bundle _state) {
        super.onCreate(_state);
        setContentView(R.layout.board_list);

        // Set up name map...
        setupNameMaps();

        mStatusView = (TextView)findViewById(R.id.status);
        mStatusView.setVisibility(View.GONE);
        
        mListView = (ListView)findViewById(R.id.list_view);
        mListView.setOnItemClickListener(new OnItemClickListener() {
        	public void onItemClick(AdapterView _av, View _v, int _index, long arg3) {
        		showItem(_index);
        	}
        });
        
        mAa = new BListAdapter(this, R.layout.board_info_item, mList);
        mListView.setAdapter(mAa);
        
        registerForContextMenu(mListView);
        updateFromPreferences();
        refreshList();
        restoreUIState();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu _menu) {
    	super.onCreateOptionsMenu(_menu);
    	
    	MenuItem itemPreferences = _menu.add(0, MENU_PREFERENCES, Menu.NONE,
    			R.string.menu_preferences);
    	itemPreferences.setIcon(android.R.drawable.ic_menu_preferences);
    	itemPreferences.setShortcut('0', 'p');
    	return true;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu _menu, View _v,
    		ContextMenu.ContextMenuInfo _menuInfo) {
    	super.onCreateOptionsMenu(_menu);
    	_menu.setHeaderTitle(getResources().getString(R.string.blist_cm_header));
    	_menu.add(0, MENU_SHOW, Menu.NONE, R.string.read_text);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
    	switch (item.getItemId()) {
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
    
    private class UpdateTask extends AsyncTask<Void, Integer, Integer> {
    	@Override
    	protected void onPreExecute() {
    		mStatusView.setVisibility(View.VISIBLE);
    	}
    	@Override
        protected Integer doInBackground(Void... _args) {
    	   	String[] tabnames = getResources().getStringArray(R.array.board_table_names);
        	String[] typeNames = getResources().getStringArray(R.array.board_type_names);
        	String[] nameMapKeys = getResources().getStringArray(R.array.board_name_map_in);
        	String[] nameMapValues = getResources().getStringArray(R.array.board_name_map_out);
    		HashMap<String,String> nameMap = new HashMap<String,String>();
        	for (int i = 0; i < nameMapKeys.length; ++i) {
        		nameMap.put(nameMapKeys[i], nameMapValues[i]);
        	}
        	for (int i = 0; i < tabnames.length; ++i) {
        		String[] p = BoardInfo.parseTabName(tabnames[i]);
        		int type = Integer.parseInt(p[0]);
        		String name = p[1];
        		String title;
        		if (type > 0 && type < typeNames.length) {
        			title = typeNames[type] + " ";
        		} else {
        			title = "";
        		}
        		String mapped = nameMap.get(name);
        		if (mapped != null) {
        			title += mapped; 
        		} else {
        			title += name;
        		}
        		mList.add(new BoardInfo(tabnames[i], title));
        	}
        	return tabnames.length;
        }
    	@Override
    	protected void onProgressUpdate(Integer... _args) {
    		String text = getResources().getString(R.string.update_text);
    		text += " (" + _args[0].toString() + ")";
    		mStatusView.setText(text);
    	}
    	@Override
        protected void onPostExecute(Integer _count) {
    		mAa.notifyDataSetChanged();
    		mStatusView.setVisibility(View.GONE);
            setTitle(getResources().getString(R.string.title_blist) +
            		" (" + _count.toString() + ")");
        }
    }
    
    private void refreshList() {
    	if (mLastUpdate == null ||
    			mLastUpdate.getStatus().equals(AsyncTask.Status.FINISHED)) {
    		mLastUpdate = new UpdateTask();
    		mLastUpdate.execute((Void[])null);
    	}
    }
    
    private void showItem(int _index) {
		BoardInfo info = mList.get(_index);
    	Uri data = Uri.parse(getResources().getString(R.string.intent_uri_tlist) +
    			PARAM_N_BOARD + "=" + info.getBoard() + 
    			"&" + PARAM_N_TYPE + "=" + info.getType() +
    			"&" + PARAM_N_TITLE + "=" + info.getTitle());
    	Intent i = new Intent(this, KidsBbsTlist.class);
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
    
    private void restoreUIState() {
    	//SharedPreferences prefs = getPreferences(Activity.MODE_PRIVATE);
    }
    
    @Override
    public void onSaveInstanceState(Bundle _state) {
    	_state.putInt(KEY_SELECTED_ITEM, mListView.getSelectedItemPosition());
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
    	mListView.setSelection(pos);
    }
}