package com.diegoca.gpsmocker

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.privacy_policy)
        val document = assets.open("privacy/privacy.html").bufferedReader().use { it.readText() }
            .replace(Regex("<style>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<title>.*?</title>"), "")
        val padding = (24 * resources.displayMetrics.density).toInt()
        setContentView(ScrollView(this).apply { addView(TextView(this@PrivacyActivity).apply {
            text = Html.fromHtml(document, Html.FROM_HTML_MODE_LEGACY)
            textSize = 16f; setPadding(padding,padding,padding,padding)
            movementMethod = LinkMovementMethod.getInstance()
        }) })
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
