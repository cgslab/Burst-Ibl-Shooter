package info.cgslab.burstiblshooter;

import static android.content.Context.MODE_PRIVATE;
import static info.cgslab.burstiblshooter.Constants.burstCount;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.theta360.pluginlibrary.activity.ThetaInfo;
import com.theta360.pluginlibrary.exif.CameraAttitude;
import com.theta360.pluginlibrary.exif.CameraSettings;
import com.theta360.pluginlibrary.exif.DngExif;
import com.theta360.pluginlibrary.exif.SensorValues;
import com.theta360.pluginlibrary.exif.values.SphereType;
import com.theta360.pluginlibrary.values.ThetaModel;

import org.apache.sanselan.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

public class CameraFragment extends Fragment {
    public static final String FILENAME = "fileName";
    public String SAVEDIR = "";
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private CameraAttitude mCameraAttitude;
    private Camera.Parameters mParameters;
    private Camera.CameraInfo mCameraInfo;
    private CFCallback mCallback;
    private int mCameraId;
    private boolean mIsCapturing = false;
    private boolean mIsDuringExposure = false;
    private boolean mIsBusting = false;
    private boolean mIsSurface = false;
    private boolean mIsEnd = false;
    private int count = 0;
    private int dngcount = 0;
    private int fileNo = 0;
    private final int nextDirNo;
    private File[] files;


