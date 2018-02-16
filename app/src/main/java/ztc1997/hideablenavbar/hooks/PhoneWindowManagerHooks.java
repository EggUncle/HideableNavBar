/*
 * Copyright 2015-2016 Alex Zhang aka. ztc1997
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ztc1997.hideablenavbar.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.view.Display;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ztc1997.hideablenavbar.BuildConfig;
import ztc1997.hideablenavbar.SettingsActivity;

import static de.robv.android.xposed.XposedBridge.log;

public class PhoneWindowManagerHooks {
    public static final String TAG = PhoneWindowManagerHooks.class.getSimpleName() + ": ";
    public static final String ACTION_HIDE_NAV_BAR = "ztc1997.hideablenavbar.hooks.PhoneWindowManagerHooks.action.HIDE_NAV_BAR";

    public static GesturesListener sGesturesListener;

    private static int sNavBarWp, sNavBarHp, sNavBarHl, sNavBarWpOrigin, sNavBarHpOrigin, sNavBarHlOrigin;
    private static Object sPhoneWindowManager;
    private static Context sContext;
    private static boolean isHide;

    private static BroadcastReceiver sHideNavBarReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_HIDE_NAV_BAR:
                    hideNavBar();
                    isHide = true;
                    break;

                case SettingsActivity.ACTION_PREF_CHANGED:
                    if (intent.hasExtra(SettingsActivity.PREF_NAVBAR_WIDTH))
                        sNavBarWp = (int) ((float) sNavBarWpOrigin * intent.getIntExtra(SettingsActivity.PREF_NAVBAR_WIDTH, sNavBarWp) / 100);
                    if (intent.hasExtra(SettingsActivity.PREF_NAVBAR_HEIGHT_PORT))
                        sNavBarHp = (int) ((float) sNavBarHpOrigin * intent.getIntExtra(SettingsActivity.PREF_NAVBAR_HEIGHT_PORT, sNavBarHp) / 100);
                    if (intent.hasExtra(SettingsActivity.PREF_NAVBAR_HEIGHT_LAND))
                        sNavBarHl = (int) ((float) sNavBarHlOrigin * intent.getIntExtra(SettingsActivity.PREF_NAVBAR_HEIGHT_LAND, sNavBarHl) / 100);

                    showNavBar();
                    break;
            }
        }
    };

    public static void doHook(ClassLoader loader) throws Throwable {
        final XSharedPreferences preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);

        final String CLASS_PHONE_WINDOW_MANAGER = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ?
                "com.android.internal.policy.impl.PhoneWindowManager" :
                "com.android.server.policy.PhoneWindowManager";
        final String CLASS_IWINDOW_MANAGER = "android.view.IWindowManager";
        final String CLASS_WINDOW_MANAGER_FUNCS = "android.view.WindowManagerPolicy.WindowManagerFuncs";

        Class<?> pwmClass = loader.loadClass(CLASS_PHONE_WINDOW_MANAGER);
        XposedHelpers.findAndHookMethod(pwmClass, "init",
                Context.class, CLASS_IWINDOW_MANAGER, CLASS_WINDOW_MANAGER_FUNCS, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        log(TAG + XposedHelpers.getObjectField(param.thisObject, "mWindowManagerFuncs").getClass().getName());
                        sPhoneWindowManager = param.thisObject;
                        sContext = (Context) XposedHelpers.getObjectField(sPhoneWindowManager, "mContext");
                        Resources res = sContext.getResources();
                        int resWidthId = res.getIdentifier(
                                "navigation_bar_width", "dimen", "android");
                        int resHeightId = res.getIdentifier(
                                "navigation_bar_height", "dimen", "android");
                        int resHeightLandscapeId = res.getIdentifier(
                                "navigation_bar_height_landscape", "dimen", "android");
                        sNavBarWpOrigin = res.getDimensionPixelSize(resWidthId);
                        sNavBarHpOrigin = res.getDimensionPixelSize(resHeightId);
                        sNavBarHlOrigin = res.getDimensionPixelSize(resHeightLandscapeId);

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(ACTION_HIDE_NAV_BAR);
                        intentFilter.addAction(SettingsActivity.ACTION_PREF_CHANGED);
                        sContext.registerReceiver(sHideNavBarReceiver, intentFilter);

                        sGesturesListener = new GesturesListener(sContext, new GesturesListener.Callbacks() {
                            @Override
                            public void onSwipeFromTop() {

                            }

                            @Override
                            public void onSwipeFromBottom() {
                                showNavBar();
                                isHide = false;
                            }

                            @Override
                            public void onSwipeFromRight() {
                            }

                            @Override
                            public void onDebug() {

                            }
                        });
                    }
                }
        );

        XposedHelpers.findAndHookMethod(CLASS_PHONE_WINDOW_MANAGER, loader, "setInitialDisplaySize",
                Display.class, int.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        if (isHide) {
                            hideNavBar();
                        } else {
                            showNavBar();
                        }
                    }
                });

        final String CLASS_WINDOW_STATE = "android.view.WindowManagerPolicy$WindowState";
        XposedHelpers.findAndHookMethod(CLASS_PHONE_WINDOW_MANAGER, loader, "layoutWindowLw", CLASS_WINDOW_STATE, CLASS_WINDOW_STATE, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                sGesturesListener.screenWidth = XposedHelpers.getIntField(param.thisObject, "mUnrestrictedScreenWidth");
                sGesturesListener.screenHeight = XposedHelpers.getIntField(param.thisObject, "mUnrestrictedScreenHeight");
            }
        });
    }

    public static void showNavBar() {
        setNavBarDimensions(sNavBarWp, sNavBarHpOrigin, sNavBarHl);
    }

    public static void hideNavBar() {
        setNavBarDimensions(0, 0, 0);
    }


    private static void setNavBarDimensions(int wp, int hp, int hl) {
        int[] navigationBarHeightForRotation;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            navigationBarHeightForRotation = (int[]) XposedHelpers.getObjectField(
                    sPhoneWindowManager, "mNavigationBarHeightForRotation");
        } else {
            navigationBarHeightForRotation = (int[]) XposedHelpers.getObjectField(
                    sPhoneWindowManager, "mNavigationBarHeightForRotationDefault");
        }

        final int portraitRotation = XposedHelpers.getIntField(sPhoneWindowManager, "mPortraitRotation");
        final int upsideDownRotation = XposedHelpers.getIntField(sPhoneWindowManager, "mUpsideDownRotation");
        if (navigationBarHeightForRotation[portraitRotation] == hp)
            return;

        navigationBarHeightForRotation[portraitRotation] =
                navigationBarHeightForRotation[upsideDownRotation] =
                        hp;
        XposedHelpers.callMethod(sPhoneWindowManager, "updateRotation", false);
    }

    public static void sendNavBarHideIntent(Context context) {
        Intent intent = new Intent(PhoneWindowManagerHooks.ACTION_HIDE_NAV_BAR);
        context.sendBroadcast(intent);
    }
}
