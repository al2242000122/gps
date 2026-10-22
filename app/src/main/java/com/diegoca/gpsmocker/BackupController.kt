package com.diegoca.gpsmocker

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object BackupGuard { val restoring = AtomicBoolean(false) }

/** SAF access is short lived; no storage permission or persistent URI grant is needed. */
class BackupController(private val activity: AppCompatActivity, private val beforeRestore: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val export = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val data = UserDataRepository.get(activity).snapshot()
                activity.contentResolver.openOutputStream(uri, "wt")?.use { BackupCodec.write(it, data) }
                    ?: error("No se pudo abrir el archivo para guardar.")
            } }.fold({ notice("Respaldo guardado") }, { notice("No se pudo guardar: ${it.message}") })
        }
    }
    private val import = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                activity.contentResolver.openInputStream(uri)?.use(BackupCodec::read) ?: error("No se pudo abrir el archivo.")
            } }.fold({ preview(it) }, { notice("Respaldo inválido: ${it.message}") })
        }
    }
    fun show() {
        MaterialAlertDialogBuilder(activity).setTitle("Respaldo y restauración")
            .setMessage("El archivo contiene favoritos, historial, selección, rutas y ajustes. Incluye ubicaciones personales; guárdalo en un lugar de confianza.")
            .setPositiveButton("Exportar") { _,_ -> export.launch("gps-mocker-respaldo.json") }
            .setNeutralButton("Restaurar") { _,_ -> import.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
            .setNegativeButton(R.string.cancel, null).show()
    }
    private fun preview(backup: BackupDocument) {
        val d = backup.data
        MaterialAlertDialogBuilder(activity).setTitle("Reemplazar los datos actuales")
            .setMessage("${DateFormat.getDateTimeInstance().format(Date(backup.exportedAt))}\n\n${d.favorites.size} favoritos · ${d.history.size} recientes\n${d.route.editablePoints.size} puntos elegidos · ${d.route.points.size} vértices de ruta\n\nSe reemplazarán tus datos y ajustes por los de este archivo. La simulación seguirá detenida.")
            .setNegativeButton(R.string.cancel, null).setPositiveButton("Restaurar") { _,_ ->
                if (MockLocationService.isRunning || SimulationStore(activity).cleanupNeeded || !BackupGuard.restoring.compareAndSet(false, true)) {
                    notice("Detén la simulación y completa la restauración del GPS antes de importar.")
                } else {
                    beforeRestore()
                    scope.launch {
                        val result = try { runCatching { withContext(NonCancellable + Dispatchers.IO) {
                            UserDataRepository.get(activity).restore(backup.data)
                            BackupGuard.restoring.set(false)
                            activity.applicationContext.sendBroadcast(Intent(ACTION_RESTORED).setPackage(activity.packageName))
                        } } }
                            finally { BackupGuard.restoring.set(false) }
                        result.fold({ notice("Datos restaurados") }, { notice("No se restauraron los datos: ${it.message}") })
                    }
                }
            }.show()
    }
    private fun notice(message: String) { if (!activity.isDestroyed && !activity.isFinishing) android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show() }
    fun close() { scope.cancel() }
    companion object { const val ACTION_RESTORED = "com.diegoca.gpsmocker.DATA_RESTORED" }
}
