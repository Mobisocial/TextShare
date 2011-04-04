package mobisocial.textshare;

import java.net.URI;

import mobisocial.nfc.Nfc;
import mobisocial.nfc.Nfc.NdefFactory;
import mobisocial.nfc.Nfc.NdefHandler;

import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.junction.Junction;
import edu.stanford.junction.JunctionException;
import edu.stanford.junction.JunctionMaker;
import edu.stanford.junction.SwitchboardConfig;
import edu.stanford.junction.android.AndroidJunctionMaker;
import edu.stanford.junction.api.activity.JunctionActor;
import edu.stanford.junction.api.messaging.MessageHeader;
import edu.stanford.junction.provider.bluetooth.BluetoothSwitchboardConfig;
import edu.stanford.junction.provider.xmpp.XMPPSwitchboardConfig;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

public class TextShareActivity extends Activity {
    private EditText mEditText;
    private static final String TAG = "textedit";
    
    private JunctionMaker mJunctionMaker;
    private Junction mJunction; // interact
    private Nfc mNfc; // discover
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNfc = new Nfc(this);
        setContentView(R.layout.main);
        
        mEditText = (EditText)findViewById(R.id.text);
        mEditText.addTextChangedListener(mTextChanged);
        
        // Connect to Junction so we can work with other devices.
        SwitchboardConfig config;
        // Using XMPP:
        //config = new XMPPSwitchboardConfig("prpl.stanford.edu");
        // Using Bluetooth:
        config = new BluetoothSwitchboardConfig();
        mJunctionMaker = AndroidJunctionMaker.getInstance(config);
        
        try {		
	        URI uri = mJunctionMaker.generateSessionUri();
	        mJunction = mJunctionMaker.newJunction(uri, mJunctionObserver);
	        
	        // Listen over NFC to join chat sessions.
	        mNfc.addNdefHandler(mJunctionHopper);
        } catch (Exception e) {
        	Log.e(TAG, "Error joining junction", e);
        }
    }

	private TextWatcher mTextChanged = new TextWatcher() {
		@Override
		public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
			// TODO: be efficient! send the params (deltas) across the wire.
			doLocalChanges(false);
		}
		
		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {
			
		}
		
		@Override
		public void afterTextChanged(Editable arg0) {
			
		}
	};
    
    private void doLocalChanges(boolean init) {
    	Junction junction = mJunction;
    	if (junction == null) return;
		JSONObject obj = null;
		try {
			obj = new JSONObject();
			if (init) {
				// request remote content
				obj.put("init", "true");
			}
			obj.put("text", mEditText.getText().toString());
		} catch (JSONException e) { }
		junction.sendMessageToSession(obj);
    }
    
	JunctionActor mJunctionObserver = new JunctionActor("editor") {
		public void onMessageReceived(MessageHeader header, final JSONObject msg) {
			if (getActorID().equals(header.from)) {
				return;
			}
			if (msg.has("init")) {
				// respond to remote content request
				doLocalChanges(false);
			}

			// Whenever you touch the UI, you must execute on the UI thread.
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					((EditText)findViewById(R.id.remote)).setText(msg.optString("text"));
				}
			});
		};
		
		public void onActivityJoin() {
	        // TODO: web link ; app manifest
	        // mJunction.getnvitationForWeb("http://prpl.stanford.edu/whatever");
	        mNfc.share(NdefFactory.fromUri(mJunction.getInvitationURI()));
	        TextShareActivity.this.mJunction = getJunction();
			doLocalChanges(true);
		};
	};
	
	NdefHandler mJunctionHopper = new NdefHandler() {
		public int handleNdef(NdefMessage[] ndefMessages) {
			// TODO: make sure it's mine
			final String foreign = new String(ndefMessages[0].getRecords()[0].getPayload());
			
			if (mJunction.getInvitationURI().toString().compareTo(foreign) > 0) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						toast("Joining other phone");						
					}
				});

				try {
					mJunction.disconnect();
					mJunction = mJunctionMaker.newJunction(URI.create(foreign), mJunctionObserver);
				} catch (JunctionException e) {
					Log.e(TAG, "could not join foreign junction", e);
				}
			} else {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						toast("Incoming!");						
					}
				});
			}
			return NDEF_CONSUME;
		};
	};
	
	protected void onResume() {
		super.onResume();
		mNfc.onResume(this);
	};
	
	@Override
	protected void onPause() {
		super.onPause();
		mNfc.onPause(this);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		if (mNfc.onNewIntent(this, intent)) return;
	}
	
	private void toast(final String text) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(TextShareActivity.this, text, Toast.LENGTH_SHORT).show();						
			}
		});
	}
}