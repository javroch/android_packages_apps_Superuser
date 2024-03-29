/*******************************************************************************
 * Copyright (c) 2011 Adam Shanks (ChainsDD)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.noshufou.android.su;

import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.provider.PermissionsProvider.Apps;
import com.noshufou.android.su.util.Util;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.io.IOException;
import java.io.OutputStream;

public class SuRequestActivity extends Activity implements OnClickListener {
    private static final String TAG = "Su.SuRequestActivity";
    private static final String ROOT_ACCESS_PROPERTY = "persist.sys.clean.root";
    private static final String ROOT_ACCESS_DEFAULT = "0";
    private static final String ROOT_SETTINGS_PROPERTY = "ro.clean.root";

    private LocalSocket mSocket;
    private SharedPreferences mPrefs;

    private int mCallerUid = 0;
    private int mDesiredUid = 0;
    private String mDesiredCmd = "";
    private int mSuVersionCode = 0;
    
    private boolean mUsePin = false;
    private int mAttempts = 3;
    
    private NfcAdapter mNfcAdapter = null;

    private CheckBox mRememberCheckBox;
    private EditText mPinText;
    private ViewFlipper mFlipper;
    private TextView mMoreInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        String socketPath;
        super.onCreate(savedInstanceState);

        if (this.getCallingPackage() != null) {
            Log.e(TAG, "SuRequest must be started from su");
            finish();
            return;
        }

		String root_settings = SystemProperties.get(ROOT_SETTINGS_PROPERTY, "");
		if ("1".equals(root_settings)) {
		    int root_access = Integer.valueOf(SystemProperties.get(ROOT_ACCESS_PROPERTY, ROOT_ACCESS_DEFAULT));
		    if ((root_access & 1) != 1) { //disabled / adb only (even values)
		        Log.e(TAG, "SuRequest denied by system settings");
		        finish();
		        return;
		    }
		}

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = this.getIntent();
        mCallerUid = intent.getIntExtra(SuRequestReceiver.EXTRA_CALLERUID, 0);
        mDesiredUid = intent.getIntExtra(SuRequestReceiver.EXTRA_UID, 0);
        mDesiredCmd = intent.getStringExtra(SuRequestReceiver.EXTRA_CMD);
        socketPath = intent.getStringExtra(SuRequestReceiver.EXTRA_SOCKET);
        mSuVersionCode = intent.getIntExtra(SuRequestReceiver.EXTRA_VERSION_CODE, 0);

        mUsePin = mPrefs.getBoolean(Preferences.PIN, false);
        if (mUsePin) {
            this.setContentView(R.layout.activity_request_pin);
            ViewGroup pinLayout = (ViewGroup) findViewById(R.id.pin_layout);
            mPinText = (EditText) pinLayout.findViewById(R.id.pin);
            ((Button)pinLayout.findViewById(R.id.pin_0)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_1)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_2)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_3)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_4)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_5)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_6)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_7)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_8)).setOnClickListener(onPinButton);
            ((Button)pinLayout.findViewById(R.id.pin_9)).setOnClickListener(onPinButton);
            ((Button)findViewById(R.id.pin_ok)).setOnClickListener(this);
            ((Button)findViewById(R.id.pin_cancel)).setOnClickListener(this);
        } else {
            this.setContentView(R.layout.activity_request);
            ((Button)findViewById(R.id.allow)).setOnClickListener(this);
            ((Button)findViewById(R.id.deny)).setOnClickListener(this);
        }
        
        try {
            mSocket = new LocalSocket();
            mSocket.connect(new LocalSocketAddress(socketPath,
                    LocalSocketAddress.Namespace.FILESYSTEM));
        } catch (IOException e) {
            // If we can't connect to the socket, there's no point in
            // being here. Log it and quit
            Log.e(TAG, "Failed to connect to socket", e);
            finish();
        }

        if (mSuVersionCode < 10) {
            Util.showOutdatedNotification(this);
        }

        TextView message = (TextView) findViewById(R.id.message);
        message.setText(getString(R.string.request_message, Util.getAppName(this, mCallerUid, false)));

        TextView appNameView = (TextView) findViewById(R.id.app_name);
        appNameView.setText(Util.getAppName(this, mCallerUid, true));

        TextView packageNameView = (TextView) findViewById(R.id.package_name);
        packageNameView.setText(Util.getAppPackage(this, mCallerUid));

        TextView requestDetailView = (TextView) findViewById(R.id.request_detail);
        requestDetailView.setText(Util.getUidName(this, mDesiredUid, true));

        TextView commandView = (TextView)findViewById(R.id.command);
        commandView.setText(mDesiredCmd);

        mRememberCheckBox = (CheckBox) findViewById(R.id.check_remember);
        mRememberCheckBox.setChecked(mPrefs.getBoolean("last_remember_value", true));

        mFlipper = (ViewFlipper) findViewById(R.id.flipper);
        mMoreInfo = (TextView)findViewById(R.id.more_info);

        if (mPrefs.getBoolean(Preferences.ADVANCED_PROMPT, false)) {
            mFlipper.setDisplayedChild(1);
            mMoreInfo.setVisibility(View.GONE);
        } else {
            mFlipper.setOnClickListener(this);
            mMoreInfo.setOnClickListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPrefs.getBoolean(Preferences.USE_ALLOW_TAG, false)) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType("text/x-su-a");
            } catch (MalformedMimeTypeException e) {
                Log.e(TAG, "Bad MIME type declared", e);
                return;
            }
            IntentFilter[] filters = new IntentFilter[] { ndef };
            String[][] techLists = new  String[][] {
                    new String[] { Ndef.class.getName() }
            };
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
            sendResult(false, false);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
        case R.id.allow:
        case R.id.pin_ok:
            if (mUsePin) {
                mAttempts--;
                if (Util.checkPin(this, mPinText.getText().toString())) {
                    sendResult(true, mRememberCheckBox.isChecked());
                } else if (mAttempts > 0) {
                    mPinText.setText("");
                    mPinText.setHint(getResources().getQuantityString(R.plurals.pin_incorrect_try,
                            mAttempts, mAttempts));
                    mPinText.setHintTextColor(Color.RED);
                } else {
                    sendResult(false, false);
                }
            } else {
                sendResult(true, mRememberCheckBox.isChecked());
            }
            break;
        case R.id.deny:
        case R.id.pin_cancel:
            sendResult(false, mRememberCheckBox.isChecked());
            break;
        case R.id.flipper:
        case R.id.more_info:
            flipInfo();
        }
    }

    private void flipInfo() {
        mFlipper.showNext();
        if (mFlipper.getDisplayedChild() == 0) {
            mMoreInfo.setText(R.string.request_more_info);
        } else {
            mMoreInfo.setText(R.string.request_less_info);
        }
    }
    
    private View.OnClickListener onPinButton = new View.OnClickListener() {
        public void onClick(View view) {
            Button button = (Button) view;
            mPinText.append(button.getText());
        }
    };

    private void sendResult(boolean allow, boolean remember) {
        String resultCode = allow ? "ALLOW" : "DENY";

        if (remember) {
            ContentValues values = new ContentValues();
            values.put(Apps.UID, mCallerUid);
            values.put(Apps.PACKAGE, Util.getAppPackage(this, mCallerUid));
            values.put(Apps.NAME, Util.getAppName(this, mCallerUid, false));
            values.put(Apps.EXEC_UID, mDesiredUid);
            values.put(Apps.EXEC_CMD, mDesiredCmd);
            values.put(Apps.ALLOW, allow?Apps.AllowType.ALLOW:Apps.AllowType.DENY);
            getContentResolver().insert(Apps.CONTENT_URI, values);
        }

        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean("last_remember_value", mRememberCheckBox.isChecked());
        
        int timeout = mPrefs.getInt(Preferences.TIMEOUT, 0);
        if (timeout > 0 && allow) {
            String key = "active_" +  mCallerUid;
            editor.putLong(key, System.currentTimeMillis() + (timeout * 1000));
        }
        editor.commit();

        try {
            OutputStream os = mSocket.getOutputStream();
            Log.i(TAG, "Sending result: " + resultCode + " for UID: " + mCallerUid);
            os.write(resultCode.getBytes("UTF-8"));
            os.flush();
            os.close();
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to socket", e);
        }
        finish();
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            NdefMessage msg = (NdefMessage) rawMsgs[0];
            NdefRecord record = msg.getRecords()[0];
            short tnf = record.getTnf();
            String type = new String(record.getType());
            if (tnf == NdefRecord.TNF_MIME_MEDIA &&
                    type.equals("text/x-su-a")) {
                String tagPin = new String(record.getPayload());
                if (tagPin.equals(mPrefs.getString("pin", ""))) {
                    sendResult(true, mRememberCheckBox.isChecked());
                }
            }
        }
    }
}
