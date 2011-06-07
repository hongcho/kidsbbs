package org.sori.sshtest;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class SshTest extends Activity {
	public static final String TAG = "SshTest";
	
	private TextView mTextView;
	private SshTask mLastUpdate = null;
	
	private String mUsername;
	private String mPassword;
	private String mPostTitle;
	private String mPostBody;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mTextView = (TextView)findViewById(R.id.text);
        
        final Resources resources = getResources();
        mUsername = resources.getString(R.string.username);
        mPassword = resources.getString(R.string.password);
        mPostTitle = resources.getString(R.string.post_title);
        mPostBody = resources.getString(R.string.post_body);
        
        mLastUpdate = new SshTask();
        mLastUpdate.execute();
    }
    
    private class SshTask extends AsyncTask<Void,Integer,Void> {
		private static final String S_EUCKR = "EUC-KR";
		
		// Kids BBS states
		private static final int ST_USER = 0;
		private static final int ST_PASSWORD = 1;
		private static final int ST_CLOSE_OTHERS = 2;
		private static final int ST_GUEST_LOGIN = 3;
		
		private static final int ST_LOGIN_NOTICE = 9;
		private static final int ST_MENU_KIDS = 10;
		private static final int ST_MENU_BBS = 11;
		private static final int ST_MENU_WRITER = 12;
		private static final int ST_MENU_SQUARE = 13;
		
		private static final int ST_LIST_BBS = 20;
		private static final int ST_VIEW_BBS = 21;
		private static final int ST_DELETE_BBS = 22;
		
		private static final int ST_WRITE_BBS = 30;
		private static final int ST_WRITE_BBS_BODY = 31;
		private static final int ST_WRITE_BBS_END = 32;
		
		private static final int ST_QUIT = 99;
		private static final int ST_INVALID = -1; 
		
		private static final int ST_LIST_BBS_1 = 100;	// temp
		private static final int ST_MENU_BBS_1 = 101;	// temp
		
		// Patterns for prompts.
		private final Pattern P_USER =
			Pattern.compile("\\(User  Id\\):");
		private final Pattern P_PASSWORD =
			Pattern.compile("\\(Password\\):");
		private final Pattern P_GUEST_NAME =
			Pattern.compile("^I Am ");
		private final Pattern P_CLOSE_OTHERS =
			Pattern.compile("\\(y or n\\)\\[y\\]");
		private final Pattern P_LESS_STATUS =
			Pattern.compile("^\\((\\d+)%\\) CTRL");
		private final Pattern P_MUTT_STATUS =
			Pattern.compile(" -- \\((\\d+)%|(all|end)\\)\\s*$");
		private final Pattern P_MENU_PROMPT =
			Pattern.compile("^\\* ");
		private final Pattern P_LIST_PROMPT =
			Pattern.compile("^>");
		private final Pattern P_WRITE_TITLE =
			Pattern.compile(": ");
		private final Pattern P_WRITE_FOOTER_S =
			Pattern.compile("\\[ New file \\]");
		private final Pattern P_WRITE_FOOTER_VI =
			Pattern.compile("\\[NEW FILE\\]");
		private final Pattern P_WRITE_SIG =
			Pattern.compile(" <Y, n> ");
		
		private static final String PS_WRITE_REG =
			"(?<=\\n)\\n";
		private static final String PS_WRITE_REP =
			"\r\n";
		
    	private KidsConnection mConn = new KidsConnection();
    	
    	private int handleLogin(int _state) throws IOException {
			switch (_state) {
			case ST_USER: return handleUser(_state);
			case ST_PASSWORD: return handlePassword(_state);
			case ST_CLOSE_OTHERS: return handleCloseOthers(_state);
			case ST_GUEST_LOGIN: return handleGuestLogin(_state);
			default:
				throw new IOException("Login: unknown state " + _state);
			}
    	}
    	private int handleUser(int _state) throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_USER.matcher(s);
			if (m.find()) {
				mConn.write(mUsername + "\n");
				if (mUsername.equals("guest")) {
					return ST_GUEST_LOGIN;
				} else {
					return ST_PASSWORD;
				}
			}
			return _state;
    	}
    	private int handlePassword(int _state) throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_PASSWORD.matcher(s);
			if (m.find()) {
				mConn.write(mPassword + "\n");
				return ST_CLOSE_OTHERS;
			}
			return _state;
    	}
    	private int handleCloseOthers(int _state) throws IOException {
			String s = mConn.getLine(mConn.getY() - 1,S_EUCKR);
			Matcher m = P_CLOSE_OTHERS.matcher(s);
			if (m.find()) {
				mConn.write("n\n");
				return ST_LOGIN_NOTICE;
			}
			// Check if there were no other logins.
			s = mConn.getCurrentLine(S_EUCKR);
			m = P_LESS_STATUS.matcher(s);
			if (m.find()) {
				return ST_LOGIN_NOTICE;
			}
			m = P_MUTT_STATUS.matcher(s);
			if (m.find()) {
				return ST_LOGIN_NOTICE;
			}
			return _state;
    	}
    	private int handleGuestLogin(int _state) throws IOException {
    		String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_GUEST_NAME.matcher(s);
			if (m.find()) {
				mConn.write(mUsername + "\n");
				mConn.write("Y\n");
				mConn.write("vt100\n");
				return ST_LOGIN_NOTICE;
			} else {
				throw new IOException("GuestLogin: unexpected prompt");
			}
    	}
    	
    	private int handleArticle(int _state, int _next) throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_LESS_STATUS.matcher(s);
			if (m.find()) {
				int n = Integer.parseInt(m.group(1));
				if (n >= 100) {
					mConn.write("q");
					return _next;
				} else {
					mConn.write(" ");
				}
				return _state;
			}
			m = P_MUTT_STATUS.matcher(s);
			if (m.find()) {
	    		int n;
				try {
					n = Integer.parseInt(m.group(1));
				} catch (NumberFormatException e1) {
					n = -1;
				}
				if (n < 0) {
					mConn.write("q");
					return _next;
				} else {
					mConn.write(" ");
				}
				return _state;
			}
			return _state;
    	}
    	
    	private int handleWriteBbs(int _state, int _next, String _body)
    			throws IOException {
			switch (_state) {
			case ST_WRITE_BBS: return handleWriteBbsTitle(_state);
			case ST_WRITE_BBS_BODY: return handleWriteBbsBody(_state, _body);
			case ST_WRITE_BBS_END: return handleWriteBbsEnd(_state, _next);
			default:
				throw new IOException("WriteBbs: unknown state " + _state);
			}
    	}
    	private int handleWriteBbsTitle(int _state) throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_WRITE_TITLE.matcher(s);
			if (m.find()) {
				mConn.write(mPostTitle + "\n");
				return ST_WRITE_BBS_BODY;
			}
			return _state;
    	}
    	private int handleWriteBbsBody(int _state, String _body)
    			throws IOException {
			String s = mConn.getLine(mConn.getHeight() - 1, S_EUCKR);
			Matcher m = P_WRITE_FOOTER_S.matcher(s);
			if (m.find()) {
				mConn.write(_body.replaceAll(PS_WRITE_REG, PS_WRITE_REP));
				mConn.write("\030");	// Ctrl-X
				return ST_WRITE_BBS_END;
			}
			m = P_WRITE_FOOTER_VI.matcher(s);
			if (m.find()) {
				mConn.write("i");	// insert
				mConn.write(_body);
				mConn.write("\033ZZ");	// ESC ZZ
				return ST_WRITE_BBS_END;
			}
			return _state;
    	}
    	private int handleWriteBbsEnd(int _state, int _next) throws IOException {
			String s = mConn.getLine(0, S_EUCKR);
			Matcher m = P_WRITE_SIG.matcher(s);
			if (m.find()) {
				mConn.write("n\n");	// signature?
				mConn.write("y\n");	// save?
				mConn.write("\n");	// return.
				return _next;
			}
			return _state;
    	}
    	
    	private int handleMenuKids(int _state, int _next) throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_MENU_PROMPT.matcher(s);
			if (m.find()) {
				switch (_next) {
				case ST_MENU_BBS:
					mConn.write("b\n");
					return _next;
				case ST_MENU_WRITER:
					mConn.write("w\n");
					return _next;
				case ST_MENU_SQUARE:
					mConn.write("s\n");
					return _next;
				case ST_QUIT:
					mConn.write("q\ny");
					return _next;
				default:
					throw new IOException("MenuKids: unknown next state: " + _next);
				}
			}
			return _state;
    	}
    	private int handleMenuBbs(int _state, int _next, String _board)
    			throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_MENU_PROMPT.matcher(s);
			if (m.find()) {
				switch (_next) {
				case ST_LIST_BBS:
					mConn.write("s\n");
					mConn.write(_board + "\n");
					mConn.write("r\n");
					return _next;
				case ST_MENU_BBS:
				case ST_MENU_BBS_1:
					mConn.write("p");
					return _next;
				case ST_QUIT:
					mConn.write("q\ny");
					return _next;
				default:
					throw new IOException("MenuBbs: unknown next state: " + _next);
				}
			}
			return _state;
    	}
    	private int handleListBbs(int _state, int _next, String _pos)
    			throws IOException {
			String s = mConn.getCurrentLine(S_EUCKR);
			Matcher m = P_LIST_PROMPT.matcher(s);
			if (m.find()) {
				switch (_next) {
				case ST_VIEW_BBS:
					mConn.write(_pos + "\n ");
					return _next;
				case ST_DELETE_BBS:
					mConn.write(_pos + "\nd");
					return _next;
				case ST_WRITE_BBS:
					mConn.write("w");
					return _next;
				case ST_MENU_BBS:
				case ST_MENU_BBS_1:
					mConn.write("q");
					return _next;
				default:
					throw new IOException("ListBbs: unknown next state: " + _next);
				}
			}
			return _state;
    	}

		@Override
		protected Void doInBackground(Void... arg0) {
			int state = ST_USER;
			try {
				mConn.open();
				while (true) {
					try {
						mConn.process();
					} catch (KidsConnection.TimeoutException e) {
						publishProgress(state);
						switch (state) {
						case ST_USER:
						case ST_PASSWORD:
						case ST_CLOSE_OTHERS:
						case ST_GUEST_LOGIN:
							state = handleLogin(state);
							break;
						case ST_LOGIN_NOTICE:
							state = handleArticle(state, ST_MENU_KIDS);
							break;
						case ST_MENU_KIDS:
							state = handleMenuKids(state, ST_MENU_BBS);
							break;
						case ST_MENU_BBS:
							state = handleMenuBbs(state, ST_LIST_BBS, "Stanford");
							break;
						case ST_LIST_BBS:
							//state = handleListBbs(state, ST_VIEW_BBS, "1000");
							state = handleListBbs(state, ST_WRITE_BBS, null);
							break;
						case ST_VIEW_BBS:
							state = handleArticle(state, ST_LIST_BBS_1);
							break;
						case ST_WRITE_BBS:
						case ST_WRITE_BBS_BODY:
						case ST_WRITE_BBS_END:
							state = handleWriteBbs(state, ST_LIST_BBS_1, mPostBody);
							break;
							
						case ST_LIST_BBS_1:
							state = handleListBbs(state, ST_MENU_BBS_1, null);
							break;
						case ST_MENU_BBS_1:
							state = handleMenuBbs(state, ST_QUIT, null);
							break;
							
						case ST_QUIT:
							// Wait until EOF.
							break;
						case ST_INVALID:
						default:
							throw e;
						}
					} catch (KidsConnection.EOFException e) {
						break;
					}
				}
			} catch (IOException e) {
	    		Log.e(TAG, "IOException", e);
			}
			publishProgress(state);
			mConn.close();
			return null;
		}
		
    	@Override
    	protected void onPreExecute() {
    		mTextView.setText("");
    	}
    	
		@Override
		protected void onProgressUpdate(Integer... _args) {
			String t = "";
			try {
				t += mConn.dump();
				t += "====== " + _args[0] + " [" +
					mConn.getY() + ":" + mConn.getX() + "] ======\n";
				t += mConn.dumpCurrentLine();
			} catch (IOException e) {
				t += (String)mTextView.getText();
				t += "\n[[[dump failed]]]";
			}
			mTextView.setText(t);
		}
		
		@Override
		protected void onPostExecute(Void _result) {
			String t = (String)mTextView.getText();
			mTextView.setText(t + "[[[DONE]]]");
		}
    }
}
