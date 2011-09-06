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
package org.sori.kidsbbs.io;

import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.sori.kidsbbs.KidsBbs.ParamName;
import org.sori.kidsbbs.KidsBbs.Settings;
import org.sori.kidsbbs.KidsBbs.UrlString;
import org.sori.kidsbbs.data.ArticleInfo;
import org.sori.kidsbbs.data.BoardInfo;
import org.sori.kidsbbs.util.ArticleUtils;
import org.sori.kidsbbs.util.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class HttpXml {

	public static final ArrayList<ArticleInfo> getArticles(final String _board,
			final int _type, final int _start) throws Exception {
		final ArrayList<ArticleInfo> articles = new ArrayList<ArticleInfo>();
		final String tabname = BoardInfo.buildTabname(_board, _type);
		final String urlString = UrlString.PLIST
			+ ParamName.BOARD + "=" + _board
			+ "&" + ParamName.TYPE + "=" + _type
			+ "&" + ParamName.SEQ + "=" + _start;
		final HttpClient client = new DefaultHttpClient();
		client.getParams().setParameter(
				HttpConnectionParams.CONNECTION_TIMEOUT, Settings.CONN_TIMEOUT);
		final HttpGet get = new HttpGet(urlString);
		final HttpResponse response = client.execute(get);
		final HttpEntity entity = response.getEntity();
		if (entity == null) {
			// ???
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
			final InputStream is = entity.getContent();
			final DocumentBuilder db =
				DocumentBuilderFactory.newInstance().newDocumentBuilder();

			// Parse the article list.
			final Document dom = db.parse(is);
			final Element docEle = dom.getDocumentElement();
			NodeList nl;

			nl = docEle.getElementsByTagName("ITEMS");
			if (nl == null || nl.getLength() <= 0) {
				throw new ParseException("ParseException: ITEMS");
			}
			final Element items = (Element) nl.item(0);

			// Get a board item
			nl = items.getElementsByTagName("ITEM");
			if (nl != null && nl.getLength() > 0) {
				for (int i = 0; i < nl.getLength(); ++i) {
					final ArticleInfo info = ArticleUtils.parseArticle(
							tabname, (Element) nl.item(i));
					if (info != null) {
						articles.add(info);
					}
				}
			}
		}
		return articles;
	}

	public static final int getArticlesLastSeq(final String _board,
			final int _type) {
		final String tabname = BoardInfo.buildTabname(_board, _type);
		final String urlString = UrlString.LIST
			+ ParamName.BOARD + "=" + _board
			+ "&" + ParamName.TYPE + "=" + _type
			+ "&" + ParamName.START + "=0"
			+ "&" + ParamName.COUNT + "=1";
		final HttpClient client = new DefaultHttpClient();
		client.getParams().setParameter(
				HttpConnectionParams.CONNECTION_TIMEOUT, Settings.CONN_TIMEOUT);
		final HttpGet get = new HttpGet(urlString);
		try {
			final HttpResponse response = client.execute(get);
			final HttpEntity entity = response.getEntity();
			if (entity == null) {
				// ???
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				final InputStream is = entity.getContent();
				final DocumentBuilder db =
					DocumentBuilderFactory.newInstance().newDocumentBuilder();

				// Parse the article list.
				final Document dom = db.parse(is);
				final Element docEle = dom.getDocumentElement();
				NodeList nl;

				nl = docEle.getElementsByTagName("ITEMS");
				if (nl == null || nl.getLength() <= 0) {
					throw new ParseException("ParseException: ITEMS");
				}
				final Element items = (Element) nl.item(0);

				// Get a board item
				nl = items.getElementsByTagName("ITEM");
				if (nl != null && nl.getLength() > 0) {
					final ArticleInfo info = ArticleUtils.parseArticle(
							tabname, (Element) nl.item(0));
					if (info != null) {
						return info.getSeq();
					}
				}
			}
		} catch (Exception e) {
		}
		return 0;
	}
}
