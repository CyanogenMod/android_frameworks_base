/*
* Copyright (C) 2014 The OmniROM Project
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
package com.android.internal.util.paranoid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.renderscript.Allocation;
import android.renderscript.Allocation.MipmapControl;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ColorUtils {

    public static Drawable getGradientDrawable(boolean isNav, int color) {
        int color2 = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color));
        GradientDrawable drawable = new GradientDrawable(
                       (isNav ? Orientation.BOTTOM_TOP : Orientation.TOP_BOTTOM),
                                     new int[]{color, color2});
        if (isBrightColor(color)) {
            color = isNav ? Color.BLACK : Color.WHITE;
        } else {
            color = isNav ? Color.WHITE : Color.BLACK;
        }
        drawable.setDither(true);
        drawable.setStroke(1, color);
        return drawable;
    }

    public static int darken(final int color, float fraction) {
        return blendColors(Color.BLACK, color, fraction);
    }

    public static int lighten(final int color, float fraction) {
        return blendColors(Color.WHITE, color, fraction);
    }

    public static int blendColors(int color1, int color2, float ratio) {
        final float inverseRatio = 1f - ratio;
        float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRatio);
        float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRatio);
        float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRatio);
        return Color.rgb((int) r, (int) g, (int) b);
    }

    public static int opposeColor(int ColorToInvert) {
        if (ColorToInvert == -3) {
            return ColorToInvert;
        }
        int RGBMAX = 255;
        float[] hsv = new float[3];
        float H;
        Color.RGBToHSV(Color.red(ColorToInvert),
              RGBMAX - Color.green(ColorToInvert),
              Color.blue(ColorToInvert), hsv);
        H = (float) (hsv[0] + 0.5);
        if (H > 1) H -= 1;
        return Color.HSVToColor(hsv);
    }

    public static int changeColorTransparency(int colorToChange, int reduce) {
        if (colorToChange == -3) {
            return colorToChange;
        }
        int nots = 255 / 100;
        int red = Color.red(colorToChange);
        int blue = Color.blue(colorToChange);
        int green = Color.green(colorToChange);
        int alpha = nots * reduce;
        return Color.argb(alpha, red, green, blue);
    }

    public static boolean isColorTransparency(int color) {
        if (color == -3) {
            return false;
        }
        int nots = 255 / 100;
        int alpha = Color.alpha(color) / nots;
        return (alpha < 100);
    }

    public static boolean isBrightColor(int color) {
        if (color == -3) {
            return false;
        } else if (color == Color.TRANSPARENT) {
            return false;
        } else if (color == Color.WHITE) {
            return true;
        }
        int[] rgb = { Color.red(color), Color.green(color), Color.blue(color) };
        int brightness = (int) Math.sqrt(rgb[0] * rgb[0] * .241 + rgb[1]
            * rgb[1] * .691 + rgb[2] * rgb[2] * .068);
        if (brightness >= 170) {
            return true;
        }
        return false;
    }

    public static int getMainColorFromBitmap(Bitmap bitmap, int x, int y) {
        if (bitmap == null) {
            return -3;
        }
        int pixel = bitmap.getPixel(x, y);
        int red = Color.red(pixel);
        int blue = Color.blue(pixel);
        int green = Color.green(pixel);
        int alpha = Color.alpha(pixel);
        return Color.argb(alpha, red, green, blue);
    }

    public static boolean getIconWhiteBlackTransparent(Drawable drawable) {
        int color = getDominantColor(drawable);
        if (color == Color.WHITE) {
            return true;
        } else if (color == Color.BLACK) {
            return true;
        } else if (color == Color.TRANSPARENT) {
            return true;
        } else if (color == -3) {
            return true;
        }
        return false;
    }

    public static int getIconColorFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return -3;
        }
        if (drawable.getConstantState() == null) {
            return -3;
        }
        Drawable copyDrawable = drawable.getConstantState().newDrawable();
        if (copyDrawable == null) {
            return -3;
        }
        if (copyDrawable instanceof ColorDrawable) {
            return ((ColorDrawable) drawable).getColor();
        }
        Bitmap bitmap = drawableToBitmap(copyDrawable);
        if (bitmap == null) {
            return -3;
        }
        return getDominantColor(bitmap);
    }

    public static int getAverageColor(Drawable image) {
        int hSamples = 20;
        int vSamples = 20;
        int sampleSize = hSamples * vSamples;
        float[] sampleTotals = {0, 0, 0};
        float minimumSaturation = 0.1f;
        int minimumAlpha = 200;
        Bitmap b = drawableToBitmap(image);
        if (b == null) {
            return -3;
        }
        int width = b.getWidth();
        int height = b.getHeight();
        float[] hsv = new float[3];
        int sample;
        for (int i = 0; i < width; i += (width / hSamples)) {
             for (int j = 0; j < height; j += (height / vSamples)) {
                  sample = b.getPixel(i, j);
                  Color.colorToHSV(sample, hsv);
                  if ((Color.alpha(sample) > minimumAlpha) && (hsv[1] >= minimumSaturation)) {
                      sampleTotals[0] += hsv[0];
                      sampleTotals[1] += hsv[1];
                      sampleTotals[2] += hsv[2];
                  }
              }
        }

        float[] average = new float[3];
        average[0] = sampleTotals[0] / sampleSize;
        average[1] = sampleTotals[1] / sampleSize;
        average[2] = 0.8f;

        return Color.HSVToColor(average);
    }

    public static int getDominantExampleColor(Bitmap bitmap) {
        if (bitmap == null) {
            return -3;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int pixels[] = new int[size];

        Bitmap bitmap2 = bitmap.copy(Config.ARGB_4444, false);

        bitmap2.getPixels(pixels, 0, width, 0, 0, width, height);

        final List<HashMap<Integer, Integer>> colorMap = new ArrayList<HashMap<Integer, Integer>>();
        colorMap.add(new HashMap<Integer, Integer>());
        colorMap.add(new HashMap<Integer, Integer>());
        colorMap.add(new HashMap<Integer, Integer>());

        int color = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        Integer rC, gC, bC;
        for (int i = 0; i < pixels.length; i++) {
             color = pixels[i];

             r = Color.red(color);
             g = Color.green(color);
             b = Color.blue(color);

             rC = colorMap.get(0).get(r);
             if (rC == null) {
                 rC = 0;
             }
             colorMap.get(0).put(r, rC++);

             gC = colorMap.get(1).get(g);
             if (gC == null) {
                 gC = 0;
             }
             colorMap.get(1).put(g, gC++);

             bC = colorMap.get(2).get(b);
             if (bC == null) {
                 bC = 0;
             }
             colorMap.get(2).put(b, bC++);
        }

        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
             int max = 0;
             int val = 0;
             for (Map.Entry<Integer, Integer> entry : colorMap.get(i).entrySet()) {
                  if (entry.getValue() > max) {
                      max = entry.getValue();
                      val = entry.getKey();
                  }
             }
             rgb[i] = val;
        }

        int dominantColor = Color.rgb(rgb[0], rgb[1], rgb[2]);
        return dominantColor;
    }

    public static int getDominantColor(Drawable drawable) {
        if (drawable == null) {
            return -3;
        }
        Bitmap bitmap = drawableToBitmap(drawable);
        if (bitmap == null) {
            return -3;
        }
        return getDominantColor(bitmap, true);
    }

    public static int getDominantColor(Bitmap source) {
        return getDominantColor(source, true);
    }

    public static int getDominantColor(Bitmap source, boolean applyThreshold) {
        if (source == null) {
            return -3;
        }
        int[] colorBins = new int[36];
        int maxBin = -1;
        float[] sumHue = new float[36];
        float[] sumSat = new float[36];
        float[] sumVal = new float[36];
        float[] hsv = new float[3];

        int height = source.getHeight();
        int width = source.getWidth();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int row = 0; row < height; row++) {
             for (int col = 0; col < width; col++) {
                  int c = pixels[col + row * width];
                  if (Color.alpha(c) < 128) {
                      continue;
                  }
                  Color.colorToHSV(c, hsv);

                  if (applyThreshold && (hsv[1] <= 0.35f || hsv[2] <= 0.35f)) {
                      continue;
                  }

                  int bin = (int) Math.floor(hsv[0] / 10.0f);
                  sumHue[bin] = sumHue[bin] + hsv[0];
                  sumSat[bin] = sumSat[bin] + hsv[1];
                  sumVal[bin] = sumVal[bin] + hsv[2];
                  colorBins[bin]++;
                  if (maxBin < 0 || colorBins[bin] > colorBins[maxBin]) {
                      maxBin = bin;
                  }
             }
        }

        if (maxBin < 0) {
            return -3;
        }
        hsv[0] = sumHue[maxBin]/colorBins[maxBin];
        hsv[1] = sumSat[maxBin]/colorBins[maxBin];
        hsv[2] = sumVal[maxBin]/colorBins[maxBin];
        return Color.HSVToColor(hsv);
    }

    public static int getMainColorFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return -3;
        }
        if (drawable.getConstantState() == null) {
            return -3;
        }
        Drawable copyDrawable = drawable.getConstantState().newDrawable();
        if (copyDrawable == null) {
            return -3;
        }
        if (copyDrawable instanceof ColorDrawable) {
            return ((ColorDrawable) drawable).getColor();
        }
        Bitmap bitmap = drawableToBitmap(copyDrawable);
        if (bitmap == null) {
            return -3;
        }
        if (bitmap.getHeight() > 5) {
            int pixel = bitmap.getPixel(0, 5);
            int red = Color.red(pixel);
            int blue = Color.blue(pixel);
            int green = Color.green(pixel);
            int alpha = Color.alpha(pixel);
            return Color.argb(alpha, red, green, blue);
        }
        return -3;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = null;
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width > 0 && height > 0) {
            bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return bitmap;
    }

    public static Bitmap blurBitmap(Context context, Bitmap bmp, int radius) {
        Bitmap out = Bitmap.createBitmap(bmp);
        RenderScript rs = RenderScript.create(context);

        Allocation input = Allocation.createFromBitmap(
                rs, bmp, MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        Allocation output = Allocation.createTyped(rs, input.getType());

        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        script.setInput(input);
        script.setRadius(radius);
        script.forEach(output);

        output.copyTo(out);

        rs.destroy();
        return out;
    }
}
