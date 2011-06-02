package org.sori.sshtest;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class SshTest extends Activity {
	public static final String TAG = "SshTest";
	
	private TextView mTextView;
	private SshTask mLastUpdate = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mTextView = (TextView)findViewById(R.id.text);
        
        mLastUpdate = new SshTask();
        mLastUpdate.execute();
    }
    
    private class SshTask extends AsyncTask<Void,Integer,Void> {
    	private KidsConnection mConn = new KidsConnection();
		
    	@Override
    	protected void onPreExecute() {
    		mTextView.setText("");
    	}

		@Override
		protected Void doInBackground(Void... arg0) {
			final String S_EUCKR = "EUC-KR";
			final String S_USER = "hongcho\n";
			final String S_PASS = "xxxxxxxx\n";
			
			final Pattern[] PATTERNS = {
				Pattern.compile("\\(User  Id\\):"),
				Pattern.compile("\\(Password\\):"),
				Pattern.compile("\\(y or n\\)\\[y\\]$"),
				Pattern.compile("\\((\\d+)%\\) CTRL"),
				Pattern.compile(" :"),
			};
			Matcher m;
			
			int state = 0;
			try {
				mConn.open();
				while (true) {
					try {
						mConn.process();
					} catch (KidsConnection.TimeoutException e) {
						publishProgress(state);
						switch (state) {
						case 0:
							m = PATTERNS[0].matcher(
									mConn.getCurrentLine(S_EUCKR));
							if (m.find()) {
								mConn.write(S_USER.getBytes());
								state = 1;
							}
							break;
						case 1:
							m = PATTERNS[1].matcher(
									mConn.getCurrentLine(S_EUCKR));
							if (m.find()) {
								mConn.write(S_PASS.getBytes());
								state = 2;
							}
							break;
						case 2:
							m = PATTERNS[2].matcher(
									mConn.getInputString(S_EUCKR));
							if (m.find()) {
								mConn.write("n\n".getBytes());
							}
							state = 999;//3;
							break;
						case 3:
							m = PATTERNS[3].matcher(
									mConn.getCurrentLine(S_EUCKR));
							if (m.find()) {
								int n = Integer.parseInt(m.group(1));
								if (n >= 100) {
									mConn.write("q".getBytes());
									state = 4;
								} else {
									mConn.write(" ".getBytes());
								}
							}
							break;
						case 4:
							m = PATTERNS[4].matcher(
									mConn.getCurrentLine(S_EUCKR));
							if (m.find()) {
								mConn.write("b\n".getBytes());
								state = 5;
							}
							break;
						case 5:
							m = PATTERNS[5].matcher(
									mConn.getCurrentLine(S_EUCKR));
							if (m.find()) {
								mConn.write("s\n".getBytes());
								//mConn.write("Stanford\n".getBytes());
								//mConn.write("r\n".getBytes());
								state = 6;
							}
							break;
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
			mConn.close();
			return null;
		}
    	
		@Override
		protected void onProgressUpdate(Integer... _args) {
			String t = "";
			try {
				//t += (String)mTextView.getText();
				//t += "==================================\n";
				t += mConn.dump();
				t += "====== " + _args[0] + " [" +
					mConn.getX() + ":" + mConn.getY() + "] ======\n";
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
