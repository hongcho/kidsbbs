package org.sori.sshtest;

import java.io.IOException;

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
    
    private class SshTask extends AsyncTask<Void,String,Void> {
    	private KidsConnection mConn = new KidsConnection();
		
		private static final String STR_GUEST = "guest";
    	
    	@Override
    	protected void onPreExecute() {
    		mTextView.setText("");
    	}

		@Override
		protected Void doInBackground(Void... arg0) {
			try {
				mConn.open(STR_GUEST, STR_GUEST);
				while (true) {
					try {
						mConn.process();
					} catch (KidsConnection.EOFException e) {
						break;
					}
					publishProgress();
				}
			} catch (IOException e) {
	    		Log.e(TAG, "IOException", e);
			}
			mConn.close();
			return null;
		}
    	
		@Override
		protected void onProgressUpdate(String... _args) {
			String t;
			try {
				t = mConn.dump();
			} catch (IOException e) {
				t = (String)mTextView.getText();
				t += "\n[[[dump failed]]]";
			}
			mTextView.setText(t);
		}
		
		@Override
		protected void onPostExecute(Void _result) {
			String t = (String)mTextView.getText();
			mTextView.setText(t + "\n[[[DONE]]]");
		}
    }
}