package es.tiarg.nfcndefreactnative;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;

import android.nfc.tech.NdefFormatable;
import android.nfc.tech.Ndef;

import android.nfc.NdefRecord;
import android.nfc.NdefMessage;

import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.Locale;
import java.util.Formatter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static android.R.attr.action;
import static android.R.attr.data;
import static android.R.attr.defaultValue;
import static android.R.attr.id;
import static android.R.attr.tag;
import static android.R.attr.x;
import static android.content.ContentValues.TAG;
import static android.view.View.X;
import static com.facebook.common.util.Hex.hexStringToByteArray;


class NfcNdefReactNativeModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {
    private ReactApplicationContext reactContext;

    private boolean idOperation;
    private boolean readOperation;
    private boolean writeOperation;
    private int tagId;

    private ReadableArray sectores;
	private ReadableArray to_write;
	private int index;

    private NfcAdapter mNfcAdapter;
    //private MifareClassic tag;
	private Tag tag;


	public NdefRecord createTextRecord(String payload, Locale locale, boolean encodeInUtf8) {

		/* if you wanna follow the standard...
		byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));
		Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
		byte[] textBytes = payload.getBytes(utfEncoding);
		int utfBit = encodeInUtf8 ? 0 : (1 << 7);
		char status = (char) (utfBit + langBytes.length);
		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;

		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
		*/

		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
			NdefRecord.RTD_TEXT, new byte[0], payload.getBytes() );
		return record;
	}

    private class ThreadLectura implements Runnable {
        public void run() {
            if (tag != null && (idOperation || readOperation || writeOperation)) {
				//Log.i("ReactNative", "[+] [NfcNdefReactNative] -> ThreadLectura.run() Op: " + String.valueOf(readOperation) + " " + String.valueOf(writeOperation) );
				try {

					//ByteBuffer bb = ByteBuffer.wrap(tag.getId());
					//int id = bb.getInt();

					if (idOperation) {
						//Log.i("ReactNative", "[+] [NfcNdefReactNative] -> ThreadLectura.run() ID Op: " + Integer.toString(id) );

						WritableMap idData = Arguments.createMap();
						idData.putInt("id", id);
						reactContext
							.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
							.emit("onTagDetected", idData);

                    	idOperation = false;

					} else if (readOperation) {
						Ndef ndef_tag = Ndef.get(tag);
						ndef_tag.connect();

						if (!ndef_tag.isConnected()) {
							return;
						}
						// Use the cached one if you want... but if you write and the read, you wont get what you wrote.
						//NdefMessage mes = ndef_tag.getCachedNdefMessage();
						NdefMessage mes = ndef_tag.getNdefMessage();
						NdefRecord[] ndef_records = mes.getRecords();
						WritableArray recs = new WritableNativeArray();

						for (int i = 0; i<ndef_records.length; i++) {
							recs.pushString(new String(ndef_records[i].getPayload(), "US-ASCII"));
						}
						//Log.i("ReactNative", "[+] [NfcNdefReactNative] -> ThreadLectura.run() Payloads: " + records[0] );

                        reactContext
                                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit("onTagRead", recs);
					
						readOperation = false;
						ndef_tag.close();

					} else if (writeOperation) {

						int s = to_write.size();
						NdefRecord[] recs = new NdefRecord[s];
						Log.i("ReactNative", "[+] [NfcNdefReactNative] -> ThreadLectura.run() len: " + Integer.toString(s));

						// This one's just for logging and sending to React
						WritableArray wrote = new WritableNativeArray();

						for (int i = 0; i<s; i++) {
							String cur = to_write.getString(i);
							Log.i("ReactNative", "[+] [NfcNdefReactNative] -> ThreadLectura.run() Writing: " + cur);
							recs[i] = createTextRecord(cur, new Locale("us"), true);
							wrote.pushString(cur);
						}

						NdefMessage mes = new NdefMessage(recs);
						NdefFormatable form_tag = NdefFormatable.get(tag);

						if (form_tag == null) {
							Ndef ndef_f_tag = Ndef.get(tag);
							ndef_f_tag.connect();

							if (!ndef_f_tag.isConnected()) {
								return;
							}

							ndef_f_tag.writeNdefMessage(mes);
							ndef_f_tag.close();
						} else {
							form_tag.connect();
							form_tag.format(mes);	
							form_tag.close();
						}
						
						reactContext
								.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
								.emit("onTagWrite", wrote);

						writeOperation = false;
					}

					//tag = null;
					return;

                } catch (Exception ex) {
                    WritableMap error = Arguments.createMap();
                    error.putString("error", ex.toString());

                    reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("onTagError", error);
                    tag = null;
				} 
			}
        }
    }

    public NfcNdefReactNativeModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.tag = null;
		this.index = 0;

        this.reactContext.addActivityEventListener(this);
        this.reactContext.addLifecycleEventListener(this);

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new ThreadLectura(), 0, 1, TimeUnit.SECONDS);

        this.idOperation = false;
        this.readOperation = false;
        this.writeOperation = false;
    }

    @Override
    public void onHostResume() {
        if (mNfcAdapter != null) {
            setupForegroundDispatch(getCurrentActivity(), mNfcAdapter);
        } else {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this.reactContext);
            setupForegroundDispatch(getCurrentActivity(), mNfcAdapter);
        }
    }

    @Override
    public void onHostPause() {
        if (mNfcAdapter != null)
            stopForegroundDispatch(getCurrentActivity(), mNfcAdapter);
    }

    @Override
    public void onHostDestroy() {
        // Activity `onDestroy`
    }

    private void handleIntent(Intent intent) {
		Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);  

		if (tagFromIntent != null) {
		    Log.i("ReactNative", Arrays.toString(tagFromIntent.getTechList()));
		}

		if (true) { //Arrays.asList(tagFromIntent.getTechList()).contains(android.nfc.tech.NdefFormatable)) {
			this.tag = tagFromIntent; // NdefFormatable.get(tagFromIntent);
		} else {
			Log.i("ReactNative", "[-] Bad tag.");
		}
		
		//this.tag = NfcA.get(tagFromIntent);

        //this.tag = MifareClassic.get( (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG));
    }

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);
        adapter.enableForegroundDispatch(activity, pendingIntent, null, null);
    }

    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    @Override
    public void onNewIntent(Intent intent) {
		Log.i("ReactNative", "[+] [NfcNdefReactNative] New intent.");
        handleIntent(intent);
    }

    @Override
    public void onActivityResult(
            final Activity activity,
            final int requestCode,
            final int resultCode,
            final Intent intent) {
    }
    /**
     * @return the name of this module. This will be the name used to {@code require()} this module
     * from javascript.
     */
    @Override
    public String getName() {
        return "NfcNdefReactNative";
    }

    @ReactMethod
    public void readTag() {
        this.index = index;
        this.readOperation = true;
    }

    @ReactMethod
    public void writeTag(ReadableArray to_write) {
        this.to_write = to_write;
        this.writeOperation = true;
    }

    @ReactMethod
    public void getTagId() {
		Log.i("ReactNative", "[+] [NfcNdefReactNative] -> getTagId()");
        this.idOperation = true;
    }
}
