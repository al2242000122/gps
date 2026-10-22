package com.diegoca.gpsmocker

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.*
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private object AdsRuntime {
    private val mutex = Mutex()
    private var ready = false
    suspend fun initialize(context: Context) = mutex.withLock {
        if (!ready) withContext(Dispatchers.IO) {
            MobileAds.initialize(context.applicationContext, InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build())
            ready = true
        }
    }
}

/** No coordinates, device location, WebView registration or simulation callbacks enter this class. */
class AdsController(private val activity: AppCompatActivity, private val container: FrameLayout,
                    private val onSpaceChanged: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val enabled = BuildConfig.ADS_ENABLED
    private val consent by lazy { UserMessagingPlatform.getConsentInformation(activity) }
    private var consentUpdated = false
    private var fits = false
    private var requested = false
    private var closed = false
    private var adView: AdView? = null
    val privacyOptionsRequired: Boolean get() = enabled && consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    fun start() {
        if (!enabled) return
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                consentUpdated = true; maybeLoad()
            }
        }, { consentUpdated = true; maybeLoad() })
    }
    fun spaceAvailable(value: Boolean) {
        if (fits == value) return
        fits = value
        if (!fits) { adView?.destroy(); adView = null; requested = false; container.removeAllViews(); container.visibility = View.GONE }
        else maybeLoad()
    }
    fun privacyOptions() {
        if (privacyOptionsRequired) UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            adView?.destroy(); adView = null; requested = false; container.removeAllViews(); container.visibility = View.GONE
            maybeLoad(); onSpaceChanged()
        }
    }
    private fun maybeLoad() {
        if (!enabled || !consentUpdated || !fits || requested || closed || !consent.canRequestAds()) return
        requested = true
        scope.launch {
            try {
                AdsRuntime.initialize(activity)
                if (closed || !fits || !consent.canRequestAds()) { requested = false; return@launch }
                val view = AdView(activity); adView = view
                container.addView(view, FrameLayout.LayoutParams(-1,-1)); container.visibility = View.VISIBLE; onSpaceChanged()
                view.loadAd(BannerAdRequest.Builder(BuildConfig.ADMOB_BANNER_ID, AdSize.BANNER).build(), object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        activity.runOnUiThread { if (closed || !fits || adView !== view) view.destroy() }
                    }
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        activity.runOnUiThread {
                            if (!closed && adView === view) { view.destroy(); adView = null; container.removeAllViews(); container.visibility = View.GONE; onSpaceChanged() }
                        }
                    }
                })
            } catch (_: Exception) { container.visibility = View.GONE; onSpaceChanged() }
        }
    }
    fun close() { closed = true; scope.cancel(); adView?.destroy(); container.removeAllViews() }
}
