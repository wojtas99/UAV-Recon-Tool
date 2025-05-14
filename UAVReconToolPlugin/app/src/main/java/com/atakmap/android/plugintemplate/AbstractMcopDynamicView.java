package com.atakmap.android.plugintemplate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

@SuppressLint("WrongCall")
public abstract class AbstractMcopDynamicView extends View {

    int availableHeight = 0;
    int availableWidth = 0;
    int height = 0;

    public AbstractMcopDynamicView(Context context) {
        super(context);
    }


    public AbstractMcopDynamicView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AbstractMcopDynamicView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public abstract void onDraw(Canvas canvas);

    public void setHeight(int height) {
        this.height = height;
        this.measure(availableWidth, height);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (height != 0) {
            availableHeight = height;
        }
        setMeasuredDimension(availableWidth, availableHeight);
    }
}