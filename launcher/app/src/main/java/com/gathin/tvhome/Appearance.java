package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Glass Home (config glass_home): the colours that follow the background, the liquid-glass pills and the
 * Control Center modules for Wi-Fi and Settings. A light background_color gets dark ink, a dark one white ink.
 */
final class Appearance {
    /** The three backgrounds Launcher settings offers, then a few colours. */
    static final int LIGHT = 0xFFECEEF3, GREY = 0xFF3A3B40, DARK = 0xFF0B0B0F;
    static final int[] COLOURS = {LIGHT, GREY, DARK, 0xFF1D3557, 0xFF2E1F47, 0xFF16302B};

    static final int INK_DARK = 0xFF111216;
    static final int BLUE = 0xFF0A84FF;

    private Appearance() {}

    /** True when the background is bright enough that white text would not read. */
    static boolean isLight(int background) {
        double lum = (0.2126 * Color.red(background) + 0.7152 * Color.green(background)
                + 0.0722 * Color.blue(background)) / 255.0;
        return lum > 0.6;
    }

    /** The text colour for this background, at full strength. */
    static int ink(boolean light) {
        return light ? INK_DARK : 0xFFFFFFFF;
    }

    /** The same colour with the alpha of {@code like}: #B3FFFFFF becomes #B3111216 on a light background. */
    static int withAlphaOf(int like, int ink) {
        return (like & 0xFF000000) | (ink & 0x00FFFFFF);
    }

