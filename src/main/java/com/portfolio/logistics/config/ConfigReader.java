package com.portfolio.logistics.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loads local config.properties when present and exposes typed getters.
 * System properties and LOGISTICS_* environment variables can override local
 * values, so credentials and environment details stay outside source control.
 */
public final class ConfigReader {

    private static final Properties PROPS = new Properties();
    private static final String ENV_PREFIX = "LOGISTICS_";

    static {
        try (InputStream in = ConfigReader.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in != null) PROPS.load(in);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ConfigReader() { }

    public static String get(String key) {
        String v = System.getProperty("logistics." + key);
        if (v == null) v = System.getenv(envName(key));
        if (v == null) v = PROPS.getProperty(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return v.trim();
    }

    public static String get(String key, String fallback) {
        try { return get(key); } catch (IllegalArgumentException missing) { return fallback; }
    }

    public static boolean getBool(String key, boolean fallback) {
        String v = get(key, null);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    public static int getInt(String key, int fallback) {
        String v = get(key, null);
        return v == null ? fallback : Integer.parseInt(v.trim());
    }

    private static String envName(String key) {
        return ENV_PREFIX + key.replace('.', '_').replace('-', '_').toUpperCase();
    }

    // --- Convenience accessors used across the suite ---
    public static String baseUrl()        { return get("base.url"); }
    public static String corporateId()    { return get("superadmin.corporateId"); }
    public static String superAdminUsername(){ return get("superadmin.username"); }
    public static String superAdminPass() { return get("superadmin.password"); }
    // Privileged (full-access) login for the SuperAdmin-style tests. Defaults to the superadmin
    // account, but each field can be overridden per environment — e.g. on LIVE the SuperAdmin login
    // is disabled, so admin.* points at a full-access ADMIN account instead. This keeps the tests
    // env-portable without hardcoding which account has full access in a given tenant.
    public static String adminCorporateId(){ return get("admin.corporateId", corporateId()); }
    public static String adminUsername()   { return get("admin.username", superAdminUsername()); }
    public static String adminPass()       { return get("admin.password", superAdminPass()); }

    public static boolean headless()      { return getBool("browser.headless", true); }
    public static int slowMo()            { return getInt("browser.slowMo", 0); }
    public static int defaultTimeout()    { return getInt("timeout.default.ms", 25_000); }


}
