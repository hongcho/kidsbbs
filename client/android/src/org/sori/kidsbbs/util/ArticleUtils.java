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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sori.kidsbbs.data.ArticleInfo;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.util.Log;

public class ArticleUtils {
	private static final String TAG = "ArticleUtils";

	public static final String getThreadTitle(final String _s) {
		if (_s != null && _s.length() > 0) {
			final Pattern PATTERN =
				Pattern.compile("^Re:\\s*", Pattern.CASE_INSENSITIVE);
			final Matcher m = PATTERN.matcher(_s);
			if (m.find()) {
				return m.replaceFirst("");
			}
		}
		return _s;
	}

	public static final String generateSummary(String _s) {
		if (_s == null) {
			return null;
		}
		// Remove quoted texts
		final Pattern[] P_QUOTES = {
				Pattern.compile(
						"^\\s*\\d{4}\\S+ \\d{2}\\S+ \\d{2}\\S+ \\(\\S+\\)"
						+ " \\S+ \\d{2}\\S+ \\d{2}\\S+ \\d{2}\\S+"
						+ " .+:\\s*$",
						Pattern.MULTILINE),
				Pattern.compile("^\\s*>.*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*-{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*={3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*_{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*/{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*#{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\*{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\+{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\.{3,}\\s*$", Pattern.MULTILINE),
				Pattern.compile("^\\s*\\\\{3,}\\s*$", Pattern.MULTILINE),
		};
		for (int i = 0; i < P_QUOTES.length; ++i) {
			Matcher m = P_QUOTES[i].matcher(_s);
			_s = m.replaceAll("");
		}
		// Remove white spaces.
		final Pattern[] P_SPACES = {
				Pattern.compile("\n+"),
				Pattern.compile("\\s+"),
				Pattern.compile("^\\s+"),
		};
		final String[] R_SPACES = { " ", " ", "", };
		for (int i = 0; i < P_SPACES.length; ++i) {
			Matcher m = P_SPACES[i].matcher(_s);
			_s = m.replaceAll(R_SPACES[i]);
		}
		return _s;
	}

	public static final ArticleInfo parseArticle(final String _tabname,
			final Element _item) {
		NodeList nl;
		Node n;
		try {
			nl = _item.getElementsByTagName("THREAD");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: THREAD");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new ParseException("ParseException: THREAD");
			}
			final String thread = n != null ? n.getNodeValue() : null;

			nl = _item.getElementsByTagName("TITLE");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: TITLE");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			final String title = n != null ? n.getNodeValue() : "";

			nl = _item.getElementsByTagName("SEQ");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: SEQ");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new ParseException("ParseException: SEQ");
			}
			final int seq = Integer.parseInt(n.getNodeValue());

			nl = _item.getElementsByTagName("DATE");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: DATE");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new ParseException("ParseException: DATE");
			}
			final String date = n.getNodeValue();

			nl = _item.getElementsByTagName("USER");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: USER");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new ParseException("ParseException: USER");
			}
			final String user = n.getNodeValue();

			nl = _item.getElementsByTagName("AUTHOR");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: AUTHOR");
			}
			n = ((Element) nl.item(0)).getFirstChild();
			if (n == null) {
				throw new ParseException("ParseException: AUTHOR");
			}
			final String author = n != null ? n.getNodeValue() : null;

			String desc = "";
			nl = _item.getElementsByTagName("DESCRIPTION");
			if (nl != null && nl.getLength() > 0) {
				n = ((Element) nl.item(0)).getFirstChild();
				desc = n != null ? n.getNodeValue() : "";
			}

			return new ArticleInfo(_tabname, seq, user, author, date, title,
					thread, desc, 1, false);
		} catch (Exception e) {
			Log.w(TAG, e);
			return null;
		}
	}
}
