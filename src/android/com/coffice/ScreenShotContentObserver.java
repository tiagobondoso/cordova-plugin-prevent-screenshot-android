package com.coffice;

import android.database.ContentObserver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.lang.System;
import java.lang.Throwable;

public abstract class ScreenShotContentObserver extends ContentObserver {
    private static final String TAG = "ScreenShotContentObserver";
    private static final long RECENT_THRESHOLD_MS = 10_000L;

    private final Context context;
    private boolean isFromEdit = false;
    private String previousPath;

    public ScreenShotContentObserver(Handler handler, Context context) {
        super(handler);
        this.context = context;
    }

    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        if (uri == null) {
            return;
        }

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, getProjection(), null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                processCursor(cursor);
            }
        } catch (Throwable t) {
            isFromEdit = true;
            Log.e(TAG, "Failed to inspect potential screenshot", t);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean isScreenshot(String path) {
        return path != null && path.toLowerCase().contains("screenshot");
    }

    private void processCursor(Cursor cursor) {
        String fileName = getString(cursor, MediaStore.Images.Media.DISPLAY_NAME);
        String absolutePath = getString(cursor, MediaStore.Images.Media.DATA);
        if (TextUtils.isEmpty(absolutePath)) {
            String relativePath = getString(cursor, MediaStore.Images.Media.RELATIVE_PATH);
            absolutePath = buildRelativePath(relativePath, fileName);
        }

        long dateAddedSeconds = getLong(cursor, MediaStore.Images.Media.DATE_ADDED);
        long lastModifiedMs = dateAddedSeconds > 0 ? dateAddedSeconds * 1000L : System.currentTimeMillis();

        if (!isRecent(lastModifiedMs)) {
            return;
        }

        if (isScreenshot(absolutePath) && !isDuplicate(absolutePath) && !isFromEdit) {
            onScreenShot(absolutePath, fileName);
        }

        previousPath = absolutePath;
        isFromEdit = false;
    }

    private boolean isDuplicate(String path) {
        return previousPath != null && previousPath.equals(path);
    }

    private boolean isRecent(long timestampMs) {
        return timestampMs >= System.currentTimeMillis() - RECENT_THRESHOLD_MS;
    }

    private String buildRelativePath(String relativePath, String fileName) {
        if (TextUtils.isEmpty(relativePath) && TextUtils.isEmpty(fileName)) {
            return null;
        }
        if (TextUtils.isEmpty(relativePath)) {
            return fileName;
        }
        if (TextUtils.isEmpty(fileName)) {
            return relativePath;
        }
        return relativePath + fileName;
    }

    private String[] getProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new String[] {
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATA
            };
        }
        return new String[] {
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
        };
    }

    private String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index == -1) {
            return null;
        }
        return cursor.getString(index);
    }

    private long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index == -1) {
            return 0L;
        }
        return cursor.getLong(index);
    }

    protected abstract void onScreenShot(String path, String fileName);

}
