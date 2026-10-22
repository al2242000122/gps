package com.diegoca.gpsmocker

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*

/** Corruption must never silently turn a user's saved places into an empty library. */
class DataRecoveryActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val import = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use(BackupCodec::read) ?: error("No se pudo abrir el archivo.")
            } }.fold({ backup ->
                MaterialAlertDialogBuilder(this@DataRecoveryActivity).setTitle("Restaurar respaldo")
                    .setMessage("Se restaurarán ${backup.data.favorites.size} favoritos y ${backup.data.history.size} lugares recientes. El archivo dañado se conservará en el teléfono.")
                    .setNegativeButton(R.string.cancel,null).setPositiveButton("Restaurar") { _,_ ->
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { UserDataRepository.recover(this@DataRecoveryActivity, backup.data) } }
                                .fold({ startActivity(Intent(this@DataRecoveryActivity,MainActivity::class.java).putExtra("skipInitialFix",true)); finish() },
                                    { Toast.makeText(this@DataRecoveryActivity,"No se restauraron los datos: ${it.message}",Toast.LENGTH_LONG).show() })
                        }
                    }.show()
            }, { Toast.makeText(this@DataRecoveryActivity,"Respaldo inválido: ${it.message}",Toast.LENGTH_LONG).show() })
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding=(24*resources.displayMetrics.density).toInt()
        setContentView(LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(padding,padding,padding,padding)
            addView(TextView(this@DataRecoveryActivity).apply {
                text="Tus datos necesitan recuperación\n\nNo se pudieron leer los datos guardados. Se conservan los archivos originales. Importa un respaldo válido para recuperar la app. No desinstales si aún necesitas rescatar datos."; textSize=18f
            })
            addView(MaterialButton(this@DataRecoveryActivity).apply {
                text="Detener y restaurar GPS"
                setOnClickListener {
                    runCatching { startService(Intent(this@DataRecoveryActivity,MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP)) }
                        .onFailure { Toast.makeText(this@DataRecoveryActivity,"No se pudo detener. Revisa la selección de aplicación de ubicación simulada.",Toast.LENGTH_LONG).show() }
                }
            })
            addView(MaterialButton(this@DataRecoveryActivity).apply { text="Restaurar respaldo"; setOnClickListener { import.launch(arrayOf("application/json","*/*")) } })
        })
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
