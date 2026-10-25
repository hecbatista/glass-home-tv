package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Glass Home's Launcher settings ("Customize Home"), in Home's language: the background colour, Inter, 96 px glass
 * pills between Home's edges (x 112 and 1808), Home's solid focus pill, and a blue checkmark for the chosen value so
 * that focus and selection never look alike.
 */
final class GlassSettings {
    private final Activity activity;
    private final float density;
    private final boolean light;
    private final int ink;
    private final Typeface semibold, medium, regular;

    GlassSettings(Activity activity, int background) {
        this.activity = activity;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.light = Appearance.isLight(background);
        this.ink = Appearance.ink(light);
        this.semibold = activity.getResources().getFont(R.font.inter_semibold);
        this.medium = activity.getResources().getFont(R.font.inter_medium);
        this.regular = activity.getResources().getFont(R.font.inter_regular);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** The screen: background, title, the edges and the vertical rhythm (24 px between choices, 36 px sections). */
    void styleScreen(View scroll, LinearLayout content, TextView title, int background) {
        scroll.setBackgroundColor(background);
        scroll.setVerticalScrollBarEnabled(false);
        content.setPaddingRelative(dp(56), dp(38), dp(56), dp(48));
        title.setText(R.string.appearance);
        title.setTypeface(activity.getResources().getFont(R.font.inter_display_bold));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f);
        title.setLetterSpacing(-0.025f);
        title.setIncludeFontPadding(false);
        title.setTextColor(ink);
    }

