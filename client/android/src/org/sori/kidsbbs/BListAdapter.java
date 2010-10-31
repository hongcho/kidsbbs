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

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class BListAdapter extends ArrayAdapter<BoardInfo> {
	private int mResource;
	private LayoutInflater mInflater;
	
	public BListAdapter(Context _context, int _resource,
			List<BoardInfo> _items) {
		super(_context, _resource, _items);
		mResource = _resource;
		mInflater =
			(LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	private class ViewHolder {
		TextView title;
		TextView count;
	}
	
	@Override
	public View getView(int _position, View _convertView, ViewGroup _parent) {
		BoardInfo info = getItem(_position);
		String title = info.getTitle();
		int count = info.getUnreadCount();
		
		ViewHolder holder;
		if (_convertView == null) {
			_convertView = mInflater.inflate(mResource, _parent, false);
			holder = new ViewHolder();
			holder.title = (TextView)_convertView.findViewById(R.id.title);
			holder.count = (TextView)_convertView.findViewById(R.id.count);
			_convertView.setTag(holder);
		} else {
			holder = (ViewHolder)_convertView.getTag();
		}
		holder.title.setText(title);
		if (count > 0) {
			holder.count.setVisibility(View.VISIBLE);
			holder.count.setText(Integer.toString(count));
		} else {
			holder.count.setVisibility(View.GONE);
		}
		return _convertView;
	}
}
