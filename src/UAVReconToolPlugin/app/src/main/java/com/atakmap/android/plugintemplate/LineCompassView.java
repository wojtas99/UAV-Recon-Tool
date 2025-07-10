package com.atakmap.android.plugintemplate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

public class LineCompassView extends AbstractMcopDynamicView {

    int fontMedium = 30;
    int fontSmall = 15;

    public double getDeg() {
        return deg;
    }

    double deg = 0.0;
    Path uArrowPath = null;
    Path lArrowPath = null;
    Paint paint = null;
    String[] directions = {"N", "E", "S", "W", "N"};
    int[] directionsOffset = {20, 20, 20, 25, 20};
    String[] directionsD = {"NE", "SE", "SW", "NW"};
    int size = 30; /// should be even

    public LineCompassView(Context context) {
        super(context);
        init();
    }

    public LineCompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineCompassView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void init() {
        uArrowPath = new Path();
        uArrowPath.moveTo(0, 10);
        uArrowPath.lineTo(-5, 0);
        uArrowPath.lineTo(5, 0);
        uArrowPath.close();

        lArrowPath = new Path();
        lArrowPath.moveTo(0, 0);
        lArrowPath.lineTo(5, 10);
        lArrowPath.lineTo(-5, 10);
        lArrowPath.close();

        paint = new Paint();
        paint.setAntiAlias(true);

        for (int i = 0; i < directions.length; i++) {
            directionsOffset[i] = (int) Math.round(paint.measureText(directions[i]) / 2.0);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        paint.setColor(Color.BLACK);
        int barwidth = 3;
        int bheight = availableHeight;
        canvas.drawRect(0, 0, availableWidth, availableHeight, paint);
        int width = availableWidth;
        int middleMedium = availableHeight / 2 + (int) (fontMedium / 2.5);
        int middle = availableHeight / 2 + (int) (fontSmall / 2.5);

        for (int k = (int) Math.floor(deg - size / 2); k <= Math.ceil(deg + size / 2); k++) {

            int i = (k + 360) % 360;
            paint.setColor(Color.WHITE);
            paint.setTextSize(fontSmall);
            if (i % 90 == 0) {
                int j = i / 90;
                if (j == 0 || j == 4) paint.setColor(Color.RED);
                paint.setTextSize(fontMedium);
                canvas.drawRect(-barwidth / 2, 0, barwidth / 2, bheight - middleMedium - 5, paint);
                canvas.drawText(directions[j], getOffset(directions[j]), (availableHeight / 2 + (int) (fontMedium / 2.5)), paint);
                canvas.drawRect(-barwidth / 2, bheight - middleMedium + fontMedium, barwidth / 2, bheight, paint);
            } else if (i % 45 == 0) {
                int j = ((i / 45) - 1) / 2;
                canvas.drawRect(-barwidth / 2, 0, barwidth / 2, bheight - middle - 5, paint);
                canvas.drawText(directionsD[j], getOffset(directionsD[j]), middle, paint);
                canvas.drawRect(-barwidth / 2, bheight - middle + fontSmall, barwidth / 2, bheight, paint);
            } else if (i % 10 == 0) {
                canvas.drawRect(-barwidth / 2, 0, barwidth / 2, bheight - middle - 5, paint);
                canvas.drawText(Integer.toString(i), getOffset(i + ""), (availableHeight / 2 + (int) (fontSmall / 2.5)), paint);
                canvas.drawRect(-barwidth / 2, bheight - middle + fontSmall, barwidth / 2, bheight, paint);
            } else if (i % 5 == 0) canvas.drawRect(-barwidth / 2, 0, barwidth / 2, bheight, paint);
            if (i == (int) deg) {


                paint.setColor(Color.RED);
                paint.setAlpha(200);
                canvas.drawRect(-barwidth / 2, 10, barwidth / 2, availableHeight - 10, paint);


                paint.setColor(Color.WHITE);
                paint.setAlpha(200);
                canvas.drawPath(uArrowPath, paint);
                canvas.translate(0, availableHeight - 10);
                canvas.drawPath(lArrowPath, paint);
                canvas.translate(0, -(availableHeight - 10));

            }

            canvas.translate((float) width / (float) size * 1.0f, 0);
        }
    }

    private int getOffset(String string) {
        return -(int) Math.round(paint.measureText(string) / 2.0);
    }

    public void setAngle(double angle) {
        this.deg = angle;
        invalidate();
    }

}