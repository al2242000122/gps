package com.diegoca.gpsmocker

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import java.io.File

/** Exports app-owned vector artwork; store screenshots still need a real rendered map. */
object StoreAssets {
    fun export(context: Context) {
        val directory=File("build/outputs/store").apply { mkdirs() }
        fun save(name:String,bitmap:Bitmap) {
            File(directory,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        val brand=ContextCompat.getColor(context,R.color.brand)
        val icon=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        Canvas(icon).apply iconCanvas@ {
            drawColor(brand)
            ContextCompat.getDrawable(context,R.drawable.ic_launcher_foreground)!!.apply { setBounds(0,0,512,512);draw(this@iconCanvas) }
        }
        save("icon-512.png",icon)
        val graphic=Bitmap.createBitmap(1024,500,Bitmap.Config.ARGB_8888)
        Canvas(graphic).apply graphicCanvas@ {
            drawColor(Color.rgb(247,249,252))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=brand }
            drawCircle(824f,250f,132f,paint)
            ContextCompat.getDrawable(context,R.drawable.ic_launcher_foreground)!!.apply { setBounds(644,70,1004,430);draw(this@graphicCanvas) }
            paint.color=Color.rgb(23,32,51);paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=68f
            drawText("GPS Mocker",64f,212f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=29f
            drawText("Elige un punto.",68f,277f,paint)
            drawText("Prepara tu recorrido.",68f,322f,paint)
        }
        save("feature-1024x500.png",graphic)
    }
}
