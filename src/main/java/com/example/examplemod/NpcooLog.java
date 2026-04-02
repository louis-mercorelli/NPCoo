package com.example.examplemod;

public final class NpcooLog {
    private static final String PREFIX = "[NPCoo] ";

    private NpcooLog() {
    }

    public static String tag(String message) {
        return PREFIX + message;
    }
}
