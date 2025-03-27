package com.example.cameraapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;

public class VideoActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "VideoActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.cameraapplication.USB_PERMISSION";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private UsbEndpoint videoEndpoint;
    private UsbInterface videoInterface;

    private TextView statusTextView;

    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    private final AtomicBoolean shouldRetry = new AtomicBoolean(true);
    private int retryAttempts = 0;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            openUsbDevice(device);
                        }
                    } else {
                        Log.e(TAG, "USB permission denied");
                        Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbDevice = UsbVideoHelper.findUvcDevice(this);

        permissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        if (usbDevice != null) {
            usbManager.requestPermission(usbDevice, permissionIntent);
        } else {
            Toast.makeText(this, "No UVC device found", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String message) {
        mainHandler.post(() -> {
            if (statusTextView != null) {
                statusTextView.setText(message);
            }
        });
    }

    public void openUsbDevice(UsbDevice device) {
        String deviceInfo = String.format(Locale.ENGLISH, "USB Device Connected: %s\nVendor ID: %d, Product ID: %d",
                device.getDeviceName(),
                device.getVendorId(),
                device.getProductId()
        );
        updateStatus(deviceInfo);

        usbConnection = usbManager.openDevice(device);
        if (usbConnection != null) {
            startVideoCapture();
        } else {
            Log.e(TAG, "Failed to open USB device");
            mainHandler.post(() -> Toast.makeText(this,
                    "Failed to connect to USB device", Toast.LENGTH_SHORT).show());
        }
    }

    public void startVideoCapture() {
        if (usbConnection == null) {
            Log.e(TAG, "USB connection is null");
            return;
        }

        videoInterface = findVideoInterface();
        if (videoInterface == null) {
            Log.e(TAG, "Video interface not found");
            return;
        }

        if (usbConnection.claimInterface(videoInterface, true)) {
            videoEndpoint = findVideoEndpoint(videoInterface);
            if (videoEndpoint == null) {
                Log.e(TAG, "Video endpoint not found");
                usbConnection.releaseInterface(videoInterface);
                return;
            }

            isCapturing.set(true);
            shouldRetry.set(true);
            retryAttempts = 0;
            startVideoReading();
        } else {
            Log.e(TAG, "Failed to claim video interface");
        }
    }

    private UsbInterface findVideoInterface() {
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface iface = usbDevice.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_VIDEO) {
                return iface;
            }
        }
        return null;
    }

    private UsbEndpoint findVideoEndpoint(UsbInterface videoInterface) {
        for (int i = 0; i < videoInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = videoInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                return endpoint;
            }
        }
        return null;
    }

    public void startVideoReading() {
        executor.execute(() -> {
            while (isCapturing.get() && shouldRetry.get() && retryAttempts < MAX_RETRY_ATTEMPTS) {
                try {
                    readVideoStream();
                } catch (Exception e) {
                    Log.e(TAG, "Video capture error", e);
                    retryAttempts++;

                    if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Failed to capture video", Toast.LENGTH_SHORT).show();
                            isCapturing.set(false);
                            shouldRetry.set(false);
                        });
                    }
                }
            }
        });
    }

    private void readVideoStream() {
        ByteBuffer buffer = ByteBuffer.allocate(videoEndpoint.getMaxPacketSize() * 1024);
        UsbRequest request = new UsbRequest();
        request.initialize(usbConnection, videoEndpoint);

        if (request.queue(buffer, buffer.capacity())) {
            if (usbConnection.requestWait() == request) {
                byte[] data = buffer.array();
                mainHandler.post(() -> displayFrame(data));
            }
        }
        request.close();
    }

    public void displayFrame(byte[] data) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap != null) {
                Canvas canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying frame", e);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (usbConnection != null) {
            startVideoCapture();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Potential future implementation for resize
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopVideoCapture();
    }

    private void stopVideoCapture() {
        isCapturing.set(false);
        shouldRetry.set(false);

        if (usbConnection != null && videoInterface != null) {
            usbConnection.releaseInterface(videoInterface);
            usbConnection.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVideoCapture();
        unregisterReceiver(usbReceiver);
        executor.shutdown();
    }
}