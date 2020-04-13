package com.elotouch.flirone;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import static com.elotouch.flirone.FlirCameraApplication.cameraHandler;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String ACTION_START_FLIR_ONE = "ACTION_START_FLIR_ONE";
    public static final String ACTION_START_SIMULATOR_ONE = "ACTION_START_SIMULATOR_ONE";
    public static final String ACTION_START_SIMULATOR_TWO = "ACTION_START_SIMULATOR_TWO";

    //Handles Android permission for eg Network
    public PermissionHandler permissionHandler;

    private TextView discoveryStatus;

    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }
    public ShowMessage showMessage = message -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.DarkTheme);
        setContentView(R.layout.activity_main);

        // Initialize Thermal SDK
        ThermalSdkAndroid.init(getApplicationContext(), ThermalLog.LogLevel.WARNING);

        // Initialize Permission Handler
        permissionHandler = new PermissionHandler(showMessage, MainActivity.this);

        // Initialize Camera Handler
        cameraHandler = new CameraHandler();

        // Initialize TextViews
        discoveryStatus = findViewById(R.id.discovery_status);
        TextView sdkVersionTextView = findViewById(R.id.sdk_version);

        // Show Thermal Android SDK version
        String sdkVersionText = getString(R.string.sdk_version_text, ThermalSdkAndroid.getVersion());
        sdkVersionTextView.setText(sdkVersionText);

    }

    public void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    public void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Connect to the Flir One Camera, starts the FlirCameraActivity
     * @param view the button pressed
     */
    public void connectFlirOne(@Nullable View view) {
        Intent intent = new Intent(getApplicationContext(), FlirCameraActivity.class);
        intent.setAction(ACTION_START_FLIR_ONE);
        startActivity(intent);
    }

    /**
     * Connect to the Flir One C++ Emulator, starts the FlirCameraActivity
     * @param view the button pressed
     */
    public void connectSimulatorOne(@Nullable View view) {
        Intent intent = new Intent(getApplicationContext(), FlirCameraActivity.class);
        intent.setAction(ACTION_START_SIMULATOR_ONE);
        startActivity(intent);
    }

    /**
     * Connect to the Flir One Emulator, starts the FlirCameraActivity
     * @param view the button pressed
     */
    public void connectSimulatorTwo(@Nullable View view) {
        Intent intent = new Intent(getApplicationContext(), FlirCameraActivity.class);
        intent.setAction(ACTION_START_SIMULATOR_TWO);
        startActivity(intent);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar,menu);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.mipmap.ic_icon_elo_round);
        } else {
            Log.e(TAG, "onCreateOptionsMenu: getSupportActionBar returned null");
        }
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.toolbar_discover:
                startDiscovery();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + Arrays.toString(permissions) + "], grantResults = [" + Arrays.toString(grantResults) + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, grantResults);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    public CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            discoveryStatus.setText(R.string.discovery_status_discovering);
        }

        @Override
        public void stopped() {
            discoveryStatus.setText(R.string.discovery_status_text);
        }
    };

    /**
     * cameraDiscoveryListener is notified if a new camera was found during a active discovery phase
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            runOnUiThread(() -> {
                if(identity.deviceId.contains("EMULATED FLIR ONE")){
                    findViewById(R.id.connect_s2).setVisibility(View.VISIBLE);
                } else if (identity.deviceId.contains("C++ Emulator")){
                    findViewById(R.id.connect_s1).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.connect_flir_one).setVisibility(View.VISIBLE);
                }
                cameraHandler.addFoundCameraIdentity(identity);
                MainActivity.this.showMessage.show("Camera Found: " + identity);
                stopDiscovery();
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.e(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(() -> {
                stopDiscovery();
                MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            });
        }

        @Override
        public void onDiscoveryFinished(CommunicationInterface communicationInterface) {
            Log.d(TAG, "onDiscoveryFinished: Discovery Finished");
        }
    };
}