package com.diegoca.gpsmocker

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Await real IO and its UI acknowledgement, rather than assuming clicks persist synchronously. */
fun settleDataWork() {
    repeat(2000) {
        shadowOf(Looper.getMainLooper()).idle()
        if (UserDataWork.pending == 0) {
            shadowOf(Looper.getMainLooper()).idle()
            if (UserDataWork.pending == 0) return
        }
        Thread.sleep(5)
    }
    error("User data work did not finish")
}
