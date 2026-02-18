package com.coffice;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.WindowManager;
import android.Manifest;

import org.apache.cordova.*;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.lang.Exception;
import java.lang.Runnable;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScreenshotBlocker extends CordovaPlugin {
    private com.coffice.ScreenshotBlocker mContext;

    private static final String TAG = "ScreenshotBlocker";
    private static final int REQUEST_SCREENSHOT_PERMISSION = 8733;
    private static final String PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES";

    static ScreenshotBlocker instance = null;
    static CordovaWebView cordovaWebView;
    static CordovaInterface cordovaInterface;
    private Context context = null;

    private boolean useDetectSS = false;
    private HandlerThread observerThread;
    private ScreenShotContentObserver screenShotContentObserver;
    private boolean observerRegistered = false;
    private CallbackContext pendingDetectCallback;
    private final CopyOnWriteArrayList<CallbackContext> listenerCallbacks = new CopyOnWriteArrayList<>();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Activity activity = this.cordova.getActivity();
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        instance = this;
        cordovaWebView = webView;
        cordovaInterface = cordova;
        context = activity.getApplicationContext();

    }

    @Override
    public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
        mContext = this;

        if (action.equals("enable")) {
            mContext.cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        // Allow to make screenshots removing the FLAG_SECURE
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                            mContext.cordova.getActivity().getWindow()
                                    .clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                        }
                        callbackContext.success("Success");
                        // triggerJavascriptEvent("TestScreenshotEvent");
                    } catch (Exception e) {
                        callbackContext.error(e.toString());
                    }
                }
            });

            return true;
        } else if (action.equals("disable")) {
            mContext.cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        // Allow to make screenshots removing the FLAG_SECURE
                        // Disable the creation of screenshots adding the FLAG_SECURE to the window
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                            mContext.cordova.getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                                    WindowManager.LayoutParams.FLAG_SECURE);
                        }
                        callbackContext.success("Success");
                    } catch (Exception e) {
                        callbackContext.error(e.toString());
                    }
                }
            });
            return true;
        } else if (action.equals("activateDetect")) {
            mContext.cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        useDetectSS = true;

                        if (!hasScreenshotPermissions()) {
                            pendingDetectCallback = callbackContext;
                            requestScreenshotPermissions();
                            return;
                        }

                        startScreenshotObserver();
                        callbackContext.success("Success");
                    } catch (Exception e) {
                        useDetectSS = false;
                        callbackContext.error(e.toString());
                    }
                }
            });
            return true;
        } else if (action.equals("deactivateDetect")) {
            mContext.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        useDetectSS = false;
                        stopScreenshotObserver();
                        callbackContext.success("Success");
                    } catch (Exception e) {
                        callbackContext.error(e.toString());
                    }
                }
            });
            return true;
        } else if (action.equals("listen")) {
            registerScreenshotListener(callbackContext);
            return true;
        } else {
            return false;
        }

    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (useDetectSS && screenShotContentObserver != null) {
            registerScreenshotObservers();
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        if (useDetectSS) {
            unregisterScreenshotObservers();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenshotObserver();
        listenerCallbacks.clear();
        useDetectSS = false;
        instance = null;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
            throws JSONException {
        if (requestCode == REQUEST_SCREENSHOT_PERMISSION) {
            boolean granted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                startScreenshotObserver();
                if (pendingDetectCallback != null) {
                    pendingDetectCallback.success("Success");
                    pendingDetectCallback = null;
                }
            } else {
                useDetectSS = false;
                if (pendingDetectCallback != null) {
                    pendingDetectCallback.error("Screenshot detection permission denied");
                    pendingDetectCallback = null;
                }
            }
            return;
        }

        super.onRequestPermissionResult(requestCode, permissions, grantResults);
    }

    private boolean hasScreenshotPermissions() {
        String[] permissions = getScreenshotPermissions();
        for (String permission : permissions) {
            if (!PermissionHelper.hasPermission(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void requestScreenshotPermissions() {
        PermissionHelper.requestPermissions(this, REQUEST_SCREENSHOT_PERMISSION, getScreenshotPermissions());
    }

    private String[] getScreenshotPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[] { PERMISSION_READ_MEDIA_IMAGES };
        }
        return new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
    }

    private void startScreenshotObserver() {
        if (context == null) {
            return;
        }

        if (screenShotContentObserver == null) {
            observerThread = new HandlerThread("screenshot_content_observer");
            observerThread.start();
            Handler handler = new Handler(observerThread.getLooper());
            screenShotContentObserver = new ScreenShotContentObserver(handler, context) {
                @Override
                protected void onScreenShot(String path, String fileName) {
                    emitScreenshotEvent();
                }
            };
        }

        registerScreenshotObservers();
    }

    private void registerScreenshotObservers() {
        if (observerRegistered || screenShotContentObserver == null || context == null) {
            return;
        }
        try {
            context.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
                    screenShotContentObserver);
            context.getContentResolver().registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, true,
                    screenShotContentObserver);
            observerRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to register screenshot observer", e);
        }
    }

    private void unregisterScreenshotObservers() {
        if (!observerRegistered || screenShotContentObserver == null || context == null) {
            return;
        }
        try {
            context.getContentResolver().unregisterContentObserver(screenShotContentObserver);
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister screenshot observer", e);
        } finally {
            observerRegistered = false;
        }
    }

    private void stopScreenshotObserver() {
        unregisterScreenshotObservers();
        if (observerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                observerThread.quitSafely();
            } else {
                observerThread.quit();
            }
            observerThread = null;
        }
        screenShotContentObserver = null;
    }

    private void registerScreenshotListener(CallbackContext callbackContext) {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        listenerCallbacks.add(callbackContext);
        callbackContext.sendPluginResult(pluginResult);
    }

    private void emitScreenshotEvent() {
        triggerJavascriptEvent("onTookScreenshot");
        sendEventToJsListeners("tookScreenshot");
    }

    private void sendEventToJsListeners(String payload) {
        for (CallbackContext listener : listenerCallbacks) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, payload);
            pluginResult.setKeepCallback(true);
            listener.sendPluginResult(pluginResult);
        }
    }

    // https://stackoverflow.com/questions/54939027/triggering-javascript-event-from-android
    // how to trigger an event from android to JS
    private static void executeGlobalJavascript(final String jsString) {
        if (instance == null) {
            return;
        }
        instance.cordovaInterface.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    instance.cordovaWebView.loadUrl("javascript:" + jsString);
                } catch (Exception e) {
                    Log.e(TAG, "Error executing javascript: " + e.toString());
                }
            }
        });
    }

    public static void triggerJavascriptEvent(final String eventName) {
        executeGlobalJavascript(String.format("document.dispatchEvent(new Event('%s'));", eventName));
    }

}
