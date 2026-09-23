package runner;

import static org.junit.Assert.*;

import android.os.Looper;
import com.foxdebug.acode.BuildConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class StartupTest {

  @Test
  public void startsAndResumesWithoutReloadingTheEditor() throws Exception {
    Thread.UncaughtExceptionHandler previous =
      Thread.getDefaultUncaughtExceptionHandler();
    try (
      org.robolectric.android.controller.ActivityController<
        MainActivity
      > controller = Robolectric.buildActivity(activityClass())
    ) {
      MainActivity activity = controller.create().start().resume().get();
      runner.webView.AppView view = (runner.webView.AppView) activity
        .getContentView()
        .getChildAt(0);
      assertEquals("https://localhost/index.html", view.getUrl());
      android.content.pm.ActivityInfo info = activity
        .getPackageManager()
        .getActivityInfo(activity.getComponentName(), 0);
      assertEquals(BuildConfig.APPLICATION_ID + ".MainActivity", info.name);
      assertNotNull(view.getBridge().getService("File"));
      assertNotNull(view.getBridge().getService("Authenticator"));
      assertNotNull(view.getBridge().getService("System"));
      long generation = view.getBridge().getGeneration();
      controller.pause().resume();
      assertEquals(generation, view.getBridge().getGeneration());
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  @Test
  public void routesOverlappingActivityRequestsToTheirOwners()
    throws Exception {
    Thread.UncaughtExceptionHandler previous =
      Thread.getDefaultUncaughtExceptionHandler();
    try (
      org.robolectric.android.controller.ActivityController<
        MainActivity
      > controller = Robolectric.buildActivity(activityClass()).setup()
    ) {
      MainActivity activity = controller.get();
      int[] received = new int[2];
      Service first = new Service() {
        @Override
        public void onActivityResult(
          int code,
          int result,
          android.content.Intent data
        ) {
          received[0] = code + result;
        }
      };
      Service second = new Service() {
        @Override
        public void onActivityResult(
          int code,
          int result,
          android.content.Intent data
        ) {
          received[1] = code + result;
        }
      };
      org.robolectric.shadows.ShadowActivity shadow = Shadows.shadowOf(
        activity
      );
      activity
        .getHost()
        .startActivityForResult(
          first,
          new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT),
          7
        );
      int firstId = shadow.getNextStartedActivityForResult().requestCode;
      activity
        .getHost()
        .startActivityForResult(
          second,
          new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT),
          7
        );
      int secondId = shadow.getNextStartedActivityForResult().requestCode;
      assertNotEquals(firstId, secondId);
      activity.getHost().onActivityResult(secondId, 20, null);
      activity.getHost().onActivityResult(firstId, 10, null);
      assertArrayEquals(new int[] {17, 27}, received);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  @Test
  public void discardsCallbacksFromPreviousPageGenerations() throws Exception {
    Thread.UncaughtExceptionHandler previous =
      Thread.getDefaultUncaughtExceptionHandler();
    try (
      org.robolectric.android.controller.ActivityController<
        MainActivity
      > controller = Robolectric.buildActivity(activityClass()).setup()
    ) {
      runner.webView.AppView view = (runner.webView.AppView) controller
        .get()
        .getContentView()
        .getChildAt(0);
      Callback old = new Callback(42, view);
      view.getBridge().reset();
      String before = Shadows.shadowOf(view).getLastEvaluatedJavascript();
      old.success("stale");
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(before, Shadows.shadowOf(view).getLastEvaluatedJavascript());
      new Callback(42, view).success("current");
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertTrue(
        Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("current")
      );
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  @Test
  public void hardwareBackReachesFullscreenOwnerBeforeWebView()
    throws Exception {
    Thread.UncaughtExceptionHandler previous =
      Thread.getDefaultUncaughtExceptionHandler();
    try (
      org.robolectric.android.controller.ActivityController<
        MainActivity
      > controller = Robolectric.buildActivity(activityClass()).setup()
    ) {
      MainActivity activity = controller.get();
      runner.webView.AppView view = (runner.webView.AppView) activity
        .getContentView()
        .getChildAt(0);
      runner.webView.ChromeClient chrome =
        (runner.webView.ChromeClient) view.getWebChromeClient();
      int[] hidden = {0};
      chrome.onShowCustomView(
        new android.view.View(activity),
        () -> hidden[0]++
      );
      chrome.setBackHandler(true);
      assertTrue(
        activity.dispatchKeyEvent(
          new android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_BACK
          )
        )
      );
      assertTrue(
        activity.dispatchKeyEvent(
          new android.view.KeyEvent(
            android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_BACK
          )
        )
      );
      assertTrue(chrome.isFullscreen());
      assertEquals(0, hidden[0]);
      assertTrue(
        Shadows.shadowOf(view)
          .getLastEvaluatedJavascript()
          .contains("fullscreenbackbutton")
      );
      chrome.setBackHandler(false);
      activity.dispatchKeyEvent(
        new android.view.KeyEvent(
          android.view.KeyEvent.ACTION_UP,
          android.view.KeyEvent.KEYCODE_BACK
        )
      );
      assertFalse(chrome.isFullscreen());
      assertEquals(1, hidden[0]);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  @SuppressWarnings("unchecked")
  private static Class<MainActivity> activityClass()
    throws ClassNotFoundException {
    return (Class<MainActivity>) Class.forName(
      BuildConfig.APPLICATION_ID + ".MainActivity"
    );
  }
}
