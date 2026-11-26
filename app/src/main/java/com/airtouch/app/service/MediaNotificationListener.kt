package com.airtouch.app.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.airtouch.app.AirTouchApplication

class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        val container = (application as AirTouchApplication).container
        container.mediaRepository.onConnected(
            this,
            ComponentName(this, MediaNotificationListener::class.java),
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        (application as AirTouchApplication).container.mediaRepository.onDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}
