package com.diegoca.gpsmocker

import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import java.io.File

class UserDataTestRule : ExternalResource() {
    override fun before() {
        settleDataWork()
        UserDataRepository.resetForTests()
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir,"userdata").deleteRecursively()
        listOf("preferences","places","simulation").forEach { context.getSharedPreferences(it,0).edit().clear().commit() }
        BackupGuard.restoring.set(false)
    }
    override fun after() { settleDataWork(); UserDataWork.resetForTests(); UserDataRepository.resetForTests(); BackupGuard.restoring.set(false) }
}
