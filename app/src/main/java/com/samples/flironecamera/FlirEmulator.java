package com.samples.flironecamera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static com.samples.flironecamera.FlirCameraContext.cameraHandler;
import static com.samples.flironecamera.FlirCameraContext.connectedIdentity;

public class FlirEmulator extends Activity {
    private static final String TAG = "FlirEmulator";
    private MainActivity.ShowMessage showMessage = message -> Toast.makeText(FlirEmulator.this, message, Toast.LENGTH_SHORT).show();
    public UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    public LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);



    private TextView connectionStatus;
    private TextView discoveryStatus;

    private ImageView msxImage;
    private ImageView photoImage;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DarkTheme);
        setContentView(R.layout.flir_emulator_main);
        msxImage = findViewById(R.id.msx_image);
        photoImage = findViewById(R.id.photo_image);
        connectionStatus = findViewById(R.id.connection_status_text);
        discoveryStatus = findViewById(R.id.discovery_status);
        // Show Thermal Android SDK version
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);
        String sdkVersionText = getString(R.string.sdk_version_text, ThermalSdkAndroid.getVersion());
        sdkVersionTextView.setText(sdkVersionText);

        connectSimulatorTwo();
//        ((MainActivity) getApplicationContext()).connectedIdentity

    }
    public void connectSimulatorTwo() {
        connect(cameraHandler.getFlirOneEmulator());
    }

    public void onClickDisconnectFlirEmulator(View view){
        disconnect();
        finish();
    }

    public void switchFilter(View view){
        if(findViewById(R.id.msx_image).getVisibility() == View.VISIBLE){
            findViewById(R.id.msx_image).setVisibility(View.INVISIBLE);
            findViewById(R.id.photo_image).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.msx_image).setVisibility(View.VISIBLE);
            findViewById(R.id.photo_image).setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING");
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnect();
            runOnUiThread(() -> updateConnectionText(null, "DISCONNECTED"));
        }).start();
    }

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            showMessage.show("connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
            doConnect(identity);
        }

    }

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    updateConnectionText(identity, "CONNECTED");
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Update the UI text for connection status
     */
    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? " " + identity.deviceId : "";
        connectionStatus.setText("Connection Status:" + deviceId + " " + status);
    }

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    public ConnectionStatusListener connectionStatusListener = errorCode -> {
        Log.d(TAG, "onDisconnected errorCode:" + errorCode);

        runOnUiThread(() -> updateConnectionText(connectedIdentity, "DISCONNECTED"));
    };

    public final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {

            runOnUiThread(() -> {
                msxImage.setImageBitmap(dataHolder.msxBitmap);
                photoImage.setImageBitmap(dataHolder.dcBitmap);
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {

            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap,dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG,"images(), unable to add incoming images to frames buffer, exception:"+e);
            }

            runOnUiThread(() -> {
                Log.d(TAG,"framebuffer size:"+framesBuffer.size());
                FrameDataHolder poll = (FrameDataHolder) framesBuffer.poll();
                if(poll != null) {
                    msxImage.setImageBitmap(poll.msxBitmap);
                    photoImage.setImageBitmap(poll.dcBitmap);
                }
            });

        }
    };

    public UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(@NotNull Identity identity) {
            doConnect(identity);
        }

        @Override
        public void permissionDenied(@NotNull Identity identity) {
            FlirEmulator.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            FlirEmulator.this.showMessage.show("Error when asking for permission for FLIR ONE, error:"+errorType+ " identity:" +identity);
        }
    };
}
