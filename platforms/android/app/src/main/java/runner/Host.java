package runner;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.json.JSONException;

public final class Host {

  private final MainActivity activity;
  private final Preferences preferences = new Preferences();
  private final Map<Integer, Request> permissionRequests = new HashMap<>();
  private final Map<Integer, Request> activityRequests = new HashMap<>();
  private int requestId = 40000;

  public Host(MainActivity activity) {
    this.activity = activity;
    preferences.set("AndroidPersistentFileLocation", "Compatibility");
    preferences.set("AndroidBlacklistSecureSocketProtocols", "SSLv3,TLSv1");
    preferences.set("scheme", "https");
    preferences.set("hostname", "localhost");
    preferences.set("BackgroundColor", 0xff313131);
  }

  public MainActivity getActivity() {
    return activity;
  }

  public Context getContext() {
    return activity;
  }

  public Preferences getPreferences() {
    return preferences;
  }

  public ExecutorService getThreadPool() {
    return AcodeApplication.getInstance().getThreadPool();
  }

  public boolean hasPermission(String permission) {
    return (
      ActivityCompat.checkSelfPermission(activity, permission) ==
      PackageManager.PERMISSION_GRANTED
    );
  }

  public void requestPermission(Service service, int code, String permission) {
    requestPermissions(service, code, new String[] {permission});
  }

  public void requestPermissions(
    Service service,
    int code,
    String[] permissions
  ) {
    activity.runOnUiThread(() -> {
      int id = requestId++;
      permissionRequests.put(id, new Request(service, code));
      ActivityCompat.requestPermissions(activity, permissions, id);
    });
  }

  public void startActivityForResult(Service service, Intent intent, int code) {
    activity.runOnUiThread(() -> {
      int id = requestId++;
      activityRequests.put(id, new Request(service, code));
      activity.startActivityForResult(intent, id);
    });
  }

  public boolean onPermissionsResult(int id, String[] permissions, int[] grants)
    throws JSONException {
    Request request = permissionRequests.remove(id);
    if (request == null) return false;
    request.service.onRequestPermissionResult(
      request.code,
      permissions,
      grants
    );
    return true;
  }

  public boolean onActivityResult(int id, int result, Intent data) {
    Request request = activityRequests.remove(id);
    if (request == null) return false;
    request.service.onActivityResult(request.code, result, data);
    return true;
  }

  private static final class Request {

    final Service service;
    final int code;

    Request(Service service, int code) {
      this.service = service;
      this.code = code;
    }
  }
}
