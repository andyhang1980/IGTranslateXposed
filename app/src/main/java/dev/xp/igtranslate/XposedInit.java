package dev.xp.igtranslate;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import de.robv.android.xposed.XposedBridge;

/**
 * Xposed entry point. Hooks only Instagram (com.instagram.android).
 */
public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName == null) return;
        if ("com.instagram.android".equals(lpparam.packageName)) {
            XposedBridge.log("[IGTranslate] hooking Instagram");
            new IgHook().hook(lpparam);
        }
    }
}
