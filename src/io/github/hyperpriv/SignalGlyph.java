package io.github.hyperpriv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

final class SignalGlyph extends Drawable {
    enum Style { SPLIT_THIN, SPLIT_THICK, DUO }

    private static final Typeface PERCENT_FONT = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics percentMetrics = new Paint.FontMetrics();
    private final RectF arc = new RectF();
    private final Path bolt = new Path();
    private Style style;
    private boolean charging;
    private String percentage;
    private int first, second, wifi, battery, color, size;
    private float gap, offset;
    private ColorFilter filter;
    int originalImageAlpha;
    int originalViewWidth = Integer.MIN_VALUE;
    int appliedViewWidth;

    SignalGlyph() {
        percentPaint.setTypeface(PERCENT_FONT);
        percentPaint.setTextAlign(Paint.Align.CENTER);
    }

    void update(Style style, int first, int second, int wifi, int battery, boolean charging, int color, ColorFilter filter,
                int width, int height, int size, float gap, float offset) {
        if (this.style == style && this.first == first && this.second == second && this.wifi == wifi
                && this.battery == battery && this.charging == charging && this.color == color
                && this.filter == filter && this.size == size
                && this.gap == gap && this.offset == offset
                && getBounds().width() == width && getBounds().height() == height) return;
        this.style = style;
        this.first = first;
        this.second = second;
        this.wifi = wifi;
        if (style == Style.DUO && (percentage == null || this.battery != battery)) percentage = Integer.toString(battery);
        this.battery = battery;
        this.charging = charging;
        this.color = color;
        this.filter = filter;
        this.size = size;
        this.gap = gap;
        this.offset = offset;
        setBounds(0, 0, width, height);
        invalidateSelf();
    }

    static int resourceLevel(String name, boolean wifi) {
        String prefix = wifi ? "stat_sys_wifi_signal_" : "stat_sys_signal_";
        if (!name.startsWith(prefix) || name.length() <= prefix.length()) return -1;
        int value = name.charAt(prefix.length()) - '0';
        if (value < 0 || value > (wifi ? 3 : 4)) return -1;
        return name.length() == prefix.length() + 1 || name.charAt(prefix.length() + 1) == '_' ? value : -1;
    }

    @Override public void draw(Canvas canvas) {
        if (style == null) return;
        int width = getBounds().width(), height = getBounds().height();
        float x = width / 2f, y = height / 2f;
        float w = Math.min(width * .96f, width * size / 100f);
        float heightShare = style == Style.DUO ? .90f : .66f;
        float h = Math.min(height * .90f, height * heightShare * size / 100f);
        if (w < 4 || h < 4) return;
        y += Math.max(-(height - h) / 2, Math.min((height - h) / 2, offset));
        paint.setColorFilter(filter);
        if (style == Style.DUO) {
            drawDuo(canvas, x, y, w, h);
        } else {
            drawSplit(canvas, x, y, w, h);
        }
    }

    private void drawSplit(Canvas canvas, float x, float y, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        float step = width / 4;
        float barWidth = step * (style == Style.SPLIT_THICK ? .66f : .46f);
        float radius = Math.min(barWidth / 3, height / 12);
        float bottom = y + height / 2;
        float split = bottom - height * .30f;
        float halfGap = Math.max(.35f, Math.min(gap / 2, height * .087f));
        for (int i = 0; i < 4; i++) {
            float left = x - width / 2 + (i + .5f) * step - barWidth / 2;
            float top = bottom - height * (.52f + i * .16f);
            ink(i < first);
            canvas.drawRoundRect(left, top, left + barWidth, split - halfGap, radius, radius, paint);
            ink(i < second);
            canvas.drawRoundRect(left, split + halfGap, left + barWidth, bottom, radius, radius, paint);
        }
    }

