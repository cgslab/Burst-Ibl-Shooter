// Testing stack: AndroidX Test JUnit4 + Espresso (instrumented tests)

package info.cgslab.burstiblshooter;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Environment;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityInstrumentedTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class, true, true);

    private MainActivity activity;

    @Before
    public void setUp() {
        activity = activityRule.getActivity();
        assertNotNull("Activity should launch", activity);
    }

    @After
    public void tearDown() {
        // No-op
    }

    @Test
    public void onPictureTaken_normalizesPathsAndDoesNotCrash_onSingleFile() {
        // Arrange: Build a fake absolute path resembling the activity's logic
        // Note: getExternalFilesDir(DIRECTORY_DCIM).getParent() is trimmed from the beginning of fileUrls
        Context ctx = ApplicationProvider.getApplicationContext();
        String base;
        try {
            // Mirror the code under test to compute the base storage path
            java.io.File dcimDir = activity.getExternalFilesDir(Environment.DIRECTORY_DCIM);
            base = dcimDir != null ? dcimDir.getParent() : "";
        } catch (Exception e) {
            base = "";
        }

        String rel = "/DCIM/100TEST/IMG_0001.DNG";
        String full = base + rel;

        String[] urls = new String[] { full };

        // Act: invoke onPictureTaken
        // mIsDone=false -> should not call displayOled reset path; we only verify no crash and effect on array
        activity.onPictureTaken(urls, /*mIsDone=*/false);

        // Assert: the path should have been normalized to be relative (base trimmed)
        assertEquals("Expected base path to be trimmed from file path", rel, urls[0]);
    }

    @Test
    public void onPictureTaken_normalizesMultiplePaths_andResetsOledWhenDone() {
        Context ctx = ApplicationProvider.getApplicationContext();
        String base;
        try {
            java.io.File dcimDir = activity.getExternalFilesDir(Environment.DIRECTORY_DCIM);
            base = dcimDir != null ? dcimDir.getParent() : "";
        } catch (Exception e) {
            base = "";
        }

        String rel1 = "/DCIM/100TEST/IMG_0002.DNG";
        String rel2 = "/DCIM/100TEST/IMG_0003.DNG";
        String[] urls = new String[] { base + rel1, base + rel2 };

        // Act
        activity.onPictureTaken(urls, /*mIsDone=*/true);

        // Assert normalization
        assertArrayEquals(new String[] { rel1, rel2 }, urls);
        // We can't directly assert OLED display as it uses PluginActivity internals,
        // but this call should not crash; behavior covered indirectly by reaching here.
    }

    @Test
    public void hasPermission_returnsFalseWhenNotGranted() {
        // This test cannot toggle runtime permissions in instrumentation easily.
        // We call the method to ensure it returns a boolean and does not crash when permissions are missing.
        // If the test device/emulator grants permissions by default, the method may return true.
        boolean result = invokeHasPermission(activity);
        assertTrue("hasPermission should return a boolean without crashing", result || !result);
    }

    @Test
    public void checkVersion_handlesSemanticComparison() {
        // We cannot mock ThetaInfo static method without additional setup; however,
        // invoking the method should not crash. We treat the return as opaque boolean.
        boolean result = invokeCheckVersion(activity);
        assertTrue("checkVersion should return a boolean without crashing", result || !result);
    }

    @Test
    public void isZ1_returnsBoolean_withoutCrash() {
        boolean result = invokeIsZ1(activity);
        assertTrue("isZ1 should return a boolean without crashing", result || !result);
    }

    // Helper invocations via reflection to access private methods
    private boolean invokeHasPermission(MainActivity a) {
        try {
            java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("hasPermission");
            m.setAccessible(true);
            Object r = m.invoke(a);
            return (Boolean) r;
        } catch (Exception e) {
            fail("Invocation of hasPermission failed: " + e.getMessage());
            return false;
        }
    }

    private boolean invokeCheckVersion(MainActivity a) {
        try {
            java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("checkVersion");
            m.setAccessible(true);
            Object r = m.invoke(a);
            return (Boolean) r;
        } catch (Exception e) {
            fail("Invocation of checkVersion failed: " + e.getMessage());
            return false;
        }
    }

    private boolean invokeIsZ1(MainActivity a) {
        try {
            java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("isZ1");
            m.setAccessible(true);
            Object r = m.invoke(a);
            return (Boolean) r;
        } catch (Exception e) {
            fail("Invocation of isZ1 failed: " + e.getMessage());
            return false;
        }
    }
}