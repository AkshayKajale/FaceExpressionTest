package com.akshay.faceexpressiontest.util;

import android.util.Size;
import java.util.Comparator;

public class CompareSizesByViewAspectRatio implements Comparator{

    private int viewHeight;
    private int viewWidth;

    public CompareSizesByViewAspectRatio(int viewHeight, int viewWidth) {
        this.viewHeight = viewHeight;
        this.viewWidth = viewWidth;
    }

    public int getViewWidth() {
        return viewWidth;
    }


    public int getViewHeight() {
        return viewHeight;
    }


    public int compare(Size lhs, Size rhs)
    {
        float lhsaspect = lhs.getHeight()/lhs.getWidth();
        float rhsaspect = rhs.getHeight()/rhs.getWidth();
        float requiredAspectRatio = viewHeight/viewWidth;
        float lhsDistancefrom1 = Math.abs(lhsaspect-requiredAspectRatio);
        float rhsDistancefrom1 = Math.abs(rhsaspect-requiredAspectRatio);

        if(lhsDistancefrom1<rhsDistancefrom1)
        {
            return 1;
        }
        return -1;
    }


    public int compare(Object var1, Object var2) {
        return this.compare((Size)var1, (Size)var2);
    }
}

