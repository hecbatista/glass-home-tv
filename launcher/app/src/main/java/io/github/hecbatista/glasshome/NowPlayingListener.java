package io.github.hecbatista.glasshome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the Now Playing panel may read the TV's media sessions: MediaSessionManager.getActiveSessions
 * answers an app that has an enabled notification listener. It reads no notifications. Fire OS has no settings
 * screen for it, so glass-install.sh grants it with `cmd notification allow_listener`.
 */
public class NowPlayingListener extends NotificationListenerService {
}
