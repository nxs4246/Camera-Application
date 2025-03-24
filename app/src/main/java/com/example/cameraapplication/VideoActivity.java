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

public class VideoActivity extends Activity implements SurfaceHolder.Callback {

    private static final String ACTION_USB_PERMISSION = "com.example.cameraapplication.USB_PERMISSION";
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private UsbEndpoint videoEndpoint;
    private boolean isCapturing = false;
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

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbDevice = UsbVideoHelper.findUvcDevice(this);

        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
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

    private void openUsbDevice(UsbDevice device) {
        usbConnection = usbManager.openDevice(device);
        if (usbConnection != null) {
            startVideoCapture();
        } else {
            Log.e("VideoActivity", "Failed to open USB device");
            mainHandler.post(() -> Toast.makeText(this, "Failed to connect to USB device", Toast.LENGTH_SHORT).show());
        }
    }

    private void startVideoCapture() {
        if (usbConnection == null) {
            Log.e("VideoActivity", "USB connection is null");
            mainHandler.post(() -> Toast.makeText(this, "USB connection error", Toast.LENGTH_SHORT).show());
            return;
        }

        UsbInterface videoInterface = null;
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface iface = usbDevice.getInterface(i);
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_VIDEO) {
                videoInterface = iface;
                break;
            }
        }

        if (videoInterface == null) {
            Log.e("VideoActivity", "Video interface not found");
            mainHandler.post(() -> Toast.makeText(this, "Video interface error", Toast.LENGTH_SHORT).show());
            return;
        }

        if (usbConnection.claimInterface(videoInterface, true)) {
            for (int i = 0; i < videoInterface.getEndpointCount(); i++) {
                UsbEndpoint endpoint = videoInterface.getEndpoint(i);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    videoEndpoint = endpoint;
                    break;
                }
            }

            if (videoEndpoint == null) {
                Log.e("VideoActivity", "Video endpoint not found");
                mainHandler.post(() -> Toast.makeText(this, "Video endpoint error", Toast.LENGTH_SHORT).show());
                usbConnection.releaseInterface(videoInterface);
                return;
            }

            isCapturing = true;
            startVideoReading();

        } else {
            Log.e("VideoActivity", "Failed to claim video interface");
            mainHandler.post(() -> Toast.makeText(this, "Failed to claim video interface", Toast.LENGTH_SHORT).show());
        }
    }

    private void startVideoReading() {
        executor.execute(() -> {
            if (usbConnection == null) {
                Log.e("VideoActivity", "USB connection is null");
                mainHandler.post(() -> Toast.makeText(this, "USB connection error", Toast.LENGTH_SHORT).show());
                isCapturing = false;
                return;
            }
            if (videoEndpoint == null) {
                Log.e("VideoActivity", "Video endpoint is null");
                mainHandler.post(() -> Toast.makeText(this, "Video endpoint error", Toast.LENGTH_SHORT).show());
                isCapturing = false;
                return;
            }

            ByteBuffer buffer = ByteBuffer.allocate(videoEndpoint.getMaxPacketSize() * 1024);
            UsbRequest request = new UsbRequest();
            request.initialize(usbConnection, videoEndpoint);

            while (isCapturing) {
                if (request.queue(buffer, buffer.capacity())) {
                    if (usbConnection.requestWait() == request) {
                        byte[] data = buffer.array();
                        mainHandler.post(() -> displayFrame(data));
                        buffer.clear();
                    } else {
                        Log.e("VideoActivity", "requestWait failed");
                        mainHandler.post(() -> Toast.makeText(this, "Video capture error", Toast.LENGTH_SHORT).show());
                        isCapturing = false;
                    }
                } else {
                    Log.e("VideoActivity", "request.queue failed");
                    mainHandler.post(() -> Toast.makeText(this, "Video capture error", Toast.LENGTH_SHORT).show());
                    isCapturing = false;
                }
            }
            request.close();

        });
    }

    private void displayFrame(byte[] data) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap != null) {
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawBitmap(bitmap, 0, 0, null);
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
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
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isCapturing = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        executor.shutdown();
    }
}