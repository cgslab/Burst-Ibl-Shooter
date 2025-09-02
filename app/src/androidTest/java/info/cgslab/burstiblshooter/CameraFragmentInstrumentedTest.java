package info.cgslab.burstiblshooter;

/*
 Testing library and framework used:
 - JUnit4 (android.support.test.runner.AndroidJUnit4)
 - Mockito for mocking Android framework types (Camera, SurfaceHolder, etc.)

 These are instrumented tests intended to run on a device/emulator.
 If your project uses AndroidX test artifacts, replace:
   android.support.test.InstrumentationRegistry -> androidx.test.platform.app.InstrumentationRegistry (or ApplicationProvider)
   android.support.test.runner.AndroidJUnit4 -> androidx.test.ext.junit.runners.AndroidJUnit4
*/

import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.view.SurfaceHolder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.hardware.Camera;

@RunWith(AndroidJUnit4.class)
public class CameraFragmentInstrumentedTest {

    private CameraFragment fragment;

    @Before
    public void setUp() {
        fragment = new CameraFragment();
    }

    @After
    public void tearDown() {
        // Attempt to unregister attitude sensor if it was registered via onAttach in any test
        try {
            Method onDetach = CameraFragment.class.getDeclaredMethod("onDetach");
            onDetach.setAccessible(true);
            onDetach.invoke(fragment);
        } catch (Throwable ignored) {
            // safe to ignore if not attached
        }
    }

    // Utility helpers for reflection to access private members
    private static Field field(String name) throws Exception {
        Field f = CameraFragment.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void setBool(Object target, String name, boolean value) throws Exception {
        Field f = field(name);
        f.setBoolean(target, value);
    }

    private static boolean getBool(Object target, String name) throws Exception {
        Field f = field(name);
        return f.getBoolean(target);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field f = field(name);
        f.setInt(target, value);
    }

    private static int getInt(Object target, String name) throws Exception {
        Field f = field(name);
        return f.getInt(target);
    }

    private static void setObj(Object target, String name, Object value) throws Exception {
        Field f = field(name);
        f.set(target, value);
    }

    private static Object getObj(Object target, String name) throws Exception {
        Field f = field(name);
        return f.get(target);
    }

    // A subclass that lets us detect when close() is invoked by private code paths
    public static class CloseAwareCameraFragment extends CameraFragment {
        boolean closeCalled = false;

        @Override
        protected void close() {
            closeCalled = true; // do not call super to avoid mutating internal state for these tests
        }

        boolean wasCloseCalled() {
            return closeCalled;
        }
    }

    @Test
    public void testClose_ReleasesCameraAndResetsState() throws Exception {
        Camera mockCamera = mock(Camera.class);
        setObj(fragment, "mCamera", mockCamera);
        setBool(fragment, "mIsCapturing", true);
        setBool(fragment, "mIsDuringExposure", true);
        setBool(fragment, "mIsBusting", true);
        setBool(fragment, "mIsEnd", true);
        setInt(fragment, "count", 5);
        setInt(fragment, "dngcount", 3);

        // Call the protected method directly
        Method close = CameraFragment.class.getDeclaredMethod("close");
        close.setAccessible(true);
        close.invoke(fragment);

        // Verify camera interactions
        verify(mockCamera, times(1)).stopPreview();
        verify(mockCamera, times(1)).setPreviewCallback(null);
        verify(mockCamera, times(1)).setErrorCallback(null);
        verify(mockCamera, times(1)).release();

        // Verify internal state reset
        assertFalse(getBool(fragment, "mIsCapturing"));
        assertFalse(getBool(fragment, "mIsDuringExposure"));
        assertFalse(getBool(fragment, "mIsBusting"));
        assertFalse(getBool(fragment, "mIsEnd"));
        assertEquals(0, getInt(fragment, "count"));
        assertEquals(0, getInt(fragment, "dngcount"));
        assertNull(getObj(fragment, "mCamera"));
    }

    @Test
    public void testSetSurface_Success_StartsPreviewAndDoesNotClose() throws Exception {
        CloseAwareCameraFragment f = new CloseAwareCameraFragment();
        Camera mockCamera = mock(Camera.class);
        Camera.Parameters mockParams = mock(Camera.Parameters.class);
        setObj(f, "mCamera", mockCamera);
        setObj(f, "mParameters", mockParams);

        SurfaceHolder mockHolder = mock(SurfaceHolder.class);

        Method setSurface = CameraFragment.class.getDeclaredMethod("setSurface", android.support.annotation.NonNull.class);
        // The above reflective signature is difficult due to @NonNull annotation at compile time.
        // Instead, resolve by parameter types directly:
        Method[] methods = CameraFragment.class.getDeclaredMethods();
        Method setSurfaceResolved = null;
        for (Method m : methods) {
            if (m.getName().equals("setSurface")) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && SurfaceHolder.class.isAssignableFrom(pts[0])) {
                    setSurfaceResolved = m;
                    break;
                }
            }
        }
        assertNotNull("setSurface method should be found via reflection", setSurfaceResolved);
        setSurfaceResolved.setAccessible(true);

        // Invoke the private method
        setSurfaceResolved.invoke(f, mockHolder);

