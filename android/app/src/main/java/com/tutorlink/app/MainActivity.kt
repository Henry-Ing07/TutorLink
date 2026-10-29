package com.tutorlink.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tutorlink.app.avisos.AvisosWorker
import com.tutorlink.app.ui.AppViewModel
import com.tutorlink.app.ui.Pantalla
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
        val desdeAviso = intent.getBooleanExtra(EXTRA_AVISOS, false)
        if (savedInstanceState == null && desdeAviso && vm.usuario != null) {
            vm.ir(Pantalla.Avisos)
        }
        setContent { TutorLinkApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        if (vm.usuario != null) vm.recargar()
    }

    companion object {
        const val EXTRA_AVISOS = "abrir_avisos"
    }
}
