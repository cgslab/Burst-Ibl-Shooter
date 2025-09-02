/**
 * Copyright 2018 Ricoh Company, Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.cgslab.burstiblshooter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;

import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.activity.ThetaInfo;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;
import com.theta360.pluginlibrary.values.OledDisplay;
import com.theta360.pluginlibrary.values.TextArea;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class MainActivity extends PluginActivity implements CameraFragment.CFCallback {

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private boolean mIsEnded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set enable to close by pluginlibrary, If you set false, please call close() after finishing your end processing.
        setAutoClose(true);
        // Set a callback when a button operation event is acquired.
        setKeyCallback(new KeyCallback() {
            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    takePicture();
                }
            }

            @Override
            public void onKeyUp(int keyCode, KeyEvent event) {
                /**
                 * You can control the LED of the camera.
                 * It is possible to change the way of lighting, the cycle of blinking, the color of light emission.
                 * Light emitting color can be changed only LED3.
                 */
                notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 1000);
            }

            @Override
            public void onKeyLongPress(int keyCode, KeyEvent event) {
                if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD) {
                    endProcess();
                }
            }
        });

        // 権限とバージョンと対象thetaのチェック
        if (hasPermission() && checkVersion() && isZ1()) {
            notificationWlanOff();
            notificationCameraClose();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        endProcess();

        super.onPause();
    }


    @Override
    public void onShutter() {
        notificationAudioShutter();
    }

    /**
     * Handles post-capture processing for one or more saved files.
     *
     * Stops the sensor, normalizes the provided absolute file paths to be relative to
     * the DCIM directory (so they can be accepted by the media database), updates
     * the media database with those paths, and—if this call marks the final frame
     * of a burst—restores the OLED display to the plugin name.
     *
     * @param fileUrls an array of absolute file paths produced by the camera; each
     *                 element will be converted to a DCIM-relative path before
     *                 calling the media database update routine
     * @param mIsDone  true when this invocation corresponds to the final image in
     *                 a burst sequence (triggers OLED reset to plugin name)
     */
    @Override
    public void onPictureTaken(String[] fileUrls, boolean mIsDone) {

        notificationSensorStop();

        /**
         * The file path specified in "notificationDatabaseUpdate"
         * specifies the file path or directory path under the DCIM directory.
         * Replace file path because fileUrls has full path set
         */
        File dcimDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
        String storagePath = dcimDir != null ? dcimDir.getParent() : "";
        for (int i = 0; i < fileUrls.length; i++) {
            fileUrls[i] = fileUrls[i].replace(storagePath, "");
        }
        Timber.d(fileUrls.toString());
        notificationDatabaseUpdate(fileUrls);

        // 連射最後の枚数の時にディスプレイの表示をプラグイン名に戻す
        if (mIsDone) {
            displayOled("Burst-IBL-Shooter", "");
        }
    }

    private void takePicture() {
        Fragment fragment;
        fragment = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (fragment != null && fragment instanceof CameraFragment) {
            if (!(((CameraFragment) fragment).isCapturing()) && !(((CameraFragment) fragment).isBusting())) {
                displayOled("Writeing .DNG FILE. CAN MOVE THETA", "");
                notificationSensorStart();
                ((CameraFragment) fragment).takePicture();
            }
        }
    }

    private void displayOled(String text, String errorText) {
        notificationOledDisplaySet(OledDisplay.DISPLAY_PLUGIN);
        Map<TextArea, String> output = new HashMap<>();
        output.put(TextArea.MIDDLE, text);
        output.put(TextArea.BOTTOM, errorText);
        notificationOledTextShow(output);

    }


    private void endProcess() {
        if (!mIsEnded) {
            Fragment fragment;
            fragment = getSupportFragmentManager().findFragmentById(R.id.fragment);
            if (fragment != null && fragment instanceof CameraFragment) {
                ((CameraFragment) fragment).close();
            }
            close();
            mIsEnded = true;
        }
    }

    private boolean hasPermission() {

        if (checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            notificationError("Permissions are not granted.");
            return false;
        }

    }

    private boolean checkVersion() {

        String version = ThetaInfo.getThetaFirmwareVersion(this);
        String[] versionSplit = version.split("\\.");
        if (versionSplit[0].compareTo("2") >= 0) {
            return true;
        } else {
            notificationError("Firmware version is old.");
            return false;
        }
    }

    private boolean isZ1() {
        if (Build.MODEL.equals(Constants.MODEL_Z1)) {
            return true;
        } else {
            notificationError("This Plugin only launches Z1");
            return false;
        }
    }

}
