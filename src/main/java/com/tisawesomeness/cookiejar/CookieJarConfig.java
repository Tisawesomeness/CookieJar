package com.tisawesomeness.cookiejar;

import eu.midnightdust.lib.config.MidnightConfig;

public class CookieJarConfig extends MidnightConfig {
    @Entry
    public static IgnoreCondition ignoreCookieStores = IgnoreCondition.NEVER;
    @Entry
    public static boolean ignoreCookieRequests = false;
    @Entry
    public static boolean ignoreTransfers = false;
    @Entry
    public static boolean enableSingleplayerFix = true;

    public enum IgnoreCondition {
        NEVER, WHILE_SCREEN_OPEN, ALWAYS;
    }
}
