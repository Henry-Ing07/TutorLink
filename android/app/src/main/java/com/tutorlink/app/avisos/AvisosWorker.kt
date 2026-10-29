package com.tutorlink.app.avisos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tutorlink.app.MainActivity
import com.tutorlink.app.R
import com.tutorlink.app.data.Api
import com.tutorlink.app.data.Aviso
import com.tutorlink.app.data.SinConexion
import com.tutorlink.app.data.Sesion
import java.util.concurrent.TimeUnit

/**
 * Descarga los avisos nuevos de la bandeja del backend y los muestra como
 * notificaciones del sistema (ADR-0004). Corre periódicamente con WorkManager,
 * que respeta el ahorro de batería y solo se ejecuta con red.
 */
class AvisosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sesion = Sesion(applicationContext)
        if (sesion.token == null) return Result.success()
        return try {
            val nuevos = Api(sesion).avisos(sesion.ultimoAviso, incluirMensajes = true)
            if (nuevos.isNotEmpty()) {
                // Primera descarga tras iniciar sesión: no se repiten avisos viejos.
                if (sesion.ultimoAviso != 0) nuevos.filterNot { it.leida }.forEach(::mostrar)
                sesion.ultimoAviso = nuevos.maxOf { it.id }
            }
            Result.success()
        } catch (_: SinConexion) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun mostrar(aviso: Aviso) {
        val contexto = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                contexto, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        crearCanal(contexto)
        val abrir = PendingIntent.getActivity(
            contexto, if (aviso.tipo == "mensaje") 1 else 0,
            Intent(contexto, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINO, if (aviso.tipo == "mensaje") "mensajes" else "avisos"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(aviso.titulo)
            .setContentText(aviso.mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.mensaje))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(contexto).notify(aviso.id, notificacion)
    }

    companion object {
        private const val CANAL = "avisos_clases"

        fun crearCanal(context: Context) {
            val canal = NotificationChannel(CANAL, "Avisos de clases", NotificationManager.IMPORTANCE_HIGH)
            canal.description = "Clases, avisos de tus docentes y mensajes"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }

        fun programar(context: Context) {
            val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val trabajo = WorkManager.getInstance(context)
            trabajo.enqueueUniquePeriodicWork(
                "avisos", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AvisosWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(conRed).build(),
            )
            trabajo.enqueueUniqueWork(
                "avisos_ahora", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AvisosWorker>().setConstraints(conRed).build(),
            )
        }

        fun detener(context: Context) {
            WorkManager.getInstance(context).cancelAllWork()
        }
    }
}
