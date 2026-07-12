package dev.xp.igtranslate.translate;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Reads translation settings written by SettingsActivity.
 * Settings are stored world-readable so the hooked Instagram process can read them.
 */
public class Prefs {

    public static final String NAME = "igtrans_settings";
    public static final String PKG = "dev.xp.igtranslate";

    private static SharedPreferences sp(Context ctx) {
        try {
            Context c = ctx.createPackageContext(PKG, Context.CONTEXT_IGNORE_SECURITY);
            return c.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean enabled(Context ctx) {
        SharedPreferences s = sp(ctx);
        return s != null && s.getBoolean("enabled", false);
    }

    public static boolean openAiEnabled(Context ctx) {
        return enabled(ctx);
    }

    public static String baseUrl(Context ctx) {
        SharedPreferences s = sp(ctx);
        return s == null ? "" : s.getString("base_url", "");
    }

    public static String apiKey(Context ctx) {
        SharedPreferences s = sp(ctx);
        return s == null ? "" : s.getString("api_key", "");
    }

    public static String model(Context ctx) {
        SharedPreferences s = sp(ctx);
        return s == null ? "" : s.getString("model", "");
    }

    public static String targetLang(Context ctx) {
        SharedPreferences s = sp(ctx);
        return s == null ? "zh-CN" : s.getString("target_lang", "zh-CN");
    }
}
