package com.aptiv.aptivusbotg;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.PermissionChecker;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.os.UserHandle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.util.Log;

import android.text.TextUtils;
import android.net.Uri;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.concurrent.Executor;



class UsbDT extends BroadcastReceiver {

    public String mountPath = "";
    private final static String LOG_TAG = "[AptivUsbOtg][UsbDT]";
    private UsbDT.onNotify onNotifyListener = null;

    public static boolean FileExists(String path) {

        if (path == null){
            return false;
        }

        try {
            Path p = Paths.get(path);
            boolean exists = Files.exists(p);
            if (!exists){
                File file = new File(path);
                exists =  file.exists();
            }
            return exists;
        } catch (InvalidPathException e) {
            Log.w(LOG_TAG, "file path error：" + e.getMessage());
        } catch (SecurityException e) {
            Log.w(LOG_TAG, "file path error：" + e.getMessage());
        } catch (Exception e) {
            Log.w(LOG_TAG, "file path uncatched error：" + e.getMessage());
        }
        return false;
    }

    public static void RunCommand(String cmd){
        try {
            Process process = Runtime.getRuntime().exec(cmd);
    
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
    
            int exitCode = process.waitFor();
            Log.d(LOG_TAG, cmd + "\nExit Code: " + exitCode + "\nOutput: " + output);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public IntentFilter getUSB_INFilter() {
        IntentFilter intent_filter = new IntentFilter();
        intent_filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intent_filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intent_filter.addDataScheme("file");
        //Log.i(LOG_TAG, "get UsbDT intent_filter");
        return intent_filter;
    }

    /**
     * 根据label获取外部存储路径
     * 
     * @param context
     * @param label   内部存储:Internal shared storage SD卡:SDCARD USB:USB
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public String getExternalPath(Context context, String label) {
        String path = null;
        StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = mStorageManager.getStorageVolumes();
        try {
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getDirectory = storageVolumeClazz.getMethod("getDirectory"); // version > android 11
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            for (int i = 0; i < volumes.size(); i++) {
                StorageVolume storageVolume = volumes.get(i);
                File f = (File) getDirectory.invoke(storageVolume);
                if (f == null) {
                    continue;
                }
                String storagePath = f.getAbsolutePath(); // 获取路径
                boolean isRemovableResult = (boolean) isRemovable.invoke(storageVolume);// 是否可移除
                String description = storageVolume.getDescription(context);
                Log.i(LOG_TAG, " i=" + i + " ,storagePath=" + storagePath + " ,description=" + description);

                // if (label.equals(description)){
                // Log.i(LOG_TAG, " i=" + i + " ,storagePath=" + storagePath + " ,description="
                // + description);
                // path = storagePath;
                // break;
                // }
                if (description.indexOf(label) != -1) {
                    Log.i(LOG_TAG, " i=" + i + " ,storagePath=" + storagePath + " ,description=" + description);
                    path = storagePath;
                    int firstSlashIndex = path.indexOf('/');
                    String tmpath = "";
                    if (firstSlashIndex != -1)
                        {
                         // first
                        int secondSlashIndex = path.indexOf('/', firstSlashIndex + 1);
                        if (secondSlashIndex != -1) {
                            // second
                            tmpath = path.substring(secondSlashIndex + 1);
                        }
                    }

                    Log.i(LOG_TAG,"tmpath:" +tmpath);
                    
                    //path = "/mnt/media_rw/" + tmpath;
                    //printAllFiles(path);

                    if(tmpath != "emulated")
                        {
                            path = "ok";
                    }
                    

                    
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, " Exception:" + e);
        }
        return path;
    }

    private void printAllFiles(String path) {
        File root = new File(path);
        if (!root.exists() || !root.isDirectory()) {
            Log.e(LOG_TAG, "path is not exsist or is not dir:" + path);
            return;
        }

        File[] files = root.listFiles();
        if (files == null) {
            Log.e(LOG_TAG, "can not read dir: " + path);
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                Log.i(LOG_TAG, "dir: " + file.getAbsolutePath());
                printAllFiles(file.getAbsolutePath()); // 递归打印子目录
            } else {
                Log.i(LOG_TAG, "file:" + file.getAbsolutePath() + ",size: " + file.length() + " bytes");
            }
        }
    }

    public boolean is_usb_device_pending(Context context) {

        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = storageManager.getStorageVolumes();
        try {

            for (StorageVolume volume : volumes){
                File directory = volume.getDirectory(); 
                boolean isRemovable = volume.isRemovable();
                String storagePath = directory != null ? directory.getAbsolutePath() : "Unmounted";
                String description = volume.getDescription(context);
                String state = volume.getState();

                if (state.equals(Environment.MEDIA_CHECKING)){
                    Log.i(LOG_TAG, "[found device softlinking][checking] description:" + description);
                    return true;
                }

                if (storagePath.equals("Unmounted")){
                    Log.i(LOG_TAG, "[found device softlinking][umounted] description:" + description);
                    return true;
                }

                if (state.equals(Environment.MEDIA_MOUNTED) && !FileExists(storagePath)) {
                    Log.i(LOG_TAG, "[found device softlinking][mounted] description:" + description);
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, " Exception:" + e);
        }

        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean checkUsbUpgrade(Context context, String target, String fbl_usb_force_flag, boolean enable_force_update) {

        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = storageManager.getStorageVolumes();

        try {

            for (StorageVolume volume: volumes){
                File directory = volume.getDirectory(); 
                String storagePath = directory != null ? directory.getAbsolutePath() : "Unmounted";
                boolean isRemovable = volume.isRemovable();
                String description = volume.getDescription(context);
                String state = volume.getState();

                if (!isRemovable){
                    Log.i(LOG_TAG, "[ignored][" + storagePath + "] is not removable");
                    continue;
                }

                Log.d(LOG_TAG, "[StorageVolume] Path: " + storagePath + ", Type: " + description + ", State: " + state);

                // remap USB disk root path #FIXME#
                String mountpoint = storagePath;
                if (!state.equals(Environment.MEDIA_MOUNTED)){
                    Log.w(LOG_TAG, "[StorageVolume][not MEDIA_MOUNTED] Path: " + storagePath + ", Type: " + description + ", State: " + state + ", MEDIA_MOUNTED:" + Environment.MEDIA_MOUNTED);
                    continue;
                }

                if(!checkUsbPath(storagePath)) {
                    continue;
                }

                if (enable_force_update){
                    if (!FileExists(storagePath + "/" + fbl_usb_force_flag)) {
                        Log.w(LOG_TAG, "[FOUND UDISK] but [NOT FOUND force update flag]");
                        continue;
                    }
                }

                if (checkUsbUpgradePackage(storagePath, target)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, " Exception:" + e);
        }
        return false;
    }

    private synchronized boolean checkUsbPath(String path) {
        if (path == null) {
            Log.i(LOG_TAG, "checkUsbPath path is null");
            return false;
        }

        String usb_path = path;
        File file = new File(usb_path);
        if (!file.exists()) {
            Log.i(LOG_TAG, usb_path + " not exist");
            return false;
        } else {
            if (!file.isDirectory()) {
                Log.i(LOG_TAG, usb_path + " is not dir");
                return false;
            }
        }
        return true;
    }

    private synchronized boolean checkUsbUpgradePackage(String path, String target) {
        if (path == null || target == null) {
            Log.i(LOG_TAG, "checkUsbUpgradePackage  is null");
            return false;
        }

        if (path.length() <= 0 || target.length() <= 0) {
            Log.i(LOG_TAG, "checkUsbUpgradePackage  len is 0");
            return false;
        }

        String usb_path = path + "/" + target;
        File file = new File(usb_path);
        if (!file.exists()) {
            Log.i(LOG_TAG, usb_path + " not exist");
            return false;
        } else {
            if (!file.isDirectory()) {
                Log.i(LOG_TAG, usb_path + " is not dir");
                return false;
            }
        }
        return true;
    }

    public interface onNotify {
        public void onNotifyUsbPath(String path);
    }

    public void setonNotifyListener(UsbDT.onNotify listener) {
        this.onNotifyListener = listener;
    }

    public IntentFilter getUSB_OUTFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
        return intentFilter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action == null || action == "") {
            Log.i(LOG_TAG, "[OTA] UsbDT onReceive null action");
            return;
        }

        Log.i(LOG_TAG, action);

        if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            mountPath = intent.getData().getPath();
            if (!TextUtils.isEmpty(mountPath)) {
                if (this.onNotifyListener != null) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        this.onNotifyListener.onNotifyUsbPath(mountPath);
                    }, 1000);
                }
            }
        } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)){
            Log.i(LOG_TAG, "Intent.ACTION_MEDIA_SCANNER_FINISHED");
        } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED) || action.equals(Intent.ACTION_MEDIA_EJECT)) {
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
        }
    }
}
