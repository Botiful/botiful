package com.botiful.sdk.helpers;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;

/**
 * This class helps you manage common Bluetooth-related tasks (well just turning it on for for moment)
 */
public class BluetoothHelper {
	public static final int REQUEST_CODE_INTENT_ENABLE_BT = 0x1B071F31;

	/**
	 * If Bluetooth is not enabled on the device, this method will fire an intent
	 * to open the system settings "enable Bluetooth" dialog.
	 * @param fromActivity: calling activity.
	 */
	public static void enableBluetooth(Activity fromActivity) {
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (!bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			fromActivity.startActivityForResult(enableBtIntent, REQUEST_CODE_INTENT_ENABLE_BT);
		}
	}
}
