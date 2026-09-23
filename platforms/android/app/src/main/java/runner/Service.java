package runner;

import android.content.Intent;
import android.net.Uri;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import runner.webView.AppView;

public abstract class Service {

  public Host host;
  public AppView webView;
  protected Preferences preferences;

  public void initialize(Host host, AppView webView) {
    this.host = host;
    this.webView = webView;
    this.preferences = host.getPreferences();
  }

  protected void serviceInitialize() {}

  public boolean execute(String action, String args, Callback callback)
    throws JSONException {
    return execute(action, new JSONArray(args), callback);
  }

  public boolean execute(String action, JSONArray args, Callback callback)
    throws JSONException {
    return false;
  }

  public void onRequestPermissionResult(
    int requestCode,
    String[] permissions,
    int[] grants
  ) throws JSONException {}

  public void onActivityResult(int requestCode, int resultCode, Intent data) {}

  public Object onMessage(String id, Object data) {
    return null;
  }

  public void onNewIntent(Intent intent) {}

  public void onPause(boolean multitasking) {}

  public void onResume(boolean multitasking) {}

  public void onReset() {}

  public void onConfigurationChanged(
    android.content.res.Configuration configuration
  ) {}

  public void onDestroy() {}

  public Uri remapUri(Uri uri) {
    return null;
  }

  public ServicePathHandler getPathHandler() {
    return null;
  }

  public ResourceApi.OpenForReadResult openForRead(Uri uri) throws IOException {
    throw new java.io.FileNotFoundException(uri.toString());
  }
}
