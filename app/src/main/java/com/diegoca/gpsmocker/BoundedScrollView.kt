package com.diegoca.gpsmocker

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** Caps the card without feeding a child's measured height back into its parent. */
class BoundedScrollView @JvmOverloads constructor(context: Context, attributes: AttributeSet? = null) : ScrollView(context, attributes) {
    var maximumHeight = Int.MAX_VALUE
        set(value) {
            if (field != value) { field = value; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val maximum = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) maximumHeight
            else size.coerceAtMost(maximumHeight)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maximum, View.MeasureSpec.AT_MOST))
    }
}
