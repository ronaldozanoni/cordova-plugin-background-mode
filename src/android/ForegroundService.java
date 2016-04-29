/*
    Copyright 2013-2014 appPlant UG

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

package de.appplant.cordova.plugin.background;

import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * Puts the service in a foreground state, where the system considers it to be
 * something the user is actively aware of and thus not a candidate for killing
 * when low on memory.
 */
public class ForegroundService extends Service {

    // Fixed ID for the 'foreground' notification
    private static final int NOTIFICATION_ID = -574543954;

    private Notification.Builder notification;

    // Binder given to clients
    private final IBinder mBinder = new ForegroundBinder();

    // Scheduler to exec periodic tasks
    final Timer scheduler = new Timer();

    // Used to keep the app alive
    TimerTask keepAliveTask;

    /**
     * Allow clients to call on to the service.
     */
    @Override
    public IBinder onBind (Intent intent) {
        return mBinder;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class ForegroundBinder extends Binder {
        ForegroundService getService() {
            // Return this instance of ForegroundService so clients can call public methods
            return ForegroundService.this;
        }
    }

    /**
     * Put the service in a foreground state to prevent app from being killed
     * by the OS.
     */
    @Override
    public void onCreate () {
        super.onCreate();
        keepAwake();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sleepWell();
    }

    /**
     * Put the service in a foreground state to prevent app from being killed
     * by the OS.
     */
    public void keepAwake() {
        final Handler handler = new Handler();

        if (!this.inSilentMode()) {
            startForeground(NOTIFICATION_ID, makeNotification());
        } else {
            Log.w("BackgroundMode", "In silent mode app may be paused by OS!");
        }

        BackgroundMode.deleteUpdateSettings();

        keepAliveTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Nothing to do here
                        // Log.d("BackgroundMode", "" + new Date().getTime());
                    }
                });
            }
        };

        scheduler.schedule(keepAliveTask, 0, 1000);
    }

    /**
     * Stop background mode.
     */
    private void sleepWell() {
        stopForeground(true);
        keepAliveTask.cancel();
    }

    /**
     * Create a notification as the visible part to be able to put the service
     * in a foreground state.
     *
     * @return
     *      A local ongoing notification which pending intent is bound to the
     *      main activity.
     */
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private Notification makeNotification() {
        JSONObject settings = BackgroundMode.getSettings();
        Context context     = getApplicationContext();
        String pkgName      = context.getPackageName();
        Intent intent       = context.getPackageManager()
                .getLaunchIntentForPackage(pkgName);

        notification = new Notification.Builder(context)
            .setContentTitle(settings.optString("title", ""))
            .setContentText(settings.optString("text", ""))
            .setTicker(settings.optString("ticker", ""))
            .setOngoing(true)
            .setSmallIcon(getIconResId());

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(settings.optBoolean("isPublic") == true) {
                notification.setVisibility(Notification.VISIBILITY_PUBLIC);
            }

            if(!settings.optString("color").equals("")) {
                try {
                    notification.setColor(Color.parseColor(settings.optString("color")));
                } catch (Exception e) {
                    Log.e("BackgroundMode", settings.optString("color") + " is not a valid color");
                }
            }
        }

        if (intent != null && settings.optBoolean("resume")) {

            PendingIntent contentIntent = PendingIntent.getActivity(
                    context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            notification.setContentIntent(contentIntent);
        }


        if (Build.VERSION.SDK_INT < 16) {
            // Build notification for HoneyComb to ICS
            return notification.getNotification();
        } else {
            // Notification for Jellybean and above
            return notification.build();
        }
    }

    public void updateNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (this.inSilentMode()) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        } else {
            Notification n = makeNotification();
            mNotificationManager.notify(NOTIFICATION_ID, n);
        }
    }

    /**
     * Retrieves the resource ID of the app icon.
     *
     * @return
     *      The resource ID of the app icon
     */
    private int getIconResId() {
        JSONObject settings = BackgroundMode.getSettings();
        Context context = getApplicationContext();
        Resources res   = context.getResources();
        String pkgName  = context.getPackageName();

        int resId = res.getIdentifier(settings.optString("icon", "icon"), "drawable", pkgName);

        return resId;
    }

    /**
     * In silent mode no notification has to be added.
     *
     * @return
     *      True if silent: was set to true
     */
    private boolean inSilentMode() {
        JSONObject settings = BackgroundMode.getSettings();

        return settings.optBoolean("silent", false);
    }
}
