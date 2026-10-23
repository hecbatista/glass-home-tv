package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private final TextView[] backgroundChoices = new TextView[Appearance.COLOURS.length];
    private int background;
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;
    /** Glass Home's look for this screen (GlassSettings); null keeps the original one. */
    private GlassSettings glass;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        // Glass Home opens this screen from its Customize Home button, so Reorder Apps leads it there too.
        boolean showReorder = config.launcherSettingsButton || config.glassHome;
        reorderChoice.setVisibility(showReorder ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        background = config.backgroundColor;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Glass Home only: the background row (Light, Grey, Dark and three colours). Without it the rows stay hidden.
        int[] backgroundIds = {R.id.bgLight, R.id.bgGrey, R.id.bgDark, R.id.bgNavy, R.id.bgPlum, R.id.bgPine};
        for (int i = 0; i < backgroundIds.length; i++) {
            final int value = Appearance.COLOURS[i];
            backgroundChoices[i] = findViewById(backgroundIds[i]);
            backgroundChoices[i].setOnClickListener(v -> {
                if (background == value) return;
                background = value;
                choices.edit().putInt(Config.BACKGROUND_KEY, value).apply();
                changed();
                refresh();
            });
        }
        if (!config.glassHome) {
            for (int id : new int[]{R.id.backgroundLabel, R.id.backgroundRow1, R.id.backgroundRow2}) {
                findViewById(id).setVisibility(View.GONE);
            }
        } else {
            styleGlass(config);
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.BACKGROUND_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            background = current.backgroundColor;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (showReorder) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    /** Glass Home: Home's background, type, edges and focus (GlassSettings), and plainer labels. */
    private void styleGlass(Config config) {
        glass = new GlassSettings(this, config.backgroundColor);
        glass.styleScreen(findViewById(R.id.settingsScroll), findViewById(R.id.settingsContent),
                findViewById(R.id.settingsTitle), config.backgroundColor);
        TextView reorder = findViewById(R.id.reorderChoice);
        reorder.setText(R.string.glass_reorder);
        glass.rowPill(reorder, true);
        ((ViewGroup.MarginLayoutParams) reorder.getLayoutParams()).topMargin = Math.round(14 * getResources().getDisplayMetrics().density);
        // All six backgrounds in one row, like the other choices.
        LinearLayout first = findViewById(R.id.backgroundRow1);
        LinearLayout second = findViewById(R.id.backgroundRow2);
        while (second.getChildCount() > 0) {
            View v = second.getChildAt(0);
            second.removeViewAt(0);
            first.addView(v);
        }
        second.setVisibility(View.GONE);
        glass.section(findViewById(R.id.columnsLabel), 0);
        glass.section(findViewById(R.id.rowsLabel), R.string.glass_rows_label);
        glass.section(findViewById(R.id.dockLabel), R.string.glass_dock_label);
        glass.section(findViewById(R.id.backgroundLabel), 0);
        // The first section sits 36 px under Reorder Apps, as the others sit under their choices.
        for (int id : new int[]{R.id.columnsRow, R.id.rowsRow, R.id.dockRow, R.id.backgroundRow1}) {
            glass.choiceRow(findViewById(id), 12);
        }
        for (int id : new int[]{R.id.wallpaperChoice, R.id.animationsChoice, R.id.advancedChoice, R.id.followThemeChoice}) {
            glass.rowPill(findViewById(id), false);
        }
        glass.rowPill(findViewById(R.id.defaultHomeChoice), true);
        glass.rowPill(findViewById(R.id.tvSettingsChoice), true);
        ((TextView) findViewById(R.id.followThemeChoice)).setText(R.string.glass_follow_theme);
        for (int id : new int[]{R.id.advancedHint, R.id.followHint, R.id.homeHint}) glass.hint(findViewById(id));
        // Sections 36 px apart: the rows below the choices start one section gap under them.
        for (int id : new int[]{R.id.wallpaperChoice, R.id.followThemeChoice, R.id.defaultHomeChoice}) {
            ((ViewGroup.MarginLayoutParams) findViewById(id).getLayoutParams()).topMargin = Math.round(18 * getResources().getDisplayMetrics().density);
        }
        for (int id : new int[]{R.id.animationsChoice, R.id.advancedChoice, R.id.tvSettingsChoice}) {
            ((ViewGroup.MarginLayoutParams) findViewById(id).getLayoutParams()).topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        }
    }

    /** Glass Home's labels for a row of choices, with the checkmark on the chosen one. */
    private void glassLabels(TextView[] views, CharSequence[] labels, int chosen, int[] swatches) {
        for (int i = 0; i < views.length; i++) {
            glass.label(views[i], labels[i], i == chosen, swatches == null ? 0 : swatches[i]);
        }
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < backgroundChoices.length; i++) {
            backgroundChoices[i].setSelected(background == Appearance.COLOURS[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
        if (glass != null) {
            // The checkmark marks the chosen value; selection draws nothing else, so it never looks like focus.
            glassLabels(columnChoices, new CharSequence[]{"4", "5", "6", "7"}, columns - 4, null);
            glassLabels(rowChoices, new CharSequence[]{getString(R.string.glass_rows_none), "1", "2", "3"}, rowsBelowDock, null);
            glassLabels(dockChoices, new CharSequence[]{getString(R.string.glass_dock_solid), "70%", "50%", "30%"},
                    indexOf(DOCK_OPACITIES, dockOpacity), null);
            glassLabels(backgroundChoices, new CharSequence[]{getString(R.string.background_light),
                    getString(R.string.background_grey), getString(R.string.background_dark),
                    getString(R.string.background_navy), getString(R.string.background_plum),
                    getString(R.string.background_pine)}, indexOf(Appearance.COLOURS, background), Appearance.COLOURS);
            followThemeChoice.setSelected(false);
            followThemeChoice.setAlpha(hasOnTvChoices() ? 1f : 0.5f);
        }
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.BACKGROUND_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
