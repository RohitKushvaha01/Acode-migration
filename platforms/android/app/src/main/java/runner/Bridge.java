package runner;

import android.net.Uri;
import android.webkit.JavascriptInterface;
import com.foxdebug.acode.BuildConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import runner.webView.AppView;

public final class Bridge {

  private final Host host;
  private final AppView webView;
  private final Map<String, JSONObject> registry = new LinkedHashMap<>();
  private final Map<String, Service> services = new LinkedHashMap<>();
  private volatile long generation;

  public Bridge(Host host, AppView webView) {
    this.host = host;
    this.webView = webView;
    try (
      java.io.InputStream input = host
        .getContext()
        .getAssets()
        .open("services.json")
    ) {
      java.io.ByteArrayOutputStream output =
        new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
      JSONArray definitions = new JSONArray(
        output.toString(StandardCharsets.UTF_8.name())
      );
      for (int index = 0; index < definitions.length(); index++) {
        JSONObject definition = definitions.getJSONObject(index);
        String variant = definition.getString("variant");
        if (
          variant.equals("free") && !BuildConfig.FLAVOR.equals("free")
        ) continue;
        if (variant.equals("store") && BuildConfig.FDROID) continue;
        registry.put(definition.getString("name"), definition);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(
        "Unable to load native services",
        exception
      );
    }
  }

  public void initialize() {
    for (JSONObject definition : registry.values()) {
      if (definition.optBoolean("onload")) getService(
        definition.optString("name")
      );
    }
  }

  public synchronized Service getService(String name) {
    if (services.containsKey(name)) return services.get(name);
    JSONObject definition = registry.get(name);
    if (definition == null) return null;
    try {
      Service service = (Service) Class.forName(definition.getString("class"))
        .getDeclaredConstructor()
        .newInstance();
      service.initialize(host, webView);
      service.serviceInitialize();
      services.put(name, service);
      return service;
    } catch (Exception exception) {
      throw new IllegalStateException(
        "Unable to initialize " + name,
        exception
      );
    }
  }

  public synchronized ArrayList<Service> getServices() {
    return new ArrayList<>(services.values());
  }

  public long getGeneration() {
    return generation;
  }

  @JavascriptInterface
  public boolean exec(String name, String action, String args, long id) {
    Callback callback = new Callback(id, webView);
    long requestGeneration = generation;
    webView.post(() -> {
      if (generation != requestGeneration) return;
      try {
        Service service = getService(name);
        if (service == null) {
          callback.error("Unknown native service: " + name);
          return;
        }
        AcodeApplication.getInstance()
          .getThreadPoolSingle()
          .execute(() -> {
            if (generation != requestGeneration) return;
            try {
              if (!service.execute(action, args, callback)) {
                callback.sendPayload(
                  new Payload(Payload.Status.INVALID_ACTION, action)
                );
              }
            } catch (Exception exception) {
              callback.error(exception.toString());
            }
          });
      } catch (Exception exception) {
        callback.error(exception.toString());
      }
    });
    return true;
  }

  public Uri remapUri(Uri uri) {
    for (Service service : getServices()) {
      Uri result = service.remapUri(uri);
      if (result != null) return result;
    }
    return null;
  }

  public void postMessage(String name, Object value) {
    for (Service service : getServices()) service.onMessage(name, value);
  }

  public void reset() {
    generation++;
    for (Service service : getServices()) service.onReset();
  }

  public void destroy() {
    generation++;
    for (Service service : getServices()) service.onDestroy();
    services.clear();
  }
}