    private void drawDuo(Canvas canvas, float x, float y, float width, float height) {
        float diameter = Math.min(width, height);
        float ringY = y + diameter * .009f;
        float radius = diameter * .407f;
        float halfGap = charging ? 19 : second >= 0 ? 4.5f : 0;
        int ringColor = color;
        if (charging) {
            int luminance = Color.red(color) + 2 * Color.green(color) + Color.blue(color);
            int green = luminance < 510 ? 0x168842 : 0x34c759;
            ringColor = (color & 0xff000000) | green;
            paint.setColorFilter(null);
        }
        arc.set(x - radius, ringY - radius, x + radius, ringY + radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(diameter * .078f);
        paint.setStrokeCap(Paint.Cap.BUTT);
        ink(ringColor, false);
        drawDuoArc(canvas, 150, 390, halfGap);
        paint.setStyle(Paint.Style.FILL);
        drawDuoCap(canvas, x, ringY, radius, 150);
        drawDuoCap(canvas, x, ringY, radius, 390);

        float start = second < 0 ? 150 : 270 - first * 30;
        float end = second < 0 ? 150 + first * 60 : 270 + second * 30;
        ink(ringColor, true);
        paint.setStyle(Paint.Style.STROKE);
        drawDuoArc(canvas, start, end, halfGap);
        paint.setStyle(Paint.Style.FILL);
        if (start == 150 && end > start) drawDuoCap(canvas, x, ringY, radius, 150);
        if (end == 390) drawDuoCap(canvas, x, ringY, radius, 390);
        if (charging) drawDuoBolt(canvas, x, ringY - radius, diameter);

        paint.setColorFilter(filter);
        if (battery == 100) {
            ink(true);
            canvas.drawCircle(x, ringY, diameter * .188f, paint);
        } else {
            drawDuoPercent(canvas, x, ringY + diameter * .018f, diameter);
        }

        for (int i = 0; i < 4; i++) {
            ink(i < wifi);
            canvas.drawCircle(x + (i - 1.5f) * diameter * .152f,
                    ringY + diameter * .370f, diameter * .049f, paint);
        }
    }

    private void drawDuoArc(Canvas canvas, float start, float end, float halfGap) {
        if (start >= end) return;
        if (halfGap == 0) {
            canvas.drawArc(arc, start, end - start, false, paint);
            return;
        }
        float leftEnd = Math.min(end, 270 - halfGap);
        float rightStart = Math.max(start, 270 + halfGap);
        if (start < leftEnd) canvas.drawArc(arc, start, leftEnd - start, false, paint);
        if (rightStart < end) canvas.drawArc(arc, rightStart, end - rightStart, false, paint);
    }

    private void drawDuoCap(Canvas canvas, float x, float y, float radius, float angle) {
        double radians = Math.toRadians(angle);
        canvas.drawCircle(x + radius * (float) Math.cos(radians),
                y + radius * (float) Math.sin(radians), paint.getStrokeWidth() / 2, paint);
    }

    private void drawDuoBolt(Canvas canvas, float x, float y, float diameter) {
        bolt.reset();
        bolt.moveTo(x + diameter * .047f, y - diameter * .099f);
        bolt.lineTo(x - diameter * .090f, y + diameter * .012f);
        bolt.lineTo(x - diameter * .012f, y + diameter * .012f);
        bolt.lineTo(x - diameter * .046f, y + diameter * .119f);
        bolt.lineTo(x + diameter * .095f, y - diameter * .021f);
        bolt.lineTo(x + diameter * .016f, y - diameter * .021f);
        bolt.close();
        canvas.drawPath(bolt, paint);
    }

    private void drawDuoPercent(Canvas canvas, float x, float y, float diameter) {
        percentPaint.setColor(color);
        percentPaint.setColorFilter(filter);
        float size = diameter * .44f;
        percentPaint.setTextSize(size);
        float maxTextWidth = percentPaint.measureText("88");
        float available = diameter * .58f;
        if (maxTextWidth > available) percentPaint.setTextSize(size * available / maxTextWidth);
        percentPaint.getFontMetrics(percentMetrics);
        canvas.drawText(percentage, x, y - (percentMetrics.ascent + percentMetrics.descent) / 2, percentPaint);
    }

    private void ink(boolean active) { ink(color, active); }

    private void ink(int tone, boolean active) {
        int opacity = Color.alpha(tone);
        paint.setColor(tone);
        paint.setAlpha(active ? opacity : Math.round(opacity * .30f));
    }

    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
