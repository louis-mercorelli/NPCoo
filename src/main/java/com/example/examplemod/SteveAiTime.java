package com.example.examplemod;

final class SteveAiTime {

    private SteveAiTime() {}

    static String scanTs() {
        return java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}
