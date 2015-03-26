/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fistforce5.fixedinstall;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageView.ScaleType;

import com.fistforce5.fixedinstall.R;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModNavigationBar {
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String TAG = "FF5:ModNavigationBar";
    private static final boolean DEBUG = true;

    private static final String CLASS_NAVBAR_VIEW = "com.android.systemui.statusbar.phone.NavigationBarView";
    private static final String CLASS_KEY_BUTTON_VIEW = "com.android.systemui.statusbar.policy.KeyButtonView";
    private static final String CLASS_NAVBAR_TRANSITIONS = 
            "com.android.systemui.statusbar.phone.NavigationBarTransitions";
    private static final String CLASS_PHONE_STATUSBAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";

    private static final int MODE_OPAQUE = 0;
    private static final int MODE_LIGHTS_OUT = 3;

    private static View mNavigationBarView;

    private static Object[] mRecentsKeys;
    private static HomeKeyInfo[] mHomeKeys;

    private static Resources mResources;
    private static Context mFiContext;
    private static NavbarViewInfo[] mNavbarViewInfo = new NavbarViewInfo[2];

    private static Drawable mRecentIcon, mRecentLandIcon;
    private static Drawable mRecentAltIcon, mRecentAltLandIcon;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    static class HomeKeyInfo {
        public ImageView homeKey;
        public boolean supportsLongPressDefault;
    }

    static class NavbarViewInfo {
        ViewGroup navButtons;
        View originalView;
        KeyButtonView mediaVolumeUp;
        KeyButtonView mediaVolumeDown;
        int mediaVolumeUpPosition;
        int mediaVolumeDownPosition;
        boolean visible;
        ViewGroup menuImeGroup;
    }

    public static void init(final ClassLoader classLoader) {
        try {
            final Class<?> navbarViewClass = XposedHelpers.findClass(CLASS_NAVBAR_VIEW, classLoader);
            final Class<?> navbarTransitionsClass = XposedHelpers.findClass(CLASS_NAVBAR_TRANSITIONS, classLoader);

            XposedBridge.hookAllConstructors(navbarViewClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    if (context == null) return;

                    mResources = context.getResources();

                    mFiContext = context.createPackageContext(
                            fixedinstall.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
                    final Resources res = mFiContext.getResources();

                    mNavigationBarView = (View) param.thisObject;

                    if (DEBUG) log("NavigationBarView constructed");
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final Context context = ((View) param.thisObject).getContext();
                    final Resources fiRes = mFiContext.getResources();
                    final int backButtonResId = mResources.getIdentifier("back", "id", PACKAGE_NAME);
                    final int recentAppsResId = mResources.getIdentifier("recent_apps", "id", PACKAGE_NAME);
                    final int homeButtonResId = mResources.getIdentifier("home", "id", PACKAGE_NAME);
                    final View[] rotatedViews = 
                            (View[]) XposedHelpers.getObjectField(param.thisObject, "mRotatedViews");

                    if (rotatedViews != null) {
                        mRecentsKeys = new Object[rotatedViews.length];
                        mHomeKeys = new HomeKeyInfo[rotatedViews.length];
                        int index = 0;
                        for(View v : rotatedViews) {
                            if (backButtonResId != 0) { 
                                ImageView backButton = (ImageView) v.findViewById(backButtonResId);
                                if (backButton != null) {
                                    backButton.setScaleType(ScaleType.FIT_CENTER);
                                }
                            }
                            if (recentAppsResId != 0) {
                                ImageView recentAppsButton = (ImageView) v.findViewById(recentAppsResId);
                                mRecentsKeys[index] = recentAppsButton;
                            }
                            if (homeButtonResId != 0) { 
                                HomeKeyInfo hkInfo = new HomeKeyInfo();
                                hkInfo.homeKey = (ImageView) v.findViewById(homeButtonResId);
                                if (hkInfo.homeKey != null) {
                                    hkInfo.supportsLongPressDefault = 
                                        XposedHelpers.getBooleanField(hkInfo.homeKey, "mSupportsLongpress");
                                }
                                mHomeKeys[index] = hkInfo;
                            }
                            index++;
                        }
                    }

                    // prepare app, dpad left, dpad right keys
                    ViewGroup vRot, navButtons;

                    // prepare keys for rot0 view
                    vRot = (ViewGroup) ((ViewGroup) param.thisObject).findViewById(
                            mResources.getIdentifier("rot0", "id", PACKAGE_NAME));
                    if (vRot != null) {
                        KeyButtonView mediaVolumeUpKey = new KeyButtonView(context);
                        mediaVolumeUpKey.setScaleType(ScaleType.CENTER);
                        mediaVolumeUpKey.setClickable(true);
                        mediaVolumeUpKey.setImageDrawable(fiRes.getDrawable(R.drawable.arrow_up_alpha));
                        mediaVolumeUpKey.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                AudioManager audioManage = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                                audioManage.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                                ;
                            }
                        });

                        KeyButtonView mediaVolumeDownKey = new KeyButtonView(context);
                        mediaVolumeDownKey.setScaleType(ScaleType.CENTER);
                        mediaVolumeDownKey.setClickable(true);
                        mediaVolumeDownKey.setImageDrawable(fiRes.getDrawable(R.drawable.arrow_down_alpha));
                        mediaVolumeDownKey.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                AudioManager audioManage = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                                audioManage.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                                ;
                            }
                        });

                        navButtons = (ViewGroup) vRot.findViewById(
                                mResources.getIdentifier("nav_buttons", "id", PACKAGE_NAME));
                        prepareNavbarViewInfo(navButtons, 0, mediaVolumeUpKey, mediaVolumeDownKey);//, dpadLeft, dpadRight);
                    }

                    // prepare keys for rot90 view
                    vRot = (ViewGroup) ((ViewGroup) param.thisObject).findViewById(
                            mResources.getIdentifier("rot90", "id", PACKAGE_NAME));
                    if (vRot != null) {
                        KeyButtonView mediaVolumeUpKey = new KeyButtonView(context);
                        mediaVolumeUpKey.setScaleType(ScaleType.CENTER);
                        mediaVolumeUpKey.setClickable(true);
                        mediaVolumeUpKey.setImageDrawable(fiRes.getDrawable(R.drawable.arrow_up_alpha));
                        mediaVolumeUpKey.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                AudioManager audioManage = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                                audioManage.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                                ;
                            }
                        });

                        KeyButtonView mediaVolumeDownKey = new KeyButtonView(context);
                        mediaVolumeDownKey.setScaleType(ScaleType.CENTER);
                        mediaVolumeDownKey.setClickable(true);
                        mediaVolumeDownKey.setImageDrawable(fiRes.getDrawable(R.drawable.arrow_down_alpha));
                        mediaVolumeDownKey.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                AudioManager audioManage = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                                audioManage.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                                ;
                            }
                        });

                        navButtons = (ViewGroup) vRot.findViewById(
                                mResources.getIdentifier("nav_buttons", "id", PACKAGE_NAME));
                        prepareNavbarViewInfo(navButtons, 1, mediaVolumeUpKey, mediaVolumeDownKey);//, dpadLeft, dpadRight);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "setDisabledFlags",
                    int.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        setCustomKeyVisibility();
