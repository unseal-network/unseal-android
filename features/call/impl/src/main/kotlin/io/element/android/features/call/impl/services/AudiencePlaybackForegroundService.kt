/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.ui.ElementCallActivity
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.push.api.notifications.ForegroundServiceType
import io.element.android.libraries.push.api.notifications.NotificationIdProvider
import timber.log.Timber

class AudiencePlaybackForegroundService : Service() {
    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AudiencePlaybackForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudiencePlaybackForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notificationManager = NotificationManagerCompat.from(this)
        val channel = NotificationChannelCompat.Builder(
            AUDIENCE_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        ).setName(getString(R.string.common_meeting_listener_notification_channel)).build()
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntentCompat.getActivity(
            this,
            0,
            Intent(this, ElementCallActivity::class.java),
            0,
            false,
        )
        val notification = NotificationCompat.Builder(this, channel.id)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(getString(R.string.common_meeting_listener_notification_title))
            .setContentText(getString(R.string.common_meeting_listener_notification_message))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        val notificationId = NotificationIdProvider.getForegroundServiceNotificationId(ForegroundServiceType.ONGOING_CALL)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        runCatchingExceptions {
            ServiceCompat.startForeground(this, notificationId, notification, serviceType)
        }.onFailure {
            Timber.e(it, "Failed to start audience playback foreground service")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private const val AUDIENCE_CHANNEL_ID = "audience_playback_foreground_service_channel"