    private final Camera.ErrorCallback mErrorCallback = new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {

        }
    };
    private final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            mIsSurface = true;
            open();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            setSurface(surfaceHolder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            mIsSurface = false;
            close();
        }
    };
    private final Camera.ShutterCallback onShutterCallback = new Camera.ShutterCallback() {

        @Override
        public void onShutter() {
            if (mIsCapturing && !mIsDuringExposure) {
                mIsDuringExposure = true;
                mIsCapturing = false;

                /*
                 * Hold the current value of the attitude sensor
                 * - It will be used later as setting value for Metadata.
                 */
                mCameraAttitude.snapshot();

                if (mCallback != null) {
                    mCallback.onShutter();
                }
            } else if (!mIsCapturing && mIsDuringExposure) {
                mIsDuringExposure = false;

                /*
                 * Acquire the camera parameters for metadata at the completion of exposure
                 */
                mParameters = mCamera.getParameters();
                CameraSettings.setCameraParameters(mParameters);
            } else {
                mIsCapturing = false;
                mIsDuringExposure = false;
            }
        }
    };

    private final Camera.PictureCallback onJpegPictureCallback = new Camera.PictureCallback() {

        /**
         * 撮影された画像データを受け取り、メタデータを付与したDNGファイルを作成してコールバックへ通知する。
         *
         * <p>このメソッドは撮影フレームのカウント管理を行い、姿勢センサ値や球体タイプなどの撮影メタデータを設定した上で、
         * 一時DNGファイルを読み込みメタデータ（GPS、球体情報、メーカー情報）を埋め込んだDNGを出力し、
         * 登録したコールバックにファイルURL一覧と撮影終了フラグを渡します。撮影セッションの終了時には内部の状態とカウンタをリセットします。</p>
         *
         * @param data   撮影されたJPEG画像のバイト配列（未加工の画像データ）
         * @param camera 呼び出し元のCameraインスタンス（プレビューやパラメータにアクセスするために使用される）
         */
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            mParameters.set("RIC_PROC_STITCHING", "RicStaticStitching");
            mCamera.setParameters(mParameters);

            count++;
            if (count == burstCount) {
                mIsEnd = true;
            }
            Timber.d("onPictureTaken");
            /*
             * Create Exif object with image data holding incomplete Metadeta
             */
            //Exif exif = new Exif(data, true);

            /*
             * Set Sphere type
             */
            CameraSettings.setSphereType(SphereType.EQUIRECTANGULAR);

            /*
             * Set attitude sensor value
             * - Get the sensor value already held and sets it to the camera settings.
             */
            SensorValues sensorValues = new SensorValues();
            sensorValues.setAttitudeRadian(mCameraAttitude.getAttitudeRadianSnapshot());
            sensorValues.setCompassAccuracy(mCameraAttitude.getAccuracySnapshot());
            CameraSettings.setSensorValues(sensorValues);

            /*
             * Set correct value for Receptor-IFD in MakerNote.
             */
            //exif.setExifSphere();
            /*
             * Set correct value for MakerNote.
             */
            //exif.setExifMaker();
            /*
             * Get Exif data
             * - Acquires the image data in which the correct Metadata has been set.
             */
            //byte[] exifData = exif.getExif();
            String fileUrl = getFileName();
            List<String> fileUrls = new ArrayList<String>();
            //fileUrls.add(fileUrl);
            /**
             * The following DNG file is output by enabling DNG output and executing still image shooting.
             */
            ///DCIM/0/temp0.dng
            File dngTempFile = new File(getContext().getExternalFilesDir(null).getPath() + "/temp" + dngcount + ".dng");
            String dngFileUrl = fileUrl.replace(".JPG", ".DNG");
            fileUrls.add(dngFileUrl);
            /**
             * Copy temp.dng and give the same metadata as the jpeg file.
             */
            try (FileInputStream dngInputStream = new FileInputStream(dngTempFile);
                 FileOutputStream dngOutputStream = new FileOutputStream(dngFileUrl)) {
                byte[] dngData = IOUtils.getInputStreamBytes(dngInputStream);
                DngExif dngExif = new DngExif(dngData, false);
                dngExif.setExifGPS();
                dngExif.setExifSphere();
                dngExif.setExifMaker();
                //dngExif.replaceJpeg(exifData);
                dngOutputStream.write(dngExif.getExif());
            } catch (IOException e) {
                e.printStackTrace();
            }

            mCallback.onPictureTaken(fileUrls.toArray(new String[fileUrls.size()]), mIsEnd);
            ++dngcount;
            if (mIsEnd) {
                mIsCapturing = false;
                mIsBusting = false;
                mIsEnd = false;
                count = 0;
                dngcount = 0;
                fileNo=0;
            }

        }
    };

    public CameraFragment() {
        nextDirNo = 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        SurfaceView surfaceView = (SurfaceView) view.findViewById(R.id.surfaceView);
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceHolderCallback);

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof CFCallback) {
            mCallback = (CFCallback) context;
        }

        /*
         * Attitude sensor start
         */
        mCameraAttitude = new CameraAttitude(context);
        mCameraAttitude.register();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mIsSurface) {
            open();
            setSurface(mSurfaceHolder);
        }
    }

    @Override
    public void onStop() {
        close();
        super.onStop();
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mCallback = null;

        /*
         * Attitude sensor stop
         */
        mCameraAttitude.unregister();
    }

    public boolean isCapturing() {
        return mIsCapturing;
    }

    public boolean isBusting() {
        return mIsBusting;
    }

    public void takePicture() {
        if (!mIsCapturing) {
            createDir();

            mIsCapturing = true;
            mIsBusting = true;
            mCamera.stopPreview();
            ThetaModel thetaModel = ThetaModel.getValue(ThetaInfo.getThetaModelName());

            CameraSettings.setManufacturer("RICOH");
            CameraSettings.setThetaSerialNumber(ThetaInfo.getThetaSerialNumber());
            CameraSettings.setThetaFirmwareVersion(ThetaInfo.getThetaFirmwareVersion(getContext()));
            CameraSettings.setThetaModel(thetaModel);
            mParameters.setPictureSize(6720, 3360);
            mParameters.set("RIC_SHOOTING_MODE", "RicStillCaptureStdBurst");
            mParameters.set("RIC_DNG_OUTPUT_ENABLED", 1);
            mParameters.set("RIC_AEC_BURST_CAPTURE_NUM", burstCount);
            mParameters.set("RIC_AEC_BURST_BRACKET_STEP", 9);
            mParameters.set("RIC_AEC_BURST_COMPENSATION", -15);
            mParameters.set("RIC_AEC_BURST_MAX_EXPOSURE_TIME", 36);
            mParameters.set("RIC_AEC_BURST_ENABLE_ISO_CONTROL", 1);
            mCamera.setParameters(mParameters);

            mCamera.takePicture(onShutterCallback, null, onJpegPictureCallback);
            Timber.d("start takePicture");

        }
    }

    /**
     * アプリの外部ファイル配下（DCIM のサブディレクトリ）にタイムスタンプ付きの保存ディレクトリを作成し、そのパスを SAVEDIR に設定します。
     *
     * 作成されるディレクトリの形式は "<external_files_DCIM>/Burst_IBL_Shooter/BIS_yyyyMMdd_HHmm_ss" です。
     */
    public void createDir() {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmm_ss");
        Date date = new Date();
        String dateToStr = dateFormat.format(date);
        File dcimDir = getContext().getExternalFilesDir(Environment.DIRECTORY_DCIM);
        String dcimPath = (dcimDir != null) ? dcimDir.getPath() : "";
        SAVEDIR = dcimPath + "/Burst_IBL_Shooter/" + "BIS_" + dateToStr;
        File newDir = new File(SAVEDIR);
        newDir.mkdirs();
    }

    private String getFileName() {
        String fileUrl = String.format("%s/BIS_%s.DNG", SAVEDIR, String.format("%04d", fileNo));
        ++fileNo;
        SharedPreferences data = getActivity().getSharedPreferences("DataSave", MODE_PRIVATE);
        SharedPreferences.Editor editor = data.edit();
        return fileUrl;
    }


    protected void close() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.setErrorCallback(null);
            mCamera.release();
            mCamera = null;
            mIsCapturing = false;
            mIsDuringExposure = false;
            mIsBusting = false;
            mIsEnd = false;
            count = 0;
            dngcount =0;
        }
    }

    private void open() {
        if (mCamera == null) {
            int numberOfCameras = Camera.getNumberOfCameras();

            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCameraInfo = info;
                    mCameraId = i;
                }

                mCamera = Camera.open(mCameraId);
            }
            mCamera.setErrorCallback(mErrorCallback);
            mParameters = mCamera.getParameters();

            /**
             * Initialize CameraSettings for Metadata
             */
            CameraSettings.initialize();

            mParameters.set("RIC_SHOOTING_MODE", "RicMonitoring");
            mCamera.setParameters(mParameters);
        }
    }

    private void setSurface(@NonNull SurfaceHolder surfaceHolder) {
        if (mCamera != null) {
            mCamera.stopPreview();

            try {
                mCamera.setPreviewDisplay(surfaceHolder);
                mParameters.setPreviewSize(1920, 960);
                mCamera.setParameters(mParameters);
            } catch (IOException e) {
                e.printStackTrace();
                close();
            }
            mCamera.startPreview();
        }
    }

    public interface CFCallback {
        void onShutter();

        void onPictureTaken(String[] fileUrls, boolean mIsEnd);

    }
}