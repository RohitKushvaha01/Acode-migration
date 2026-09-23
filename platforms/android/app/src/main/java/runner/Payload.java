package runner;

import android.util.Base64;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Payload {

  public static final int MESSAGE_TYPE_STRING = 1;
  public static final int MESSAGE_TYPE_ARRAYBUFFER = 6;
  public static final int MESSAGE_TYPE_BINARYSTRING = 7;

  public enum Status {
    NO_RESULT,
    OK,
    CLASS_NOT_FOUND_EXCEPTION,
    ILLEGAL_ACCESS_EXCEPTION,
    INSTANTIATION_EXCEPTION,
    MALFORMED_URL_EXCEPTION,
    IO_EXCEPTION,
    INVALID_ACTION,
    JSON_EXCEPTION,
    ERROR,
  }

  private static final String[] STATUS_MESSAGES = {
    "No result",
    "OK",
    "Class not found",
    "Illegal access",
    "Instantiation error",
    "Malformed url",
    "IO error",
    "Invalid action",
    "JSON error",
    "Error",
  };

  private final Status status;
  private final Object data;
  private boolean keepAlive;

  public Payload(Status status) {
    this(status, STATUS_MESSAGES[status.ordinal()]);
  }

  public Payload(Status status, Object data) {
    this.status = status;
    this.data = data;
  }

  public Payload(Status status, byte[] data, boolean binaryString) {
    this(status, encodeBinary(data, binaryString));
  }

  public Payload(Object data) {
    this(Status.OK, data);
  }

  public int getStatus() {
    return status.ordinal();
  }

  public boolean getKeepCallback() {
    return keepAlive;
  }

  public void setKeepCallback(boolean keepAlive) {
    this.keepAlive = keepAlive;
  }

  public JSONObject toJSON(long id, long generation) throws JSONException {
    JSONObject result = new JSONObject();
    result.put("id", id);
    result.put("generation", generation);
    result.put("type", status == Status.OK ? 0 : 1);
    result.put("status", status.ordinal());
    result.put("keep", keepAlive);
    result.put("data", encode(data));
    return result;
  }

  private static Object encode(Object value) throws JSONException {
    if (value == null) return JSONObject.NULL;
    if (value instanceof byte[]) return encodeBinary((byte[]) value, false);
    if (value instanceof List<?>) {
      JSONArray parts = new JSONArray();
      for (Object part : (List<?>) value)
        parts.put(encode(((Payload) part).data));
      return new JSONObject().put("kind", "multipart").put("data", parts);
    }
    return value;
  }

  private static JSONObject encodeBinary(byte[] bytes, boolean binaryString) {
    try {
      return new JSONObject()
        .put("kind", binaryString ? "binaryString" : "arrayBuffer")
        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
    } catch (JSONException exception) {
      throw new IllegalArgumentException(exception);
    }
  }
}
