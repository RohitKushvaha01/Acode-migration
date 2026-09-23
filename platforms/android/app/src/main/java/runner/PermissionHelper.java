package runner;

public final class PermissionHelper {
    private PermissionHelper() {}
    public static boolean hasPermission(Service service, String permission) {
        return service.host.hasPermission(permission);
    }
    public static void requestPermission(Service service, int code, String permission) {
        service.host.requestPermission(service, code, permission);
    }
    public static void requestPermissions(Service service, int code, String[] permissions) {
        service.host.requestPermissions(service, code, permissions);
    }
}
