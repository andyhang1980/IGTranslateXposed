package dev.xp.igtranslate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dalvik.system.DexFile;

import dev.xp.igtranslate.translate.Prefs;
import dev.xp.igtranslate.translate.Translator;

/**
 * Hooks Instagram text rendering:
 * - android.widget.TextView.setText (covers AppCompatTextView etc.)
 * - any custom setText(CharSequence/String) method found in the app's dex
 * so that posts / comments / bios get translated.
 */
public class IgHook {

    private static final String PENDING_KEY = "igtranslate_pending";
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private static volatile Context appContext;

    public void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        hookTextView(lpparam);
        hookCustomSetText(lpparam);
    }

    private void hookTextView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText",
                    CharSequence.class, TextView.BufferType.class, boolean.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handle(param, param.thisObject, (CharSequence) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[IGTranslate] hook TextView failed: " + t);
        }
    }

    private void hookCustomSetText(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Field pathListField = XposedHelpers.findField(lpparam.classLoader.getClass(), "pathList");
            Object pathList = pathListField.get(lpparam.classLoader);
            Field dexElementsField = XposedHelpers.findField(pathList.getClass(), "dexElements");
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);

            Field dexFileField = null;
            for (Object element : dexElements) {
                if (element == null) continue;
                if (dexFileField == null) {
                    dexFileField = XposedHelpers.findFieldIfExists(element.getClass(), "dexFile");
                }
                if (dexFileField == null) continue;
                DexFile dexFile = (DexFile) dexFileField.get(element);
                if (dexFile == null) continue;

                Enumeration<String> classNames = dexFile.entries();
                while (classNames.hasMoreElements()) {
                    String className = classNames.nextElement();
                    try {
                        Class<?> clazz = lpparam.classLoader.loadClass(className);
                        if (TextView.class.isAssignableFrom(clazz)) continue;

                        for (final Method method : clazz.getDeclaredMethods()) {
                            if (!method.getName().equals("setText")) continue;
                            Class<?>[] p = method.getParameterTypes();
                            if (p.length == 1 && (p[0] == CharSequence.class || p[0] == String.class)) {
                                XposedBridge.hookMethod(method, new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        handle(param, param.thisObject, (CharSequence) param.args[0]);
                                    }
                                });
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[IGTranslate] enumerate dex failed: " + t);
        }
    }

    private void handle(XC_MethodHook.MethodHookParam param, Object target, CharSequence original) {
        if (original == null || original.length() == 0) return;
        if (original instanceof Editable) return;
        try {
            if (target instanceof EditText) return;
        } catch (Throwable ignored) {
        }

        String text = original.toString();
        if (text.trim().isEmpty()) return;

        // dedupe: skip if the same text is already being translated for this target
        try {
            String pending = (String) XposedHelpers.getAdditionalInstanceField(target, PENDING_KEY);
            if (text.equals(pending)) return;
            XposedHelpers.setAdditionalInstanceField(target, PENDING_KEY, text);
        } catch (Throwable ignored) {
        }

        Context ctx = findContext(target);
        if (ctx == null) {
            clearPending(target);
            return;
        }
        if (!Prefs.enabled(ctx)) {
            clearPending(target);
            return;
        }

        String targetLang = Prefs.targetLang(ctx);
        exec.submit(() -> {
            String translated = Translator.translate(text, targetLang, ctx);
            try {
                String cur = (String) XposedHelpers.getAdditionalInstanceField(target, PENDING_KEY);
                if (!text.equals(cur)) return; // superseded by newer text; skip stale apply
                if (translated != null && !translated.equals(text)) {
                    CharSequence newText = (original instanceof Spanned)
                            ? new SpannableString(translated) : translated;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            param.args[0] = newText;
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        } catch (Throwable ignored) {
                        }
                    });
                }
            } finally {
                clearPending(target);
            }
        });
    }

    private void clearPending(Object target) {
        try {
            XposedHelpers.setAdditionalInstanceField(target, PENDING_KEY, null);
        } catch (Throwable ignored) {
        }
    }

    private Context findContext(Object target) {
        Context ctx = null;
        try {
            if (target instanceof Context) {
                ctx = (Context) target;
            } else if (target instanceof View) {
                ctx = ((View) target).getContext();
            } else {
                Method g = XposedHelpers.findMethodExactIfExists(target.getClass(), "getContext");
                if (g != null) {
                    ctx = (Context) XposedHelpers.callMethod(target, "getContext");
                }
            }
        } catch (Throwable ignored) {
        }
        if (ctx != null) appContext = ctx;
        return ctx != null ? ctx : appContext;
    }
}