    /** A section's name over its choices: Inter Medium, dimmed, 36 px under the pills above. */
    void section(TextView label, int text) {
        if (text != 0) label.setText(text);
        label.setTypeface(medium);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        label.setLetterSpacing(-0.005f);
        label.setIncludeFontPadding(false);
        label.setTextColor(Appearance.withAlphaOf(0x9EFFFFFF, ink));
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) label.getLayoutParams();
        lp.topMargin = dp(18);
        label.setLayoutParams(lp);
    }

    /** The grey line under a row ("Uses the current theme..."), in Inter at the same edge. */
    void hint(TextView hint) {
        hint.setTypeface(regular);
        hint.setTextColor(Appearance.withAlphaOf(0x9EFFFFFF, ink));
        hint.setLineSpacing(dp(2), 1f);
    }

    /** A row of choices: equal pills 24 px apart, the last one reaching the right edge. */
    void choiceRow(LinearLayout row, int topMarginDp) {
        ((ViewGroup.MarginLayoutParams) row.getLayoutParams()).topMargin = dp(topMarginDp);
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView choice = (TextView) row.getChildAt(i);
            pill(choice);
            choice.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) choice.getLayoutParams();
            lp.setMarginEnd(i == row.getChildCount() - 1 ? 0 : dp(12));
            choice.setLayoutParams(lp);
        }
    }

    /** A full-width row; {@code chevron} for one that opens something (Reorder Apps, the system settings). */
    void rowPill(TextView row, boolean chevron) {
        pill(row);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setPaddingRelative(dp(20), 0, dp(20), 0);
        if (chevron) {
            Drawable icon = activity.getDrawable(R.drawable.ic_chevron_right).mutate();
            icon.setBounds(0, 0, dp(17), dp(17));
            row.setCompoundDrawablesRelative(null, null, icon, null);
            row.setCompoundDrawableTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{focusedInk(), Appearance.withAlphaOf(0x99FFFFFF, ink)}));
        }
    }

    /** A pill's background: Home's solid focus pill, flat glass at rest. */
    StateListDrawable background() {
        float radius = dp(24);
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(radius);
        focused.setColor(light ? Appearance.INK_DARK : 0xF5FFFFFF);
        // Flat glass: Home's material reads the glow behind Home, which this screen does not have.
        GradientDrawable rest = new GradientDrawable();
        rest.setCornerRadius(radius);
        rest.setColor(light ? 0x99FFFFFF : 0x1FFFFFFF);
        if (light) rest.setStroke(Math.max(1, Math.round(density * 0.75f)), 0x1A000000);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, rest);
        return states;
    }

    /**
     * A full-width row with its label at the left and a value at the right, before an optional chevron ("Alarm and
     * Sleep … 7:00 AM ›", "Time … 7:00 AM"). The row takes focus; both texts follow its focus colours. Returns the
     * value view.
     */
    TextView valueRow(LinearLayout row, TextView label, boolean chevron, int valueAlpha) {
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(background());
        row.setFocusable(true);
        row.setClickable(true);
        row.setPaddingRelative(dp(20), 0, dp(20), 0);
        ViewGroup.LayoutParams rlp = row.getLayoutParams();
        if (rlp != null) {
            rlp.height = dp(48);
            row.setLayoutParams(rlp);
        }
        styleText(label);
        label.setDuplicateParentStateEnabled(true);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(activity);
        styleText(value);
        value.setDuplicateParentStateEnabled(true);
        value.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{Appearance.withAlphaOf(valueAlpha << 24, focusedInk()), Appearance.withAlphaOf(valueAlpha << 24, ink)}));
        value.setFontFeatureSettings("tnum");
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (chevron) {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(R.drawable.ic_chevron_right);
            icon.setDuplicateParentStateEnabled(true);
            icon.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{focusedInk(), Appearance.withAlphaOf(0x99FFFFFF, ink)}));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(17), dp(17));
            ilp.setMarginStart(dp(12));
            row.addView(icon, ilp);
        }
        return value;
    }

    private void styleText(TextView v) {
        v.setTextColor(Appearance.pillText(light));
        v.setTypeface(semibold);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        v.setLetterSpacing(-0.01f);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
    }

    private void pill(TextView v) {
        v.setBackground(background());
        v.setTextColor(Appearance.pillText(light));
        v.setTypeface(semibold);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        v.setLetterSpacing(-0.01f);
        v.setIncludeFontPadding(false);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.height = dp(48);
        v.setLayoutParams(lp);
    }

    private int focusedInk() {
        return light ? 0xFFFFFFFF : Appearance.INK_DARK;
    }

    /**
     * The choice's text: a blue checkmark when it is the chosen value (ink on the focused pill), then the swatch of a
     * background colour, then the label.
     */
    void label(TextView choice, CharSequence text, boolean selected, int swatch) {
        SpannableStringBuilder s = new SpannableStringBuilder();
        if (selected) {
            s.append("✓", new CheckSpan(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (swatch != 0) {
            s.append("●", new SwatchSpan(swatch), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        s.append(text);
        choice.setText(s);
        choice.setContentDescription(text);
    }

    /** Material Symbols' check, 18 dp, blue on the resting glass and in the focused pill's ink when focused. */
    private final class CheckSpan extends ReplacementSpan {
        private final Drawable icon = activity.getDrawable(R.drawable.ic_check).mutate();

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return dp(18 + 6);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint paint) {
            int size = dp(18);
            int color = paint.getColor() == focusedInk() ? focusedInk() : Appearance.BLUE;
            icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            int t = (top + bottom - size) / 2;
            icon.setBounds(Math.round(x), t, Math.round(x) + size, t + size);
            icon.draw(canvas);
        }
    }

    /** A background colour's dot, 18 dp, with a faint rim so Dark shows on dark glass and Light on light. */
    private final class SwatchSpan extends ReplacementSpan {
        private final int color;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwatchSpan(int color) {
            this.color = color;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return dp(18 + 8);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint paint) {
            float r = dp(9);
            float cx = x + r, cy = (top + bottom) / 2f;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
            canvas.drawCircle(cx, cy, r, fill);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(density);
            fill.setColor(Appearance.isLight(color) ? 0x33000000 : 0x59FFFFFF);
            canvas.drawCircle(cx, cy, r - density / 2f, fill);
        }
    }
}
