package io.github.hecbatista.glasshome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * The link to the owner's Spotify account (spotify_connect): a client ID and a refresh token in the launcher's
 * private preferences. launcher/spotify-link.py push sends them over ADB to this receiver, which only the shell may
 * call (WRITE_SECURE_SETTINGS in the manifest); `unlink` removes them.
 */
public final class SpotifyLink extends BroadcastReceiver {
    static final String ACTION_LINK = "io.github.hecbatista.glasshome.action.SPOTIFY_LINK";
    static final String ACTION_UNLINK = "io.github.hecbatista.glasshome.action.SPOTIFY_UNLINK";
    private static final String PREFS = "spotify_link", CLIENT_ID = "client_id", REFRESH_TOKEN = "refresh_token";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (ACTION_UNLINK.equals(intent.getAction())) {
            prefs.edit().clear().commit();
            setResultData("unlinked");
            return;
        }
        if (!ACTION_LINK.equals(intent.getAction())) return;
        String id = intent.getStringExtra(CLIENT_ID), token = intent.getStringExtra(REFRESH_TOKEN);
        if (id == null || id.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            setResultData("missing client_id or refresh_token");
            return;
        }
        prefs.edit().putString(CLIENT_ID, id.trim()).putString(REFRESH_TOKEN, token.trim()).commit();
        setResultData("linked");
    }

    /** The API for the linked account, or null when there is no link yet. */
    static SpotifyApi api(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(CLIENT_ID, null), token = prefs.getString(REFRESH_TOKEN, null);
        if (id == null || token == null) return null;
        return new SpotifyApi(id, token, new SpotifyApi.UrlTransport(), System::currentTimeMillis,
                rotated -> prefs.edit().putString(REFRESH_TOKEN, rotated).apply());
    }
}
