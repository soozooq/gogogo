package com.zcshou.gogogo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Small UI kit for GoGoGo's programmatic screens.
 *
 * Keeps spacing, typography, cards and actions consistent without forcing a large
 * XML migration. It is deliberately presentation-only.
 */
public final class GoGoUi {
    private GoGoUi() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @ColorInt
    public static int color(Context context, int resId) {
        return ContextCompat.getColor(context, resId);
    }

    public static void applyScreenBackground(View view) {
        view.setBackgroundColor(color(view.getContext(), R.color.gogogo_bg));
    }

    /** Compact context label shared by home and research navigation screens. */
    public static TextView eyebrow(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(11);
        view.setLetterSpacing(0.07f);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(color(context, R.color.gogogo_primary_dark));
        view.setPadding(dp(context, 12), dp(context, 7),
                dp(context, 12), dp(context, 7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, R.color.gogogo_primary_soft));
        bg.setCornerRadius(dp(context, 10));
        view.setBackground(bg);
        return view;
    }

    /** Legacy-friendly navigation API with a consistent default research icon. */
    public static MaterialCardView navigationTile(
            Context context,
            String title,
            String description,
            View.OnClickListener listener) {
        return navigationTile(context, R.drawable.ic_gogogo_lab, title,
                description, listener);
    }

