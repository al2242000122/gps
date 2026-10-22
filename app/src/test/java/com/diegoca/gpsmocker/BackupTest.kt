package com.diegoca.gpsmocker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36])
class BackupTest {
    @get:org.junit.Rule val userDataRule = UserDataTestRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private fun encoded(data:UserData) = ByteArrayOutputStream().also { BackupCodec.write(it,data,12345) }.toByteArray()
    @Test fun `complete unicode snapshot roundtrips without runtime state`() {
        val data=UserData(favorites=listOf(SavedPlace("id","Café México 🌎",Coordinates.DEFAULT,123)),history=listOf(SavedPlace("hist","Reciente",Coordinates(20.0,-99.0),456)),
            options=SimulationOptions(2300.0,10f,500),dynamicColor=true,mapStyle="dark",selection=Coordinates(21.0,-98.0),selectionName="Selección",selectionAddress="Dirección ñ",route=RouteDraft(listOf(Coordinates.DEFAULT),12.0,true))
        val bytes=encoded(data);assertEquals(data,BackupCodec.read(bytes.inputStream()).data)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("cleanup_needed"))
        assertFalse(bytes.toString(Charsets.UTF_8).contains("notifications_prompted"))
    }
    @Test fun `invalid file future version and oversize input never replace existing state`() {
        val repo=UserDataRepository.get(context);val before=repo.update { it.copy(selectionName="Conservar") }
        val valid=encoded(before).toString(Charsets.UTF_8)
        val invalid=listOf("{",valid.replace("\"schemaVersion\":2","\"schemaVersion\":999"),valid.replace("19.432608","999"),valid+"trailing","x".repeat(BackupCodec.MAX_BYTES+1))
        invalid.forEach { input -> assertTrue(runCatching { val b=BackupCodec.read(input.byteInputStream());repo.update { b.data } }.isFailure);assertEquals(before,repo.snapshot()) }
    }
    @Test fun `migration is persisted and old preference files no longer overwrite user data`() {
        context.getSharedPreferences("preferences",0).edit().putString("route","[[19.0,-99.0]]").putString("speed","8").putBoolean("dynamic_color",true).commit()
        context.getSharedPreferences("simulation",0).edit().putString("selection_name","Antes").commit()
        val data=UserDataRepository.get(context).snapshot()
        assertEquals(1,data.route.points.size);assertEquals("Antes",data.selectionName);assertTrue(data.dynamicColor)
        assertTrue(File(context.filesDir,"userdata/user-data.json").isFile)
        UserDataRepository.resetForTests()
        context.getSharedPreferences("simulation",0).edit().putString("selection_name","XML viejo").commit()
        assertEquals(data,UserDataRepository.get(context).snapshot())
    }
    @Test fun `write failure keeps durable and visible state unchanged`() {
        var fail=false; val file=File(context.filesDir,"fault-test.json")
        val repo=UserDataRepository(context,file) { if (fail) throw IOException("disk full") }
        val before=repo.update { it.copy(selectionName="Antes") };val bytes=file.readBytes()
        fail=true
        assertTrue(runCatching { repo.update { it.copy(selectionName="Después") } }.isFailure)
        assertEquals(before,repo.snapshot());assertArrayEquals(bytes,file.readBytes());repo.close()
    }
    @Test fun `restore replaces all user data and does not import operational state`() {
        val repo=UserDataRepository.get(context);val store=SimulationStore(context)
        store.cleanupNeeded=true;store.writeStatus("Pendiente",true)
        repo.update { UserData(selectionName="Restaurado") }
        assertTrue(store.cleanupNeeded);assertTrue(store.hasError);assertEquals("Restaurado",store.selectionName)
    }
    @Test fun `external legacy rescue is validated by production codec`() {
        val path=System.getProperty("gps.backup.path");assumeTrue(!path.isNullOrBlank())
        val backup=File(path!!).inputStream().use(BackupCodec::read)
        assertEquals(backup.data,BackupCodec.read(encoded(backup.data).inputStream()).data)
    }

    @Test fun `corruption is preserved and explicit recovery archives original bytes`() {
        val file=File(context.filesDir,"userdata/user-data.json");file.parentFile!!.mkdirs();file.writeText("damaged")
        assertTrue(runCatching { UserDataRepository.get(context) }.isFailure)
        assertEquals("damaged",file.readText())
        UserDataRepository.recover(context,UserData(selectionName="Recuperado"))
        assertEquals("Recuperado",UserDataRepository.get(context).snapshot().selectionName)
        assertEquals("damaged",File(context.filesDir,"recovery").listFiles()!!.single().readText())
    }
    @Test fun `recovery blocks pending GPS cleanup without loading damaged userdata`() {
        val file=File(context.filesDir,"userdata/user-data.json");file.parentFile!!.mkdirs();file.writeText("damaged")
        SimulationStore(context).cleanupNeeded=true
        assertTrue(runCatching { UserDataRepository.recover(context,UserData()) }.isFailure)
        assertEquals("damaged",file.readText())
    }

    @Test fun `restore transaction blocks concurrent normal writes`() {
        val repo=UserDataRepository.get(context)
        BackupGuard.restoring.set(true)
        try {
            assertTrue(runCatching { repo.update { it.copy(selectionName="Late selection") } }.isFailure)
            repo.restore(UserData(selectionName="Restored"))
            assertEquals("Restored",repo.snapshot().selectionName)
        } finally { BackupGuard.restoring.set(false) }
    }
}
