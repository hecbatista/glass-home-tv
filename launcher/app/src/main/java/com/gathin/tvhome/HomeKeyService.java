package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.UserManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here, for firmware that turns
 * that launcher on again after it was disabled (XGIMI's xrmservice does, on its own remote keys).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();
    /** sleep_mode: held ≡ Menu dims the screen (SleepMode). */
    private boolean sleepMode;
    private final SleepKey sleepKey = new SleepKey();
    private final android.os.Handler sleepTimer = new android.os.Handler(android.os.Looper.getMainLooper());
    /** The running helper, for the alarm and sleep mode to put the TV to standby (lockScreen). */
    private static java.lang.ref.WeakReference<HomeKeyService> running = new java.lang.ref.WeakReference<>(null);

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private BroadcastReceiver unlockReceiver;
    /**
     * stock_home_redirect: Home pressed while the stock home screen is already in front changes no window, so the
     * redirect never hears of it. Android 11 tells apps about the key through CLOSE_SYSTEM_DIALOGS, but Fire OS keeps
     * that broadcast from third-party apps and sends its own, amazon.intent.action.HOME_PRESSED, instead.
     */
    private BroadcastReceiver homeKeyReceiver;
    private static final String AMAZON_HOME_PRESSED = "amazon.intent.action.HOME_PRESSED";
    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private final Set<String> stockHome = new HashSet<>();

    /** Until when (uptime) the stock home screen may stay up: the Fire TV Home tile opened it on purpose. */
    private static volatile long redirectPausedUntil;
    /** Long enough for the stock launcher's windows to settle after it starts; Home after that comes back here. */
    private static final long REDIRECT_PAUSE_MS = 3000;

    /** The Fire TV Home tile is about to open the stock home screen (fire_home_tile). */
    static void pauseRedirect() {
        redirectPausedUntil = SystemClock.uptimeMillis() + REDIRECT_PAUSE_MS;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // The service is directBootAware: Android 11 skips a service that is not while the user is still
        // locked at boot, writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the
        // unlock. Config.load reads credential-protected storage, so wait for the unlock before reading it.
        UserManager users = getSystemService(UserManager.class);
        if (users.isUserUnlocked()) {
            loadConfig();
            return;
        }
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopWaitingForUnlock();
                loadConfig();
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked()) {
            stopWaitingForUnlock();
            loadConfig();
        }
    }

    private void stopWaitingForUnlock() {
        if (unlockReceiver == null) return;
        unregisterReceiver(unlockReceiver);
        unlockReceiver = null;
    }

    @Override
    public void onDestroy() {
        stopWaitingForUnlock();
        if (homeKeyReceiver != null) {
            unregisterReceiver(homeKeyReceiver);
            homeKeyReceiver = null;
        }
        super.onDestroy();
    }

    private void loadConfig() {
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop. Here, not
        // in onServiceConnected: the note is in credential-protected storage too, readable only after the unlock.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        sleepMode = config.sleepMode && AlarmScheduler.prefs(this).getBoolean(SLEEP_KEY, false);
        running = new java.lang.ref.WeakReference<>(this);
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        stockHome.clear();
        if (!config.stockHomeRedirect.isEmpty()) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
        }
        if (!stockHome.isEmpty() && homeKeyReceiver == null) {
            homeKeyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String reason = intent.getStringExtra("reason");
                    boolean home = AMAZON_HOME_PRESSED.equals(intent.getAction()) || "homekey".equals(reason);
                    android.util.Log.i("HomeKeyService", "home key broadcast: " + intent.getAction() + " " + reason);
                    if (!home || SystemClock.uptimeMillis() < redirectPausedUntil) return;
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(getPackageName())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                }
            };
            IntentFilter keys = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            keys.addAction(AMAZON_HOME_PRESSED);
            registerReceiver(homeKeyReceiver, keys);
        }
        // home_key_service.xml subscribes to no events, so the redirect asks for window changes only when it is on.
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null && !stockHome.isEmpty()) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    /** Launcher settings' Sleep Mode switch; the helper reads it when it starts (glass-install.sh, a reboot). */
    static final String SLEEP_KEY = "sleep_mode_on";

    /** Sleep Mode was switched in Launcher settings: the running helper follows at once. */
    static void setSleepMode(boolean on) {
        HomeKeyService s = running.get();
        if (s != null) s.sleepMode = on && Config.load(s).sleepMode;
        if (!on) SleepMode.clear();
    }

    static boolean isRunning() {
        return running.get() != null;
    }

    /** Puts the TV to standby, as the power button does; false when the helper isn't running or Android refuses. */
    static boolean lockScreen() {
        HomeKeyService s = running.get();
        if (s == null || android.os.Build.VERSION.SDK_INT < 28) return false;
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (sleepMode && sleepKeys(event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) return true;
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS);
                try {
                    startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (RemoteKeys.handle(this, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    /**
     * Sleep mode's keys: the rest of a press that woke the screen, then held ≡ Menu, then a press that wakes it. True
     * consumes the key; a short ≡ press and volume keys pass on.
     */
    private boolean sleepKeys(KeyEvent event) {
        if (!SleepMode.isOn() && SleepMode.onKey(event)) return true;   // the tail of the waking press
        switch (sleepKey.on(event.getKeyCode(), event.getAction(), event.getDownTime(), event.getEventTime())) {
            case START:
                SleepMode.dim(this);
                return true;
            case CONSUME:
                return true;
            default:
                if (SleepMode.onKey(event)) return true;
                if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0) {
                    // The filter doesn't see Android's key repeats, so the hold is timed from the down.
                    final long down = event.getDownTime();
                    sleepTimer.postDelayed(() -> {
                        if (sleepKey.held(down)) SleepMode.dim(this);
                    }, SleepKey.HOLD_MS);
                }
                return false;
        }
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        running = new java.lang.ref.WeakReference<>(null);
        SleepMode.clear();
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // stock_home_redirect: the firmware turned the stock launcher back on and its home screen came up.
        if (stockHome.isEmpty() || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (cls != null && stockHome.contains(cls.toString())) {
            // The Fire TV Home tile asked for this screen: let it come up, and only send the TV back after that.
            if (SystemClock.uptimeMillis() < redirectPausedUntil) return;
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }
    }

    @Override
    public void onInterrupt() {
    }
}