    /**
     * White text anywhere under {@code view} turns into dark ink with the same alpha, so dimmed lines stay dimmed.
     * Views with a state-dependent colour (the pills) are left to {@link #stylePill}.
     */
    static void inkTree(View view, boolean light) {
        if (!light) return;
        if (view instanceof TextView) {
            TextView t = (TextView) view;
            ColorStateList colors = t.getTextColors();
            if (!colors.isStateful() && (colors.getDefaultColor() & 0x00FFFFFF) == 0x00FFFFFF) {
                t.setTextColor(withAlphaOf(colors.getDefaultColor(), INK_DARK));
            }
        } else if (view.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
            // Hairline dividers (#33FFFFFF) go dark too.
            int c = ((android.graphics.drawable.ColorDrawable) view.getBackground()).getColor();
            if ((c & 0x00FFFFFF) == 0x00FFFFFF && Color.alpha(c) < 0xFF) view.setBackgroundColor(withAlphaOf(c, INK_DARK));
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) inkTree(g.getChildAt(i), true);
        }
    }

    /**
     * Liquid glass for a pill of this corner radius: a translucent fill that is brighter at the top, a fine rim and
     * a soft highlight over the upper half. Focused, the pill turns solid: white on a dark background, ink on a light one.
     */
    static Drawable glassPill(View host, float radius, float density, boolean light) {
        GradientDrawable focused = new GradientDrawable();
        focused.setCornerRadius(radius);
        focused.setColor(light ? INK_DARK : 0xF5FFFFFF);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, glassPanel(host, radius, density, light));
        return states;
    }

    /**
     * Flat translucent glass: one even see-through fill with no gradient, rim or shine, so the glow shows through
     * (12% white on a dark background, 60% on a light one). False brings back the emulated glass below.
     */
    static final boolean FLAT_GLASS = true;

    /** The resting glass alone, for a panel that never takes focus itself (Now Playing). */
    static Drawable glassPanel(View host, float radius, float density, boolean light) {
        // Home's material when Glass Home is up (GlassMaterial): the glow behind, toward white and saturated, with a
        // lit rim. The flat and gradient fills below remain for anything drawn before Home sets the material up.
        if (GlassMaterial.current != null) return new GlassDrawable(GlassMaterial.current, host, radius);
        if (FLAT_GLASS) {
            GradientDrawable flat = new GradientDrawable();
            flat.setCornerRadius(radius);
            flat.setColor(light ? 0x99FFFFFF : 0x1FFFFFFF);
            return flat;
        }
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, light
                ? new int[]{0xD9FFFFFF, 0xA6FFFFFF} : new int[]{0x38FFFFFF, 0x12FFFFFF});
        fill.setCornerRadius(radius);
        fill.setStroke(Math.max(1, Math.round(density)), light ? 0x1F000000 : 0x47FFFFFF);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{light ? 0x99FFFFFF : 0x2EFFFFFF, 0x00FFFFFF});
        shine.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        LayerDrawable glass = new LayerDrawable(new Drawable[]{fill, shine});
        int inset = Math.round(1.5f * density);
        glass.setLayerInset(1, inset, inset, inset, 0);
        glass.setLayerGravity(1, android.view.Gravity.TOP | android.view.Gravity.FILL_HORIZONTAL);
        glass.setLayerHeight(1, Math.round(radius));
        return glass;
    }

    /** Text colours for a glass pill: ink at rest, the opposite on the solid focused pill. */
    static ColorStateList pillText(boolean light) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{light ? 0xFFFFFFFF : INK_DARK, ink(light)});
    }

    /**
     * Control Center's round button: a white disc with a blue glyph, turning blue with a white glyph when the
     * module is focused. {@code iconRes} is one of the 24dp outline icons the pills already use.
     */
    static Drawable disc(Context ctx, int iconRes, float density, boolean light) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, discLayer(ctx, iconRes, density, BLUE, 0xFFFFFFFF, false));
        states.addState(new int[]{}, discLayer(ctx, iconRes, density, 0xFFFFFFFF, BLUE, light));
        int size = Math.round(44 * density);
        states.setBounds(0, 0, size, size);
        return states;
    }

    /**
     * One disc drawn into a bitmap. A layered drawable would not survive the TextView: it mutates its compound
     * drawables, which rebuilds the layers from their constant state and drops the glyph's colour.
     */
    private static Drawable discLayer(Context ctx, int iconRes, float density, int discColor, int iconColor, boolean outline) {
        int size = Math.round(44 * density);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(discColor);
        float r = size / 2f;
        canvas.drawCircle(r, r, outline ? r - density : r, paint);
        if (outline) {
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(0x1F000000);
            canvas.drawCircle(r, r, r - density / 2f, paint);
        }
        Drawable icon = ctx.getDrawable(iconRes).mutate();
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN));
        int inset = Math.round(11 * density);
        icon.setBounds(inset, inset, size - inset, size - inset);
        icon.draw(canvas);
        android.graphics.drawable.BitmapDrawable disc = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bmp);
        disc.setBounds(0, 0, size, size);
        return disc;
    }

    /** Dims the text under it to 60% of whatever colour the view draws in now, so it follows the focus state. */
    static final class Dim extends CharacterStyle implements UpdateAppearance {
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor((Math.round(Color.alpha(tp.getColor()) * 0.62f) << 24) | (tp.getColor() & 0x00FFFFFF));
        }
    }

    /** "Wi-Fi" in bold over the network name, dimmer and smaller: Control Center's two-line module label. */
    static CharSequence moduleLabel(CharSequence title, CharSequence status, float density) {
        String t = title.toString();
        if (status == null || status.length() == 0) return t;
        SpannableString s = new SpannableString(t + "\n" + status);
        int at = t.length() + 1;
        s.setSpan(new AbsoluteSizeSpan(Math.round(14 * density)), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new Dim(), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Control Center's small round button: a 64dp glass circle with one glyph, solid when focused. */
    static TextView roundButton(Context ctx, int iconRes, int label, float density, boolean light) {
        TextView b = new TextView(ctx);
        int size = Math.round(64 * density);
        b.setBackground(glassPill(b, size / 2f, density, light));
        Drawable icon = ctx.getDrawable(iconRes).mutate();
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        b.setCompoundDrawablesRelative(icon, null, null, null);
        b.setCompoundDrawableTintList(pillText(light));
        int pad = (size - icon.getIntrinsicWidth()) / 2;
        b.setPaddingRelative(pad, 0, pad, 0);
        b.setGravity(android.view.Gravity.CENTER);
        b.setContentDescription(ctx.getString(label));
        b.setFocusable(true);
        b.setClickable(true);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(Math.round(12 * density));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        b.setLayoutParams(lp);
        return b;
    }

    /** Turns a top-bar pill into a Control Center module: 64dp tall, a disc on the left, two lines of text. */
    static void makeModule(TextView pill, Context ctx, int iconRes, float density, boolean light) {
        float radius = 32 * density;
        pill.setBackground(glassPill(pill, radius, density, light));
        pill.setTextColor(pillText(light));
        pill.setCompoundDrawableTintList(null);
        pill.setCompoundDrawablesRelative(disc(ctx, iconRes, density, light), null, null, null);
        pill.setCompoundDrawablePadding(Math.round(12 * density));
        pill.setPaddingRelative(Math.round(10 * density), 0, Math.round(24 * density), 0);
        pill.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f);
        pill.setTypeface(ctx.getResources().getFont(R.font.inter_semibold));
        pill.setLetterSpacing(-0.01f);
        pill.setLineSpacing(2 * density, 1f);
        pill.setSingleLine(false);
        pill.setMaxLines(2);
        pill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.setMaxWidth(Math.round(320 * density));
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) pill.getLayoutParams();
        lp.height = Math.round(64 * density);
        lp.setMarginStart(Math.round(12 * density));
        pill.setLayoutParams(lp);
    }
}
