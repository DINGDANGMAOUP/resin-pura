package com.caucho;

public final class Version {
    public static String VERSION;

    static {
        VERSION = "3.1.13";
        System.setProperty("resin.pura.test.version-class-initialized", "executed");
    }

    private Version() {
    }
}
