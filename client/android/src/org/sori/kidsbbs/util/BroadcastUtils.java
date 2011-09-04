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
package org.sori.kidsbbs.util;

import org.sori.kidsbbs.KidsBbs.PackageBase;
import org.sori.kidsbbs.provider.ArticleDatabase.ArticleColumn;
import org.sori.kidsbbs.provider.ArticleDatabase.BoardColumn;

import android.content.Context;
import android.content.Intent;

public class BroadcastUtils {

	public interface BroadcastType {
		String BOARD_UPDATED = PackageBase.BCAST + "BoardUpdated";
		String ARTICLE_UPDATED = PackageBase.BCAST + "ArticleUpdated";
		String UPDATE_ERROR = PackageBase.BCAST + "UpdateError";
	}

	public static final void announceUpdateError(Context _context) {
		_context.sendBroadcast(new Intent(BroadcastType.UPDATE_ERROR));
	}

	public static final void announceBoardUpdated(Context _context,
			String _tabname) {
		DBUtils.updateBoardCount(_context.getContentResolver(), _tabname);

		final Intent intent = new Intent(BroadcastType.BOARD_UPDATED);
		intent.putExtra(PackageBase.PARAM + BoardColumn.TABNAME, _tabname);
		_context.sendBroadcast(intent);
	}

	public static void announceArticleUpdated(Context _context,
			String _tabname, int _seq, String _user, String _thread) {
		DBUtils.updateBoardCount(_context.getContentResolver(), _tabname);

		final Intent intent = new Intent(BroadcastType.ARTICLE_UPDATED);
		intent.putExtra(PackageBase.PARAM + BoardColumn.TABNAME, _tabname);
		intent.putExtra(PackageBase.PARAM + ArticleColumn.SEQ, _seq);
		intent.putExtra(PackageBase.PARAM + ArticleColumn.USER, _user);
		intent.putExtra(PackageBase.PARAM + ArticleColumn.THREAD, _thread);
		_context.sendBroadcast(intent);
	}
}
