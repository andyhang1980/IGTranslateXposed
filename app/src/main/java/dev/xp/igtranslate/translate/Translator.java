package dev.xp.igtranslate.translate;

import android.content.Context;
import android.util.Log;

import androidx.collection.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Translation backend:
 * 1) OpenAI-compatible API (DeepSeek / SiliconFlow / custom) when enabled
 * 2) Google free translate API as fallback
 * Results are cached in memory to avoid repeated network calls.
 */
public class Translator {

    private static final String TAG = "IGTranslate";
    private static final LruCache<String, String> CACHE = new LruCache<>(3000);
    private static final ExecutorService EXEC = Executors.newCachedThreadPool();

    public interface Callback {
        void onResult(String translated);
    }

    private static String key(String lang, String text) {
        return lang + ":" + text;
    }

    public static boolean isCached(String lang, String text) {
        return CACHE.get(key(lang, text)) != null;
    }

    public static void translate(String text, String lang, Context ctx, Callback cb) {
        String k = key(lang, text);
        String cached = CACHE.get(k);
        if (cached != null) {
            cb.onResult(cached);
            return;
        }
        EXEC.submit(() -> {
            String r = translateSync(text, lang, ctx);
            if (r != null) CACHE.put(k, r);
            cb.onResult(r != null ? r : text);
        });
    }

    public static String translate(String text, String lang, Context ctx) {
        String k = key(lang, text);
        String cached = CACHE.get(k);
        if (cached != null) return cached;
        String r = translateSync(text, lang, ctx);
        if (r != null) CACHE.put(k, r);
        return r != null ? r : text;
    }

    private static String translateSync(String text, String lang, Context ctx) {
        if (Prefs.openAiEnabled(ctx)) {
            String r = callOpenAi(text, lang, ctx);
            if (r != null && !r.isEmpty()) return r;
        }
        return callGoogle(text, lang);
    }

    private static String callOpenAi(String text, String lang, Context ctx) {
        String base = Prefs.baseUrl(ctx) == null ? "" : Prefs.baseUrl(ctx).trim();
        String apiKey = Prefs.apiKey(ctx) == null ? "" : Prefs.apiKey(ctx).trim();
        String model = Prefs.model(ctx) == null ? "" : Prefs.model(ctx).trim();
        if (base.isEmpty() || model.isEmpty()) return null;
        try {
            String endpoint = base.replaceAll("/+$", "") + "/chat/completions";
            String sys = "You are a professional translator. Translate the following text into "
                    + lang + " only. Output ONLY the translated text, with no explanations, no notes, and no markdown formatting.";
            String body = "{\"model\":" + json(model)
                    + ",\"messages\":[{\"role\":\"system\",\"content\":" + json(sys)
                    + "},{\"role\":\"user\",\"content\":" + json(text) + "}]"
                    + ",\"temperature\":0.3,\"stream\":false}";

            HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            if (!apiKey.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            c.setConnectTimeout(5000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);

            try (OutputStream os = c.getOutputStream()) {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                os.write(b);
                os.flush();
            }

            int st = c.getResponseCode();
            if (st != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            return parseOpenAi(sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "OpenAi failed: " + t.getMessage());
            return null;
        }
    }

    private static String callGoogle(String text, String lang) {
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                    + URLEncoder.encode(lang, "UTF-8") + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }

            JSONArray arr = new JSONArray(sb.toString());
            JSONArray tr = arr.getJSONArray(0);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < tr.length(); i++) {
                out.append(tr.getJSONArray(i).getString(0));
            }
            return out.toString().trim();
        } catch (Throwable t) {
            Log.w(TAG, "Google failed: " + t.getMessage());
            return null;
        }
    }

    private static String parseOpenAi(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
            if (msg == null) return null;
            String content = msg.optString("content", null);
            return content == null ? null : content.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String json(String s) {
        if (s == null) return "null";
        String e = s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
        return "\"" + e + "\"";
    }
}
