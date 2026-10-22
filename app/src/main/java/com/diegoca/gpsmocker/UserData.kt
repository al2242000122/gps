package com.diegoca.gpsmocker

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.io.File

/** Only user-owned data. Runtime provider ownership and permissions never belong in a backup. */
data class UserData(
    val favorites: List<SavedPlace> = emptyList(), val history: List<SavedPlace> = emptyList(),
    val options: SimulationOptions = SimulationOptions(), val dynamicColor: Boolean = false,
    val mapStyle: String = "light", val selection: Coordinates = Coordinates.DEFAULT,
    val selectionName: String = "Punto seleccionado", val selectionAddress: String = "",
    val route: RouteDraft = RouteDraft(), internal val migrationComplete: Boolean = true
)

data class BackupDocument(val exportedAt: Long, val data: UserData)

object BackupCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val VERSION = 2
    private const val FORMAT = "gps-mocker-backup"
    fun read(input: InputStream): BackupDocument {
        val bytes = boundedRead(input, MAX_BYTES)
        val json = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        val tokens = org.json.JSONTokener(json)
        val root = tokens.nextValue() as? JSONObject ?: error("Respaldo inválido.")
        require(tokens.nextClean() == '\u0000') { "El respaldo contiene datos adicionales." }
        require(root.getString("format") == FORMAT) { "Este archivo no es un respaldo de GPS Mocker." }
        require(root.int("schemaVersion") in 1..VERSION) { "Versión de respaldo no compatible. Actualiza la app." }
        return BackupDocument(root.long("exportedAt").also { require(it >= 0) }, decode(root.getJSONObject("data")))
    }
    fun write(output: OutputStream, data: UserData, exportedAt: Long = System.currentTimeMillis()) {
        val json = JSONObject().put("format", FORMAT).put("schemaVersion", VERSION)
            .put("exportedAt", exportedAt).put("data", encode(data))
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "El respaldo supera 2 MiB." }
        output.write(bytes)
    }
    fun encode(data: UserData): JSONObject {
        val value = JSONObject().put("favorites", places(data.favorites)).put("history", places(data.history))
            .put("settings", JSONObject().put("altitude", data.options.altitudeMeters).put("accuracy", data.options.accuracyMeters)
                .put("interval", data.options.intervalMillis).put("dynamicColor", data.dynamicColor).put("mapStyle", data.mapStyle))
            .put("selection", JSONObject().put("point", point(data.selection)).put("name", data.selectionName).put("address", data.selectionAddress))
            .put("route", routeJson(data.route))
        // The same strict codec validates imports, migrations and every persisted write.
        decode(value)
        return value
    }
    fun decode(value: JSONObject): UserData {
        val settings = value.getJSONObject("settings"); val selection = value.getJSONObject("selection")
        val style = settings.getString("mapStyle").also { require(it in setOf("light", "dark", "auto")) }
        val history = readPlaces(value.getJSONArray("history")).also { require(it.size <= 50) { "El historial supera 50 lugares." } }
        return UserData(readPlaces(value.getJSONArray("favorites")), history,
            SimulationOptions(settings.number("altitude"), settings.number("accuracy").toFloat(), settings.long("interval")),
            settings.bool("dynamicColor"), style, readPoint(selection.getJSONArray("point")),
            text(selection, "name", 200), text(selection, "address", 500), readRoute(value.getJSONObject("route")))
    }
    fun routeJson(route: RouteDraft) = JSONObject().put("points", points(route.points)).put("speed", route.speedKmh)
        .put("loop", route.loop).put("mode", route.mode.name).put("profile", route.profile.name)
        .put("waypoints", points(route.waypoints)).put("calculatedLoop", route.calculatedLoop ?: JSONObject.NULL)
        .put("estimatedSeconds", route.estimatedSeconds ?: JSONObject.NULL).put("source", route.source)
    fun readRoute(value: JSONObject): RouteDraft {
        val points = readPoints(value.getJSONArray("points"))
        val speed = value.number("speed").also { require(it in 0.5..300.0) }
        val mode = RouteMode.valueOf(value.optString("mode", "DIRECT"))
        val profile = RouteProfile.valueOf(value.optString("profile", "WALK"))
        val waypoints = value.optJSONArray("waypoints")?.let(::readPoints).orEmpty()
        require(waypoints.size <= 20)
        val duration = if (value.isNull("estimatedSeconds")) null else value.number("estimatedSeconds").also { require(it >= 0) }
        val closed = if (value.isNull("calculatedLoop")) null else value.bool("calculatedLoop")
        val source = value.optString("source", "").also { require(it in setOf("", "geoapify")) }
        val route = RouteDraft(points, speed, value.bool("loop"), mode, profile, waypoints, closed, duration, source)
        if (mode == RouteMode.STREET && points.isNotEmpty()) {
            require(route.ready) { "La ruta por calles necesita volver a calcularse." }
            require(points.zipWithNext { a,b -> RouteNavigator.distance(a,b) }.sum() <= 100_000)
            if (route.loop) require(points.first() == points.last()) { "Circuito por calles incompleto." }
        }
        return route
    }
    private fun places(values: List<SavedPlace>) = JSONArray().apply { values.forEach {
        put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.coordinates.latitude)
            .put("lon", it.coordinates.longitude).put("time", it.timestamp))
    } }
    private fun readPlaces(values: JSONArray): List<SavedPlace> {
        val result = (0 until values.length()).map {
            val v = values.getJSONObject(it)
            SavedPlace(text(v, "id", 128).also { require(it.isNotBlank()) }, text(v, "name", 100).also { require(it.isNotBlank()) },
                Coordinates(v.number("lat"), v.number("lon")), v.long("time").also { require(it >= 0) })
        }
        require(result.map { it.id }.distinct().size == result.size) { "El respaldo contiene identificadores duplicados." }
        return result
    }
    private fun text(value: JSONObject, key: String, max: Int) = (value.get(key) as? String
        ?: throw IllegalArgumentException("Texto inválido: $key")).also { require(it.length <= max) { "Texto demasiado largo: $key" } }
    fun point(value: Coordinates) = JSONArray().put(value.latitude).put(value.longitude)
    fun points(values: List<Coordinates>) = JSONArray().apply { values.forEach { put(point(it)) } }
    fun readPoint(value: JSONArray): Coordinates {
        require(value.length() == 2)
        val lat = (value.get(0) as? Number)?.toDouble() ?: error("Latitud inválida")
        val lon = (value.get(1) as? Number)?.toDouble() ?: error("Longitud inválida")
        return Coordinates(lat, lon)
    }
    fun readPoints(value: JSONArray): List<Coordinates> {
        require(value.length() <= RouteNavigator.MAX_POINTS) { "Demasiados puntos en la ruta." }
        return (0 until value.length()).map { readPoint(value.getJSONArray(it)) }
    }
    internal fun JSONObject.number(key: String) = (get(key) as? Number)?.toDouble()?.also { require(it.isFinite()) }
        ?: throw IllegalArgumentException("Número inválido: $key")
    private fun JSONObject.long(key: String): Long = number(key).let { require(it % 1.0 == 0.0 && it >= Long.MIN_VALUE && it < Long.MAX_VALUE.toDouble()); it.toLong() }
    private fun JSONObject.int(key: String) = long(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JSONObject.bool(key: String) = get(key) as? Boolean ?: throw IllegalArgumentException("Valor lógico inválido: $key")
    fun boundedRead(input: InputStream, maximum: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break
            require(out.size() + n <= maximum) { "El archivo supera el tamaño permitido." }; out.write(buffer, 0, n) }
        return out.toByteArray()
    }
}

