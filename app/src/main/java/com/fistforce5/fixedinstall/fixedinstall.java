package com.fistforce5.fixedinstall;

import android.os.Build;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class fixedinstall implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    public static final String PACKAGE_NAME = fixedinstall.class.getPackage().getName();
//    public static String MODULE_PATH = null;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {

//        MODULE_PATH = startupParam.modulePath;

        XposedBridge.log("FF5:Hardware: " + Build.HARDWARE);
        XposedBridge.log("FF5:Product: " + Build.PRODUCT);
        XposedBridge.log("FF5:Device manufacturer: " + Build.MANUFACTURER);
        XposedBridge.log("FF5:Device brand: " + Build.BRAND);
        XposedBridge.log("FF5:Device model: " + Build.MODEL);
        XposedBridge.log("FF5:Android SDK: " + Build.VERSION.SDK_INT);
        XposedBridge.log("FF5:Android Release: " + Build.VERSION.RELEASE);
        XposedBridge.log("FF5:ROM: " + Build.DISPLAY);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;
        ModNavigationBar.init(lpparam.classLoader);
    }
}
