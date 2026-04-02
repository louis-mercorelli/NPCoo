package com.sai;

public final class NpcooLog {
    private static final String PREFIX = "[NPCoo] ";

    private NpcooLog() {
    }

    public static String tag(String message) {
        return PREFIX + message;
    }
}
