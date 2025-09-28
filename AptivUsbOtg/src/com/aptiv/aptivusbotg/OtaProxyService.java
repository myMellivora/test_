package com.aptiv.aptivusbotg;

import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Binder;
import android.os.HwBinder;
import android.os.IHwBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import android.widget.Toast;
import android.app.Service;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
// import vendor.aptiv.hardware.automotive.vehicle.V2_0.AptivVehicleProperty;

import java.util.NoSuchElementException;
import java.io.File;

public class OtaProxyService extends Service {
    private final static String LOG_TAG = "[AptivUsbOtg][OtaProxyService]";
    UsbDialog usbDialog = null;
    UsbDT usbDT = null;
    private IVehicle mVehicle;
    private boolean bOnStartCheckUsbPath  = false;
    private boolean enter_fbl_cmd_sent_ = false;

    private static final String usb_update_dir = "Chery_S59D";
    private static final String fbl_usb_force_flag = usb_update_dir +"/cheryusbtestforaptiv9527";

    private static final int VENDOR = 0x20000000;
    private static final int BYTES = 0x00700000;
    private static final int GLOBAL = 0x01000000;

    // final int I_SWITCH_TO_EMERGENCY_QNX = AptivVehicleProperty.USB_UPDATE;
    final int I_SWITCH_TO_EMERGENCY_QNX = 100;

    private BroadcastReceiver mInitReceiver = null;

    private int check_usb_disk_count_ = 0;

    private Handler delayed_handler_ = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(){
        initUsbDT();
        createInitReceiver();
        enter_fbl_cmd_sent_ = false;
        check_usb_disk_count_ = 0;
        Log.i(LOG_TAG,"onCreate end");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "onStartCommand uid: " + UserHandle.myUserId());