/** One DataStore per application. Its immutable projection is published only after durable writes. */
class UserDataRepository internal constructor(context: Context, private val file: File = File(context.filesDir, "userdata/user-data.json"), private val beforeWrite: () -> Unit = {}) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = DataStoreFactory.create(serializer = object : Serializer<UserData> {
        override val defaultValue: UserData get() = UserData(migrationComplete = false)
        override suspend fun readFrom(input: InputStream): UserData = try { BackupCodec.read(input).data }
            catch (e: Exception) { throw CorruptionException("Los datos requieren recuperación desde un respaldo; se conserva el archivo original.", e) }
        override suspend fun writeTo(t: UserData, output: OutputStream) { beforeWrite(); BackupCodec.write(output, t, 0) }
    }, scope = scope, produceFile = { file })
    @Volatile private var projection: UserData? = null
    private val lock = Any()
    init {
        try {
            runBlocking(Dispatchers.IO) {
                // Persist migration immediately. Original XML files remain untouched for recovery.
                projection = dataStore.updateData { if (it.migrationComplete) it else legacy(context) }
            }
        } catch (e: Exception) { close(); throw e }
    }
    fun snapshot(): UserData = checkNotNull(projection)
    fun update(transform: (UserData) -> UserData): UserData = mutate(false, transform)
    fun restore(backup: UserData): UserData = mutate(true) { backup }
    private fun mutate(restoring: Boolean, transform: (UserData) -> UserData): UserData = synchronized(lock) {
        check(restoring || !BackupGuard.restoring.get()) { "Espera a que termine la restauración." }
        runBlocking(Dispatchers.IO) { dataStore.updateData { transform(it) } }.also { projection = it }
    }
    internal fun close() { runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() } }
    companion object {
        private val instances = java.util.WeakHashMap<Context, UserDataRepository>()
        @Synchronized fun get(context: Context) = instances.getOrPut(context.applicationContext) { UserDataRepository(context.applicationContext) }
        @Synchronized internal fun resetForTests() { instances.values.forEach { it.close() }; instances.clear() }
        @Synchronized fun recover(context: Context, backup: UserData) {
            check(!MockLocationService.isRunning && !SimulationStore(context).cleanupNeeded) { "Detén la simulación y restaura el GPS antes de recuperar datos." }
            BackupCodec.encode(backup)
            resetForTests()
            val file = File(context.filesDir, "userdata/user-data.json")
            file.parentFile!!.mkdirs()
            if (file.exists()) {
                val archive = File(context.filesDir, "recovery/user-data-${System.currentTimeMillis()}.json")
                archive.parentFile!!.mkdirs(); file.copyTo(archive)
            }
            val temporary = File(file.parentFile, "recovered.tmp")
            try {
                java.io.FileOutputStream(temporary).use { BackupCodec.write(it, backup, 0); it.fd.sync() }
                java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        internal fun legacy(context: Context): UserData {
            val p = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            val l = context.getSharedPreferences("places", Context.MODE_PRIVATE)
            val s = context.getSharedPreferences("simulation", Context.MODE_PRIVATE)
            val style = p.getString("map_style", "light") ?: "light"
            val route = JSONObject().put("points", JSONArray(p.getString("route", "[]"))).put("speed", p.getString("speed", "5")!!.toDouble()).put("loop", p.getBoolean("loop", false))
            val selected = if (!s.contains("latitude") && !s.contains("longitude")) Coordinates.DEFAULT
                else Coordinates.parse(s.getString("latitude", "").orEmpty(), s.getString("longitude", "").orEmpty())
                    ?: error("La selección guardada está dañada. Se conservan los archivos originales.")
            val data = JSONObject().put("favorites", JSONArray(l.getString("favorites", "[]"))).put("history", JSONArray(l.getString("history", "[]")))
                .put("settings", JSONObject().put("altitude", p.getString("altitude", "0")!!.toDouble()).put("accuracy", p.getFloat("accuracy", 3f))
                    .put("interval", p.getLong("interval", 1000)).put("dynamicColor", p.getBoolean("dynamic_color", false))
                    .put("mapStyle", if (style == "auto" && !p.getBoolean("light_map_default_v1", false)) "light" else style))
                .put("selection", JSONObject().put("point", BackupCodec.point(selected))
                    .put("name", s.getString("selection_name", "Punto seleccionado")).put("address", s.getString("selection_address", ""))).put("route", route)
            return BackupCodec.decode(data)
        }
    }
}
