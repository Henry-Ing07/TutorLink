package com.tutorlink.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tutorlink.app.avisos.AvisosWorker
import com.tutorlink.app.ui.AppViewModel
import com.tutorlink.app.ui.Pestana
import com.tutorlink.app.ui.TutorLinkApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AvisosWorker.crearCanal(this)
        if (Build.VERSION.SDK_INT >= 33) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) abrirDestino(intent)
        setContent { TutorLinkApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDestino(intent)
    }

    /** Al tocar una notificación se abre la pestaña de mensajes o de avisos. */
    private fun abrirDestino(intent: Intent) {
        if (vm.usuario == null) return
        when (intent.getStringExtra(EXTRA_DESTINO)) {
            "mensajes" -> vm.abrirPestana(Pestana.MENSAJES)
            "avisos" -> vm.abrirPestana(Pestana.AVISOS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.usuario != null) vm.recargar()
    }

    companion object {
        const val EXTRA_DESTINO = "destino"
    }
}