        if(bOnStartCheckUsbPath == false) {
            bOnStartCheckUsbPath = true;
            check_usb_disk_count_ = 0;
            delayedCheckUSBDiskTask(2000);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "onDestroy" );
        unregisterReceiver(mInitReceiver);
        unregisterReceiver(usbDT);
    }

    @Nullable
    private static IVehicle getVehicle() {
        try {
            return android.hardware.automotive.vehicle.V2_0.IVehicle.getService();
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to get IVehicle service", e);
        } catch (NoSuchElementException e) {
            Log.e(LOG_TAG, "IVehicle service not registered yet");
        }
        return null;
    }

    private boolean initVehicleHal()
    {
        Log.i(LOG_TAG,"initVehicleHal ");
        mVehicle = getVehicle();
        if(mVehicle == null) {
            Log.i(LOG_TAG,"vehicle hal init failed ");
            return false;
        }
        Log.i(LOG_TAG,"vehicle hal init success ");
        return true;
    }

    private void delayedCheckUSBDiskTask(int delayed_ms)
    {
        if (delayed_ms == 0){
            sendAction(OtaProxyType.OTA_EXTERNAL_PATH);
            return;
        }

        Runnable delayedCheckUSBDiskTask = new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "onStartCommand delay to check USB-Disk ");
                sendAction(OtaProxyType.OTA_EXTERNAL_PATH);
            }
        };
        delayed_handler_.postDelayed(delayedCheckUSBDiskTask, delayed_ms);
    }

    private void delayedStopService(int delayed_ms)
    {
        if (delayed_ms == 0){
            stopSelf();
            return;
        }

        Runnable delayedStopService = new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "onStartCommand delay to stop service ");
                stopSelf();
            }
        };
        delayed_handler_.postDelayed(delayedStopService, delayed_ms);
    }

    private void checkUsbUpdatePath()
    {
        sendAction(OtaProxyType.OTA_EXTERNAL_PATH);
    }

    private void sendAction(String action)
    {
        Intent intent= new Intent();
        intent.setAction(action);
        sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendActionWithParam(String action,String name,String path)
    {
        Intent intent= new Intent("");
        intent.putExtra(name,path);
        intent.setAction(action);
        //sendBroadcast(intent);
        sendBroadcastAsUser(intent, UserHandle.ALL);
        Log.i(LOG_TAG,"sendActionWithParam end :" + action);
    }

    private void sendSystemAction(String action, String name,String path)
    {
        Intent intent= new Intent("");
        intent.putExtra(name,path);
        intent.setAction(action);
        //sendBroadcastAsUser(intent, UserHandle.SYSTEM);
        sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    boolean startEnterFBL(int enabled)
    {
        if (enter_fbl_cmd_sent_){
            Log.w(LOG_TAG,"enter fbl cmd has sent.ignored");
            return false;
        }

        if(mVehicle == null) {
           if(!initVehicleHal()) {
                Log.i(LOG_TAG,"sendProp Vehicle Hal not init" );
                return false;
           }
        }

        byte bValue = (byte) enabled;
        VehiclePropValue propRequest = new VehiclePropValue();
        propRequest.prop = I_SWITCH_TO_EMERGENCY_QNX;
        propRequest.value.bytes.add(bValue);
        Log.i(LOG_TAG,"Vehicle Hal set prop:"+Integer.toHexString(I_SWITCH_TO_EMERGENCY_QNX) + " value:" + bValue);

        if(mVehicle != null) {
            int status = -1;
            try {
                status = mVehicle.set(propRequest);
                Log.i(LOG_TAG,"Vehicle Hal set status:" + status);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "IVehicle Hal set " + e);
            }
        }else{
            Log.i(LOG_TAG,"Vehicle Hal not init" );
        }

        enter_fbl_cmd_sent_ = true;

        return true;
    }

    private void onNotifyIntentActionMediaMounted(Context context, Intent intent) {

        // Handle the media mounted intent
        String path = intent.getStringExtra("mountpoint");

        if (path.startsWith("/storage/emulated") || path.startsWith("/storage/self")) {
            return;
        }

        // Log.i(LOG_TAG, "onNotifyIntentActionMediaMounted:" + path + " context uid:" + context.getUserId() + " binder uid:" + Binder.getCallingUid());

        if(!checkUsbPath(path)) {
            return;
        }

        String flag_path = path + "/" + fbl_usb_force_flag;
        if(!UsbDT.FileExists(flag_path)) {
            Log.w(LOG_TAG, "[UDISK FOUND][NOT FOUND force update flag] onNotifyUsbPath: CHECK flag fail!!!Don't flash");
            return;
        }

        if(checkUsbUpgradePackage(path)) {
            startEnterFBL(0);
        }
    }

    private void createInitReceiver() {
        mInitReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action == null) {
                    return;
                }
                switch (action) {
                    case OtaProxyType.OTA_PROXY_SERVICE_INIT_VEHICLE_HAL: {

                        if (initVehicleHal()) {
                            sendAction(OtaProxyType.OTA_PROXY_SERVICE_INIT_VEHICLE_HAL_FINISH);
                        } else {
                            sendAction(OtaProxyType.OTA_PROXY_SERVICE_INIT_VEHICLE_HAL_FAILED);
                        }

                    }
                        break;
                    case OtaProxyType.OTA_PROXY_SERVICE_SWITCH_TO_EMERGENCY_QNX: {
                        int req = intent.getIntExtra(OtaProxyType.REQ_TO_EMERGENCY_QNX, 1);
                        Log.i(LOG_TAG, "REQ_TO_EMERGENCY_QNX = " + req);
                        startEnterFBL(req);
                    }
                        break;
                    case OtaProxyType.OTA_EXTERNAL_PATH: {
                        Log.i(LOG_TAG, "check updata file start! uid:" + UserHandle.myUserId() + " check_usb_disk_count:" + check_usb_disk_count_);

                        if (check_usb_disk_count_ < 3){
                            if (usbDT.is_usb_device_pending(OtaProxyService.this)){
                                delayedCheckUSBDiskTask(3000);
                                check_usb_disk_count_++;
                                break;
                            }
                        }

                        if (usbDT.checkUsbUpgrade(OtaProxyService.this, usb_update_dir, fbl_usb_force_flag, true)) {
                            startEnterFBL(0);
                        } else {
                            Log.i(LOG_TAG, "check updata file fail!");
                        }

                    }
                        break;
                    case OtaProxyType.OTA_EXTERNAL_PATH2:{
                            Log.i(LOG_TAG, "go to emergency qnxxx");
                            startEnterFBL(0);
                        }
                    break;

                    case OtaProxyType.INTENT_ACTION_APTIVUSBOTG_MEDIA_MOUNTED:
                        onNotifyIntentActionMediaMounted(context, intent);
                        break;
                    default:
                        break;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        // filter.addAction(OtaProxyType.OTA_PROXY_SHOW_DIALOG);
        filter.addAction(OtaProxyType.OTA_EXTERNAL_PATH);
        filter.addAction(OtaProxyType.OTA_EXTERNAL_PATH2);
        filter.addAction(OtaProxyType.OTA_PROXY_SERVICE_INIT_VEHICLE_HAL);
        filter.addAction(OtaProxyType.OTA_PROXY_SERVICE_SWITCH_TO_EMERGENCY_QNX);
        filter.addAction(OtaProxyType.INTENT_ACTION_APTIVUSBOTG_MEDIA_MOUNTED);
        registerReceiver(mInitReceiver, filter);
    }

    private synchronized boolean checkUsbPath(String path) {

        if (path == null) {
            Log.i(LOG_TAG, "checkUsbPath path is null");
            return false;
        }

        //Log.i(LOG_TAG, "checkUsbPath:" + path);

        String usb_path = path;
        File file = new File(usb_path);
        if (!file.exists()) {
            Log.i(LOG_TAG, usb_path + " not exist");
            return false;
        } else {
            if (!file.isDirectory()) {
                Log.i(LOG_TAG, usb_path + "is not dir");
                return false;
            }
        }
        return true;
    }

    private synchronized boolean checkUsbUpgradePackage(String path) {
        Log.i(LOG_TAG, "checkUsbUpgradePackage:" + path);
        if (path == null) {
            Log.i(LOG_TAG, "checkUsbUpgradePackage path is null");
            return false;
        }
        String usb_path = path + "/" + usb_update_dir;
        File file = new File(usb_path);
        if (!file.exists()) {
            Log.i(LOG_TAG, usb_path + " not exist");
            return false;
        } else {
            if (!file.isDirectory()) {
                Log.i(LOG_TAG, usb_path + "is not dir");
                return false;
            }
        }
        return true;
    }

    private void initUsbDT()
    {
        //Log.i(LOG_TAG, "InitUsbDT .");
        usbDT  = new UsbDT();
        usbDT.setonNotifyListener(new UsbDT.onNotify() {
            @Override
            public void onNotifyUsbPath(String path) {
                sendSystemAction(OtaProxyType.INTENT_ACTION_APTIVUSBOTG_MEDIA_MOUNTED, "mountpoint", path);
            }
        });

        //registerReceiverAsUser(usbDT, UserHandle.ALL, ((UsbDT) usbDT).getUSB_INFilter(), null, null);
        //registerReceiverAsUser(usbDT, UserHandle.ALL, ((UsbDT) usbDT).getUSB_OUTFilter(), null, null);
        registerReceiver(usbDT, ((UsbDT) usbDT).getUSB_INFilter());
        registerReceiver(usbDT, ((UsbDT) usbDT).getUSB_OUTFilter());
        Log.i(LOG_TAG, "InitUsbDT end.");
    }

}