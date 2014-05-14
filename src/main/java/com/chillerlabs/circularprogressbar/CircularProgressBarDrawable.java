/*
* Copyright (C) 2006 The Android Open Source Project
*
* Changes to accomodate repurposing for CircularProgressBarDrawable
* Copyright (C) 2014 Chiller Labs
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.chillerlabs.circularprogressbar;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

public class CircularProgressBarDrawable extends Drawable {

    private CircularProgressBarState mCircularProgressBarState;

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ColorFilter mColorFilter;   // optional, set by the caller
    private int mAlpha = 0xFF;  // modified by the caller

    private final RectF mRect = new RectF();

    private boolean mRectIsDirty;   // internal state
    private boolean mMutated;
    private Path mRingPath;
    private boolean mPathIsDirty = true;
    private float startingAngle;

    public void setThicknessRatio(float thicknessRatio) {
        mCircularProgressBarState.mThicknessRatio = thicknessRatio;
    }

    public void setUseLevel(boolean useLevel) {
        mCircularProgressBarState.mUseLevel = useLevel;
    }

    public void setStartingAngle(float startingAngle) {
        this.startingAngle = startingAngle;
    }

    public CircularProgressBarDrawable() {
        this(new CircularProgressBarState((int[]) null));

        setUseLevel(true);
        setThicknessRatio(8);
        mRectIsDirty = true;
        invalidateSelf();
        setStartingAngle(90);
    }

    private int modulateAlpha(int alpha) {
        int scale = mAlpha + (mAlpha >> 7);
        return alpha * scale >> 8;
    }

    /**
     * <p>Sets the colors used to draw the gradient. Each color is specified as an
     * ARGB integer and the array must contain at least 2 colors.</p>
     * <p><strong>Note</strong>: changing orientation will affect all instances
     * of a drawable loaded from a resource. It is recommended to invoke
     * {@link #mutate()} before changing the orientation.</p>
     *
     * @param colors 2 or more ARGB colors
     *
     * @see #mutate()
     * @see #setColor(int)
     */
    public void setColors(int[] colors) {
        mCircularProgressBarState.setColors(colors);
        mRectIsDirty = true;
        invalidateSelf();
    }

    public void setColorResources(Resources resources, int... colorResources) {
        int[] colors = new int[colorResources.length];

        for (int i = 0; i < colorResources.length; i++) {
            colors[i] = resources.getColor(colorResources[i]);
        }

        setColors(colors);
    }

    @Override
    public void draw(Canvas canvas) {
        if (!ensureValidRect()) {
            // nothing to draw
            return;
        }

        // remember the alpha values, in case we temporarily overwrite them
        // when we modulate them with mAlpha
        final int prevFillAlpha = mFillPaint.getAlpha();

        // compute the modulate alpha values
        final int currFillAlpha = modulateAlpha(prevFillAlpha);

        final CircularProgressBarState st = mCircularProgressBarState;

        /*  Drawing with a layer is slower than direct drawing, but it
            allows us to apply paint effects like alpha and colorfilter to
            the result of multiple separate draws. In our case, if the user
            asks for a non-opaque alpha value (via setAlpha), and we're
            stroking, then we need to apply the alpha AFTER we've drawn
            both the fill and the stroke.
        */
        /*  since we're not using a layer, apply the dither/filter to our
            individual paints
        */

        mFillPaint.setAlpha(currFillAlpha);
        mFillPaint.setColorFilter(mColorFilter);
        if (mColorFilter != null && !mCircularProgressBarState.mHasSolidColor) {
            mFillPaint.setColor(mAlpha << 24);
        }

        Path path = buildRing(st);
        canvas.drawPath(path, mFillPaint);

        mFillPaint.setAlpha(prevFillAlpha);
    }

    private Path buildRing(CircularProgressBarState st) {
        if (mRingPath != null && (!st.mUseLevel || !mPathIsDirty)) return mRingPath;
        mPathIsDirty = false;

        float sweep = st.mUseLevel ? (360.0f * getLevel() / 10000.0f) : 360f;

        RectF bounds = new RectF(mRect);

        float x = bounds.width() / 2.0f;
        float y = bounds.height() / 2.0f;

        float thickness = bounds.width() / st.mThicknessRatio;
        float innerRadius = (bounds.width() / 2) - thickness;

        RectF innerBounds = new RectF(bounds);
        innerBounds.inset(x - innerRadius, y - innerRadius);

        bounds = new RectF(innerBounds);
        bounds.inset(-thickness, -thickness);

        if (mRingPath == null) {
            mRingPath = new Path();
        } else {
            mRingPath.reset();
        }

        final Path ringPath = mRingPath;
        // arcTo treats the sweep angle mod 360, so check for that, since we
        // think 360 means draw the entire oval
        if (Math.abs(sweep) < 360) {
            ringPath.setFillType(Path.FillType.EVEN_ODD);

            double startingAngleRadians = Math.toRadians(startingAngle);
            final float startingAngleCosine = (float) Math.cos(startingAngleRadians);
            final float startingAngleSine = (float) Math.sin(startingAngleRadians) * -1;

            // inner top
            final float innerX = startingAngleCosine * innerRadius + x;
            final float innerY = startingAngleSine * innerRadius + y;
            ringPath.moveTo(innerX, innerY);

            // outer top
            final float outerX = startingAngleCosine * (innerRadius + thickness) + x;
            final float outerY = startingAngleSine * (innerRadius + thickness) + y;
            ringPath.lineTo(outerX, outerY);

            // outer arc
            ringPath.arcTo(bounds, -startingAngle, -sweep, false);

            // inner arc
            ringPath.arcTo(innerBounds, -startingAngle - sweep, sweep, false);
            ringPath.close();

        } else {
            // add the entire ovals
            ringPath.addOval(bounds, Path.Direction.CW);
            ringPath.addOval(innerBounds, Path.Direction.CCW);
        }

        return ringPath;
    }

    /**
     * <p>Changes this drawbale to use a single color instead of a gradient.</p>
     * <p><strong>Note</strong>: changing color will affect all instances
     * of a drawable loaded from a resource. It is recommended to invoke
     * {@link #mutate()} before changing the color.</p>
     *
     * @param argb The color used to fill the shape
     *
     * @see #mutate()
     * @see #setColors(int[])
     */
    public void setColor(int argb) {
        mCircularProgressBarState.setSolidColor(argb);
        mFillPaint.setColor(argb);
        invalidateSelf();
    }

    public void setColorResource(Resources resources, int color) {
        setColor(resources.getColor(color));
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mCircularProgressBarState.mChangingConfigurations;
    }

    @Override
    public void setAlpha(int alpha) {
        if (alpha != mAlpha) {
            mAlpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        if (cf != mColorFilter) {
            mColorFilter = cf;
            invalidateSelf();
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect r) {
        super.onBoundsChange(r);
        mRingPath = null;
        mPathIsDirty = true;
        mRectIsDirty = true;
    }

    @Override
    protected boolean onLevelChange(int level) {
        super.onLevelChange(level);
        mRectIsDirty = true;
        mPathIsDirty = true;
        invalidateSelf();
        return true;
    }

    /**
     * This checks mRectIsDirty, and if it is true, recomputes both our drawing
     * rectangle (mRect) and the gradient itself, since it depends on our
     * rectangle too.
     * @return true if the resulting rectangle is not empty, false otherwise
     */
    private boolean ensureValidRect() {
        if (mRectIsDirty) {
            mRectIsDirty = false;

            Rect bounds = getBounds();
            float inset = 0;

            final CircularProgressBarState st = mCircularProgressBarState;

            mRect.set(bounds.left + inset, bounds.top + inset,
                    bounds.right - inset, bounds.bottom - inset);

            final int[] colors = st.mColors;
            if (colors != null) {
                RectF r = mRect;
                float x0, y0;

                x0 = r.left + (r.right - r.left) * st.mCenterX;
                y0 = r.top + (r.bottom - r.top) * st.mCenterY;

                final SweepGradient sweepGradient = new SweepGradient(x0, y0, colors, null);
                Matrix flipMatrix = new Matrix();
                flipMatrix.setScale(1, -1);
                flipMatrix.postTranslate(0, (r.bottom - r.top));
                flipMatrix.postRotate(-startingAngle, x0, y0);
                sweepGradient.setLocalMatrix(flipMatrix);
                mFillPaint.setShader(sweepGradient);

                // If we don't have a solid color, the alpha channel must be
                // maxed out so that alpha modulation works correctly.
                if (!st.mHasSolidColor) {
                    mFillPaint.setColor(Color.BLACK);
                }
            }
        }
        return !mRect.isEmpty();
    }

    @Override
    public int getIntrinsicWidth() {
        return mCircularProgressBarState.mWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mCircularProgressBarState.mHeight;
    }

    @Override
    public ConstantState getConstantState() {
        mCircularProgressBarState.mChangingConfigurations = getChangingConfigurations();
        return mCircularProgressBarState;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mCircularProgressBarState = new CircularProgressBarState(mCircularProgressBarState);
            initializeWithState(mCircularProgressBarState);
            mMutated = true;
        }
        return this;
    }

    public static class CircularProgressBarState extends ConstantState {
        public int mChangingConfigurations;
        public int[] mColors;
        public float[] mPositions;
        public boolean mHasSolidColor;
        public int mSolidColor;
        public Rect mPadding;
        public int mWidth = -1;
        public int mHeight = -1;
        public float mThicknessRatio;
        private float mCenterX = 0.5f;
        private float mCenterY = 0.5f;
        private float mGradientRadius = 0.5f;
        private boolean mUseLevel;

        CircularProgressBarState(int[] colors) {
            setColors(colors);
        }

        public CircularProgressBarState(CircularProgressBarState state) {
            mChangingConfigurations = state.mChangingConfigurations;
            if (state.mColors != null) {
                mColors = state.mColors.clone();
            }
            if (state.mPositions != null) {
                mPositions = state.mPositions.clone();
            }
            mHasSolidColor = state.mHasSolidColor;
            mSolidColor = state.mSolidColor;

            if (state.mPadding != null) {
                mPadding = new Rect(state.mPadding);
            }
            mWidth = state.mWidth;
            mHeight = state.mHeight;
            mThicknessRatio = state.mThicknessRatio;
            mCenterX = state.mCenterX;
            mCenterY = state.mCenterY;
            mGradientRadius = state.mGradientRadius;
            mUseLevel = state.mUseLevel;
        }

        @Override
        public Drawable newDrawable() {
            return new CircularProgressBarDrawable(this);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new CircularProgressBarDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        public void setColors(int[] colors) {
            mHasSolidColor = false;
            mColors = colors;
        }

        public void setSolidColor(int argb) {
            mHasSolidColor = true;
            mSolidColor = argb;
            mColors = null;
        }
    }

    private CircularProgressBarDrawable(CircularProgressBarState state) {
        mCircularProgressBarState = state;
        initializeWithState(state);
        mRectIsDirty = true;
        mMutated = false;
    }

    private void initializeWithState(CircularProgressBarState state) {
        if (state.mHasSolidColor) {
            mFillPaint.setColor(state.mSolidColor);
        } else if (state.mColors == null) {
            // If we don't have a solid color and we don't have a gradient,
            // the app is stroking the shape, set the color to the default
            // value of state.mSolidColor
            mFillPaint.setColor(0);
        } else {
            // Otherwise, make sure the fill alpha is maxed out.
            mFillPaint.setColor(Color.BLACK);
        }

    }
}
