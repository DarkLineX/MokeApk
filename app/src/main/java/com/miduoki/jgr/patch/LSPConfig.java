package com.miduoki.jgr.patch;

public class LSPConfig {

    public static final LSPConfig instance;

    public int API_CODE;
    public int VERSION_CODE;
    public String VERSION_NAME;
    public int CORE_VERSION_CODE;
    public String CORE_VERSION_NAME;

    private LSPConfig() {
    }

    static {
        instance = new LSPConfig();
        instance.API_CODE = 93;
        instance.VERSION_CODE = 330;
        instance.VERSION_NAME = "0.3.1";
        instance.CORE_VERSION_CODE = 6600;
        instance.CORE_VERSION_NAME = "1.8.3";
    }
}
