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
     * 撮影完了時に呼ばれ、センサー停止を通知した上で保存ファイルのパスをDCIM配下の相対パスへ変換し、データベースへ登録する。
     *
     * 受け取った fileUrls の各要素はフルパスからアプリ外部ファイルの DCIM 親ディレクトリ部分を取り除いて相対パスに変換され、notificationDatabaseUpdate に渡される。
     * mIsDone が真のときは OLED 表示をプラグイン名に戻す。
     *
     * @param fileUrls 保存済みファイルのフルパス配列（このメソッド内で DCIM 以下の相対パスへ変換される）
     * @param mIsDone  連射処理の最終ファイルであれば `true`、それ以外は `false`
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