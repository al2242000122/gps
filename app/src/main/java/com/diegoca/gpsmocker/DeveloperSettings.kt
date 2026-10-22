package com.diegoca.gpsmocker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings

object DeveloperSettings {
    const val PREFERENCE_KEY = "mock_location_app"
    const val FRAGMENT_KEY = ":settings:fragment_args_key"
    const val FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

    fun mockLocationIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(FRAGMENT_KEY, PREFERENCE_KEY)
        .putExtra(FRAGMENT_ARGUMENTS, Bundle().apply { putString(FRAGMENT_KEY, PREFERENCE_KEY) })
}