    /** One shared vector icon language for navigation throughout the app. */
    public static MaterialCardView navigationTile(
            Context context,
            int iconRes,
            String title,
            String description,
            View.OnClickListener listener) {
        MaterialCardView tile = card(context);
        tile.setRadius(dp(context, 14));
        tile.setCardElevation(0f);
        LinearLayout row = row(context);
        row.setPadding(dp(context, 13), dp(context, 12),
                dp(context, 12), dp(context, 12));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(
                color(context, R.color.gogogo_primary_dark)));
        icon.setContentDescription(null);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setBackgroundTintList(null);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(color(context, R.color.gogogo_primary_soft));
        iconBg.setCornerRadius(dp(context, 12));
        icon.setBackground(iconBg);
        icon.setPadding(dp(context, 12), dp(context, 12),
                dp(context, 12), dp(context, 12));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                dp(context, 48), dp(context, 48));
        iconLp.setMargins(0, 0, dp(context, 12), 0);
        row.addView(icon, iconLp);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(15);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(color(context, R.color.gogogo_text));
        labels.addView(heading, matchWrap());
        labels.addView(muted(context, description), matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_gogogo_chevron);
        arrow.setImageTintList(ColorStateList.valueOf(
                color(context, R.color.gogogo_primary)));
        arrow.setContentDescription(null);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams arrowLp =
                new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20));
        arrowLp.setMargins(dp(context, 6), 0, 0, 0);
        row.addView(arrow, arrowLp);

        tile.addView(row);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(title + "，" + description);
        tile.setOnClickListener(listener);
        return tile;
    }

    public static MaterialButton backButton(
            Context context, View.OnClickListener listener) {
        MaterialButton button = textButton(context, "返回", listener);
        button.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_gogogo_back));
        button.setIconTint(ColorStateList.valueOf(
                color(context, R.color.gogogo_primary)));
        button.setIconPadding(dp(context, 8));
        return button;
    }

    /**
     * Consistent research-screen heading. Back always returns to the actual
     * caller (Hub or Sandbox), rather than naming an assumed parent screen.
     */
    public static void addLabHeader(
            LinearLayout parent, String section, String title,
            String description, View.OnClickListener onBack) {
        Context context = parent.getContext();
        parent.addView(backButton(context, onBack), matchWrap());
        parent.addView(gap(context, 4));
        parent.addView(eyebrow(context, section), matchWrap());
        parent.addView(gap(context, 8));
        parent.addView(heroTitle(context, title), matchWrap());
        parent.addView(subtitle(context, description), matchWrap());
        parent.addView(gap(context, 16));
    }

    /**
     * Selectable, softly bordered technical result. Unlike the home page,
     * research reports remain expanded by default for copying/debugging.
     */
    public static TextView reportPanel(Context context) {
        TextView panel = status(context, "读取中…");
        panel.setTextSize(13);
        panel.setLineSpacing(dp(context, 2), 1.12f);
        panel.setPadding(dp(context, 14), dp(context, 12),
                dp(context, 14), dp(context, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, R.color.gogogo_surface));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), color(context, R.color.gogogo_border));
        panel.setBackground(bg);
        return panel;
    }

    /**
     * Stacked actions replace horizontal-scrolling rows, keeping every
     * operation discoverable on 320–360dp phones with larger font sizes.
     */
    public static LinearLayout actionStack(Context context, MaterialButton... buttons) {
        LinearLayout stack = new LinearLayout(context);
        stack.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) stack.addView(gap(context, 6));
            stack.addView(buttons[i], matchWrap());
        }
        return stack;
    }

    public static TextView heroTitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(28);
        view.setTextColor(color(context, R.color.gogogo_text));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.START);
        view.setPadding(0, dp(context, 4), 0, 0);
        return view;
    }

    public static TextView subtitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(color(context, R.color.gogogo_text_muted));
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    public static TextView sectionTitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(color(context, R.color.gogogo_text));
        view.setPadding(0, 0, 0, dp(context, 10));
        return view;
    }

    public static TextView muted(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(color(context, R.color.gogogo_text_muted));
        view.setLineSpacing(0f, 1.10f);
        return view;
    }

    public static TextView status(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(color(context, R.color.gogogo_text));
        view.setTextIsSelectable(true);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    /** Semantic status colors: callers must not equate a request with success. */
    public enum StatusTone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

    public static void setStatusTone(TextView view, StatusTone tone) {
        int token;
        switch (tone) {
            case INFO: token = R.color.gogogo_info; break;
            case SUCCESS: token = R.color.gogogo_success; break;
            case WARNING: token = R.color.gogogo_warning; break;
            case ERROR: token = R.color.gogogo_danger; break;
            case NEUTRAL:
            default: token = R.color.gogogo_text;
        }
        view.setTextColor(color(view.getContext(), token));
    }

    public static MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(color(context, R.color.gogogo_surface));
        card.setRadius(dp(context, 18));
        card.setCardElevation(dp(context, 1));
        card.setStrokeColor(color(context, R.color.gogogo_border));
        card.setStrokeWidth(dp(context, 1));
        card.setUseCompatPadding(false);
        return card;
    }

    public static LinearLayout cardContent(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = dp(context, 16);
        content.setPadding(p, p, p, p);
        return content;
    }

    public static MaterialButton primaryButton(
            Context context,
            String text,
            View.OnClickListener listener) {
        MaterialButton button = baseButton(context, text, listener);
        button.setBackgroundTintList(ColorStateList.valueOf(
                color(context, R.color.gogogo_primary)));
        button.setTextColor(color(context, R.color.gogogo_on_primary));
        button.setStrokeWidth(0);
        return button;
    }

    public static MaterialButton secondaryButton(
            Context context,
            String text,
            View.OnClickListener listener) {
        MaterialButton button = baseButton(context, text, listener);
        button.setBackgroundTintList(ColorStateList.valueOf(
                color(context, R.color.gogogo_surface)));
        button.setTextColor(color(context, R.color.gogogo_primary));
        button.setStrokeWidth(dp(context, 1));
        button.setStrokeColor(ColorStateList.valueOf(
                color(context, R.color.gogogo_border)));
        return button;
    }

    public static MaterialButton dangerButton(
            Context context,
            String text,
            View.OnClickListener listener) {
        MaterialButton button = baseButton(context, text, listener);
        button.setBackgroundTintList(ColorStateList.valueOf(
                color(context, R.color.gogogo_surface)));
        button.setTextColor(color(context, R.color.gogogo_danger));
        button.setStrokeWidth(dp(context, 1));
        button.setStrokeColor(ColorStateList.valueOf(
                color(context, R.color.gogogo_border)));
        return button;
    }

    public static MaterialButton textButton(
            Context context,
            String text,
            View.OnClickListener listener) {
        MaterialButton button = baseButton(context, text, listener);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setTextColor(color(context, R.color.gogogo_primary));
        button.setStrokeWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private static MaterialButton baseButton(
            Context context,
            String text,
            View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(3);
        button.setMinWidth(0);
        button.setGravity(Gravity.CENTER);
        button.setCornerRadius(dp(context, 14));
        button.setMinHeight(dp(context, 48));
        button.setInsetTop(dp(context, 2));
        button.setInsetBottom(dp(context, 2));
        button.setOnClickListener(listener);
        return button;
    }

    public static void styleInput(EditText input) {
        Context context = input.getContext();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, R.color.gogogo_surface));
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), color(context, R.color.gogogo_border));
        input.setBackground(bg);
        input.setTextColor(color(context, R.color.gogogo_text));
        input.setHintTextColor(color(context, R.color.gogogo_text_muted));
        input.setTextSize(15);
        input.setSingleLine(true);
        int h = dp(context, 14);
        int v = dp(context, 12);
        input.setPadding(h, v, h, v);
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static Space gap(Context context, int dp) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, GoGoUi.dp(context, dp)));
        return space;
    }

    public static void addHorizontalGap(Context context, LinearLayout row, int dp) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(GoGoUi.dp(context, dp), 1));
        row.addView(space);
    }

    public static void addCard(
            LinearLayout parent,
            MaterialCardView card,
            int topMarginDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(parent.getContext(), topMarginDp), 0, 0);
        parent.addView(card, lp);
    }
}
