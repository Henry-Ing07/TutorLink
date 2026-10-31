package com.tutorlink.app.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ingreso con huella (ADR-0007).
 *
 * La contraseña de UNA cuenta se guarda cifrada con una llave AES del Android Keystore que
 * solo se puede usar después de verificar la huella. Si se registra una huella nueva en el
 * celular, Android invalida la llave y hay que volver a activar la huella con la contraseña.
 * Se guarda aparte de la sesión, así sigue disponible después de cerrar sesión.
 */
class Huella(context: Context) {
    private val contexto = context.applicationContext
    private val prefs = contexto.getSharedPreferences("tutorlink_huella", Context.MODE_PRIVATE)

    sealed interface Resultado {
        data class Ok(val email: String, val password: String) : Resultado
        data object Cancelada : Resultado
        /** Se registró otra huella en el celular: por seguridad hay que activarla de nuevo. */
        data object Invalidada : Resultado
        data class Error(val mensaje: String) : Resultado
    }

    /** El celular tiene lector de huella y al menos una huella registrada. */
    fun disponible(): Boolean =
        BiometricManager.from(contexto).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val activada: Boolean get() = prefs.contains(CIFRADO)

    /** Correo de la cuenta asociada a la huella. */
    val cuenta: String? get() = prefs.getString(EMAIL, null)

    fun desactivar() {
        prefs.edit { clear() }
        runCatching { almacen().deleteEntry(ALIAS) }
    }

    /** Pide la huella y, si se reconoce, guarda la cuenta cifrada. */
    fun activar(actividad: FragmentActivity, email: String, password: String, alTerminar: (Resultado) -> Unit) {
        val cifrador = try {
            runCatching { almacen().deleteEntry(ALIAS) }
            Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, crearLlave()) }
        } catch (e: Exception) {
            alTerminar(Resultado.Error("No se pudo preparar el ingreso con huella en este celular."))
            return
        }
        pedirHuella(actividad, "Activar ingreso con huella", email, cifrador, alTerminar) { c ->
            val datos = c.doFinal(password.toByteArray(Charsets.UTF_8))
            prefs.edit {
                putString(EMAIL, email)
                putString(IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
                putString(CIFRADO, Base64.encodeToString(datos, Base64.NO_WRAP))
            }
            Resultado.Ok(email, password)
        }
    }

    /** Pide la huella y, si se reconoce, devuelve el correo y la contraseña guardados. */
    fun ingresar(actividad: FragmentActivity, alTerminar: (Resultado) -> Unit) {
        val email = cuenta
        val iv = prefs.getString(IV, null)
        val cifrado = prefs.getString(CIFRADO, null)
        if (email == null || iv == null || cifrado == null) {
            alTerminar(Resultado.Invalidada)
            return
        }
        val cifrador = try {
            val llave = almacen().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance(TRANSFORMACION).apply {
                init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            desactivar()
            alTerminar(Resultado.Invalidada)
            return
        } catch (e: Exception) {
            alTerminar(Resultado.Error("No se pudo usar la huella. Ingresa con tu contraseña."))
            return
        }
        pedirHuella(actividad, "Ingresar a TutorLink", email, cifrador, alTerminar) { c ->
            Resultado.Ok(email, String(c.doFinal(Base64.decode(cifrado, Base64.NO_WRAP)), Charsets.UTF_8))
        }
    }

    private fun pedirHuella(
        actividad: FragmentActivity,
        titulo: String,
        email: String,
        cifrador: Cipher,
        alTerminar: (Resultado) -> Unit,
        conCifrador: (Cipher) -> Resultado,
    ) {
        val respuesta = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                alTerminar(
                    if (c == null) {
                        Resultado.Error("No se pudo verificar la huella.")
                    } else {
                        runCatching { conCifrador(c) }
                            .getOrElse { Resultado.Error("No se pudo leer la cuenta guardada. Ingresa con tu contraseña.") }
                    },
                )
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                val cancelada = codigo == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    codigo == BiometricPrompt.ERROR_USER_CANCELED || codigo == BiometricPrompt.ERROR_CANCELED
                alTerminar(if (cancelada) Resultado.Cancelada else Resultado.Error(mensaje.toString()))
            }
            // onAuthenticationFailed (dedo no reconocido) no termina: el diálogo deja reintentar.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(email)
            .setNegativeButtonText("Usar contraseña")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(actividad, ContextCompat.getMainExecutor(actividad), respuesta)
            .authenticate(info, BiometricPrompt.CryptoObject(cifrador))
    }

    private fun almacen() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun crearLlave(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                // Cada uso de la llave exige una huella (no solo "desbloqueado hace poco").
                if (Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tutorlink_huella"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val EMAIL = "email"
        const val IV = "iv"
        const val CIFRADO = "cifrado"
    }
}