        // Verify behavior
        verify(mockCamera, times(1)).stopPreview();
        verify(mockCamera, times(1)).setPreviewDisplay(mockHolder);
        verify(mockParams, times(1)).setPreviewSize(1920, 960);
        verify(mockCamera, times(1)).setParameters(mockParams);
        verify(mockCamera, times(1)).startPreview();
        assertFalse("close() should not be called on success path", f.wasCloseCalled());
    }

    @Test
    public void testSetSurface_IOException_InvokesCloseAndDoesNotStartPreview() throws Exception {
        CloseAwareCameraFragment f = new CloseAwareCameraFragment();
        Camera mockCamera = mock(Camera.class);
        Camera.Parameters mockParams = mock(Camera.Parameters.class);
        setObj(f, "mCamera", mockCamera);
        setObj(f, "mParameters", mockParams);

        SurfaceHolder mockHolder = mock(SurfaceHolder.class);
        doThrow(new IOException("boom")).when(mockCamera).setPreviewDisplay(mockHolder);

        Method setSurfaceResolved = null;
        for (Method m : CameraFragment.class.getDeclaredMethods()) {
            if (m.getName().equals("setSurface")) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && SurfaceHolder.class.isAssignableFrom(pts[0])) {
                    setSurfaceResolved = m;
                    break;
                }
            }
        }
        assertNotNull(setSurfaceResolved);
        setSurfaceResolved.setAccessible(true);

        setSurfaceResolved.invoke(f, mockHolder);

        assertTrue("close() should be called when IOException occurs", f.wasCloseCalled());
        verify(mockCamera, never()).startPreview();
    }

    @Test
    public void testOnShutterCallback_FirstCall_SetsDuringExposureAndNotifies() throws Exception {
        // Prepare initial flags: capturing = true, duringExposure = false
        setBool(fragment, "mIsCapturing", true);
        setBool(fragment, "mIsDuringExposure", false);

        // Mock attitude sensor and callback
        Object mockAttitude = mock(com.theta360.pluginlibrary.exif.CameraAttitude.class);
        setObj(fragment, "mCameraAttitude", mockAttitude);
        CameraFragment.CFCallback mockCF = mock(CameraFragment.CFCallback.class);
        setObj(fragment, "mCallback", mockCF);

        // Access and invoke onShutter callback
        Camera.ShutterCallback shutter = (Camera.ShutterCallback) getObj(fragment, "onShutterCallback");
        assertNotNull(shutter);
        shutter.onShutter();

        // Verify state transitions and interactions
        assertFalse(getBool(fragment, "mIsCapturing"));
        assertTrue(getBool(fragment, "mIsDuringExposure"));
        verify((com.theta360.pluginlibrary.exif.CameraAttitude) mockAttitude, times(1)).snapshot();
        verify(mockCF, times(1)).onShutter();
    }

    @Test
    public void testOnShutterCallback_SecondCall_UpdatesParametersFromCamera() throws Exception {
        // Prepare flags: capturing = false, duringExposure = true
        setBool(fragment, "mIsCapturing", false);
        setBool(fragment, "mIsDuringExposure", true);

        Camera mockCamera = mock(Camera.class);
        Camera.Parameters mockParams = mock(Camera.Parameters.class);
        when(mockCamera.getParameters()).thenReturn(mockParams);
        setObj(fragment, "mCamera", mockCamera);

        Camera.ShutterCallback shutter = (Camera.ShutterCallback) getObj(fragment, "onShutterCallback");
        assertNotNull(shutter);
        shutter.onShutter();

        // Verify transition and parameter capture
        assertFalse(getBool(fragment, "mIsDuringExposure"));
        // mParameters should now be set to mockParams
        assertSame(mockParams, getObj(fragment, "mParameters"));
        verify(mockCamera, times(1)).getParameters();
    }

    @Test
    public void testCreateDir_CreatesBurstDirectoryUnderExternalDcim() throws Exception {
        // Attach with a valid context so getContext() is non-null
        android.content.Context ctx = InstrumentationRegistry.getTargetContext();
        Method onAttach = CameraFragment.class.getDeclaredMethod("onAttach", android.content.Context.class);
        onAttach.setAccessible(true);
        onAttach.invoke(fragment, ctx);

        // Invoke createDir()
        Method createDir = CameraFragment.class.getDeclaredMethod("createDir");
        createDir.setAccessible(true);
        createDir.invoke(fragment);

        // Assert SAVEDIR is set and directory exists
        String saveDir = (String) getObj(fragment, "SAVEDIR");
        assertNotNull(saveDir);
        assertTrue("SAVEDIR should include Burst_IBL_Shooter", saveDir.contains("/Burst_IBL_Shooter/"));
        File dir = new File(saveDir);
        assertTrue("Directory should be created", dir.exists() && dir.isDirectory());

        // Optional: Ensure directory resides under external DCIM app-specific path
        File dcim = ctx.getExternalFilesDir(Environment.DIRECTORY_DCIM);
        if (dcim != null) {
            assertTrue("SAVEDIR should start with app external DCIM path",
                    saveDir.startsWith(dcim.getPath()));
        }
    }
}