package com.aptiv.aptivusbotg;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.util.Log;
import android.os.UserHandle;
import android.os.UserManager;


public class UsbOtgStart extends BroadcastReceiver {
    private final static String LOG_TAG = "[AptivUsbOtg][UsbOtgStart]";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null || action == "") {
            Log.i(LOG_TAG, "[OTA] UsbOtgStart onReceive null action");
            return;
        }

        UserManager um = context.getSystemService(UserManager.class);
        if (um == null) {
            Log.e(LOG_TAG, "[OTA] UsbOtgStart onReceive: UserManager is null");
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)){
            Log.i(LOG_TAG, "[OTA] recevied BOOT_COMPLETED. isSystemUser:" + um.isSystemUser());
            try {
                Intent serviceIntent = new Intent(context, OtaProxyService.class);
                ComponentName cn = context.startService(serviceIntent);
                if (cn != null) {
                    Log.i(LOG_TAG, "Service started: " + cn.getClassName());
                } else {
                    Log.e(LOG_TAG, "Failed to start service");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to start service:", e);
            }
        }else{
            Log.e(LOG_TAG, "[OTA] not recevied BOOT_COMPLETED");
        }
    }
}