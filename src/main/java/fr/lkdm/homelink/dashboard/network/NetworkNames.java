package fr.lkdm.homelink.dashboard.network;

/** Shared input rules; the server always validates again before mutation. */
public final class NetworkNames {
    private NetworkNames() { }
    public static boolean isValid(String name) {
        return name != null && !name.isBlank() && name.length() <= 128
                && name.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == 0xA7);
    }
}
