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

        if (deviceList.isEmpty()) {
            // Log.d("UsbVideoHelper", "No USB devices found.");
            return null;
        }

        // Iterate over all devices
        for (UsbDevice device : deviceList.values()) {
            // Log.d("UsbVideoHelper", "Found USB device: " + device.getDeviceName() + " (Vendor ID: " + device.getVendorId() + ", Product ID: " + device.getProductId() + ")");
            // Return the first device found
            return device;
        }

        // Log.d("UsbVideoHelper", "No USB devices found.");
        return null;
    }
}