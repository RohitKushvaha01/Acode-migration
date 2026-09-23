package runner;

import android.util.Log;
import org.json.JSONException;
import runner.webView.AppView;

public class Callback {

  private final long id;
  private final AppView webView;
  private final long generation;
  private boolean finished;

  public Callback(long id, AppView webView) {
    this.id = id;
    this.webView = webView;
    this.generation = webView.getBridge().getGeneration();
  }

  public synchronized boolean isFinished() {
    return finished;
  }

  public String getCallbackId() {
    return Long.toString(id);
  }

  public void success() {
    sendPayload(new Payload(Payload.Status.OK));
  }

  public void success(Object data) {
    sendPayload(new Payload(Payload.Status.OK, data));
  }

  public void error(Object data) {
    sendPayload(new Payload(Payload.Status.ERROR, data));
  }

  public synchronized void sendPayload(Payload payload) {
    if (finished) return;
    finished = !payload.getKeepCallback();
    if (
      payload.getStatus() == Payload.Status.NO_RESULT.ordinal() &&
      payload.getKeepCallback()
    ) return;
    try {
      String json = payload.toJSON(id, generation).toString();
      webView.post(() -> {
        if (generation == webView.getBridge().getGeneration()) {
          webView.evaluateJavascript(
            "window.Android.callback(" + json + ");",
            null
          );
        }
      });
    } catch (JSONException exception) {
      Log.e("Acode", "Unable to serialize callback", exception);
    }
  }
}