//                        for (int i = 0; i <= 1; i++) {
//                            mNavbarViewInfo[i].navButtons.addView(mNavbarViewInfo[i].mediaVolumeUp,
//                                    mNavbarViewInfo[i].mediaVolumeUpPosition);
//                            mNavbarViewInfo[i].navButtons.addView(mNavbarViewInfo[i].mediaVolumeDown,
//                                    mNavbarViewInfo[i].mediaVolumeDownPosition);
//                        }
                    }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "getIcons", Resources.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mRecentIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mRecentIcon");
                    mRecentLandIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mRecentLandIcon");

                    if (mFiContext != null) {
                        final Resources fiRes = mFiContext.getResources();
                        mRecentAltIcon = fiRes.getDrawable(R.drawable.ic_sysbar_recent_clear_lollipop);
                        mRecentAltLandIcon = fiRes.getDrawable(R.drawable.ic_sysbar_recent_clear_land_lollipop);

                        XposedHelpers.setObjectField(param.thisObject, "mBackAltLandIcon",
                                    fiRes.getDrawable(R.drawable.ic_sysbar_back_ime_land));
                    }
                }
            });

            XposedHelpers.findAndHookMethod(navbarTransitionsClass, "applyMode",
                    int.class, boolean.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            final int mode = (Integer) param.args[0];
                            final boolean animate = (Boolean) param.args[1];
                            final boolean isOpaque = mode == MODE_OPAQUE || mode == MODE_LIGHTS_OUT;
                            final float alpha = isOpaque ? KeyButtonView.DEFAULT_QUIESCENT_ALPHA : 1f;
                            for(int i = 0; i < mNavbarViewInfo.length; i++) {
                                if (mNavbarViewInfo[i] != null) {
                                    if (mNavbarViewInfo[i].mediaVolumeUp != null) {
                                        mNavbarViewInfo[i].mediaVolumeUp.setQuiescentAlpha(alpha, animate);
                                        mNavbarViewInfo[i].mediaVolumeDown.setQuiescentAlpha(alpha, animate);
                                    }
                                }
                            }
                        }
                    });
        } catch(Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void prepareNavbarViewInfo(ViewGroup navButtons, int index,
            KeyButtonView mediaVolumeUpView, KeyButtonView mediaVolumeDownView) {//}, KeyButtonView dpadLeft, KeyButtonView dpadRight) {
        try {
            final int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    40, navButtons.getResources().getDisplayMetrics());
            if (DEBUG) log("App key view minimum size=" + size);

            mNavbarViewInfo[index] = new NavbarViewInfo();
            mNavbarViewInfo[index].navButtons = navButtons;
            mNavbarViewInfo[index].mediaVolumeUp = mediaVolumeUpView;
            mNavbarViewInfo[index].mediaVolumeDown = mediaVolumeDownView;

//            mNavbarViewInfo[index].dpadLeft = dpadLeft;
//            mNavbarViewInfo[index].dpadRight = dpadRight;
//            mNavbarViewInfo[index].navButtons.addView(mediaVolumeUpView, 0);
//            mNavbarViewInfo[index].navButtons.addView(mediaVolumeDownView, 1);

            int searchPosition = index == 0 ? 1 : navButtons.getChildCount()-1;
            View v = navButtons.getChildAt(searchPosition);
            if (v.getId() == -1 && !v.getClass().getName().equals(CLASS_KEY_BUTTON_VIEW) &&
                    !(v instanceof ViewGroup)) {
                mNavbarViewInfo[index].originalView = v;
            } else {
                searchPosition = searchPosition == 1 ? navButtons.getChildCount()-1 : 1;
                v = navButtons.getChildAt(searchPosition);
                if (v.getId() == -1 && !v.getClass().getName().equals(CLASS_KEY_BUTTON_VIEW) &&
                        !(v instanceof ViewGroup)) {
                    mNavbarViewInfo[index].originalView = v;
                }
            }
            mNavbarViewInfo[index].mediaVolumeUpPosition = searchPosition;
            mNavbarViewInfo[index].visible=true;

            // find ime switcher and menu group
            int childCount = mNavbarViewInfo[index].navButtons.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = mNavbarViewInfo[index].navButtons.getChildAt(i);
                if (child instanceof ViewGroup) {
                    mNavbarViewInfo[index].menuImeGroup = (ViewGroup)child;
                    break;
                }
            }

            // determine app key layout
            LinearLayout.LayoutParams lp = null;
            if (mNavbarViewInfo[index].originalView != null) {
                // determine layout from layout of placeholder view we found
                ViewGroup.LayoutParams ovlp = mNavbarViewInfo[index].originalView.getLayoutParams();
                if (DEBUG) log("originalView: lpWidth=" + ovlp.width + "; lpHeight=" + ovlp.height);
                if (ovlp instanceof LinearLayout.LayoutParams) {
                    lp = (LinearLayout.LayoutParams) ovlp;
                } else if (ovlp.width >= 0) {
                    lp = new LinearLayout.LayoutParams(ovlp.width, LinearLayout.LayoutParams.MATCH_PARENT, 0);
                } else if (ovlp.height >= 0) {
                    lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ovlp.height, 0);
                } else {
                    log("Weird layout of placeholder view detected");
                }
            } else {
                // determine layout from Back key
                final int resId = navButtons.getResources().getIdentifier("back", "id", PACKAGE_NAME);
                if (resId != 0) {
                    View back = navButtons.findViewById(resId);
                    if (back != null) {
                        ViewGroup.LayoutParams blp = back.getLayoutParams();
                        if (blp.width >= 0) {
                            lp = new LinearLayout.LayoutParams(blp.width, LinearLayout.LayoutParams.MATCH_PARENT, 0);
                        } else if (blp.height >= 0) {
                            lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, blp.height, 0);
                        } else {
                            log("Weird layout of back button view detected");
                        }
                    } else {
                        log("Could not find back button view");
                    }
                } else {
                    log("Could not find back button resource ID");
                }
            }
            // worst case scenario (should never happen, but just to make sure)
            if (lp == null) {
                lp = new LinearLayout.LayoutParams(size, size, 0);
            }
            if (DEBUG) log("appView: lpWidth=" + lp.width + "; lpHeight=" + lp.height);
            mNavbarViewInfo[index].mediaVolumeUp.setLayoutParams(lp);
            mNavbarViewInfo[index].mediaVolumeDown.setLayoutParams(lp);
        } catch (Throwable t) {
            log("Error preparing NavbarViewInfo: " + t.getMessage());
        }
    }

    private static void setCustomKeyVisibility() {
        try {
            final boolean visible = true;
            for (int i = 0; i <= 1; i++) {
                if (mNavbarViewInfo[i].originalView != null) {
                    if (DEBUG) log("setCustomKeyVisibility: Had to remove a view");
                    mNavbarViewInfo[i].navButtons.removeViewAt(mNavbarViewInfo[i].mediaVolumeUpPosition);
                    mNavbarViewInfo[i].navButtons.addView(visible ?
                                    mNavbarViewInfo[i].mediaVolumeUp : mNavbarViewInfo[i].originalView,
                            mNavbarViewInfo[i].mediaVolumeUpPosition);

                    mNavbarViewInfo[i].navButtons.removeViewAt(mNavbarViewInfo[i].mediaVolumeDownPosition);
                    mNavbarViewInfo[i].navButtons.addView(visible ?
                                    mNavbarViewInfo[i].mediaVolumeDown : mNavbarViewInfo[i].originalView,
                            mNavbarViewInfo[i].mediaVolumeDownPosition);
                } else {
                    if (visible) {
                        if (DEBUG) log("setCustomKeyVisibility: Adding views, no remove");
                        mNavbarViewInfo[i].navButtons.addView(mNavbarViewInfo[i].mediaVolumeUp,
                                mNavbarViewInfo[i].mediaVolumeUpPosition);
                        mNavbarViewInfo[i].navButtons.addView(mNavbarViewInfo[i].mediaVolumeDown,
                                mNavbarViewInfo[i].mediaVolumeDownPosition);
                    } else {
                        mNavbarViewInfo[i].navButtons.removeView(mNavbarViewInfo[i].mediaVolumeUp);
                        mNavbarViewInfo[i].navButtons.removeView(mNavbarViewInfo[i].mediaVolumeDown);
                    }
                }
                mNavbarViewInfo[i].visible = visible;
                mNavbarViewInfo[i].navButtons.requestLayout();
//                if (DEBUG) log("setAppKeyVisibility: visible=" + visible);
            }
        } catch (Throwable t) {
            log("Error setting custom key visibility: " + t.getMessage());
        }
    }
}
