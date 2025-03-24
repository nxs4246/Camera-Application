package com.example.cameraapplication;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import java.util.HashMap;

public class UsbVideoHelper {

    public static UsbDevice findUvcDevice(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        for (UsbDevice device : deviceList.values()) {
            // UVC devices typically have the video class (0x0E)
            // can add your own
            if (device.getDeviceClass() == 0x0E) {
                Log.d("UsbVideoHelper", "Found UVC device: " + device.getDeviceName());
                return device;
            }
        }
        Log.d("UsbVideoHelper", "No UVC device found.");
        return null;
    }
}