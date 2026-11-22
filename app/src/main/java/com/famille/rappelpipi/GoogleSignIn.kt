package com.famille.rappelpipi

import android.app.Activity
import android.content.Context
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Connexion Google du parent (Credential Manager) : renvoie un jeton d'identite que ParentRepo echange contre une
 * session Firebase. L'identifiant client web vient de google-services.json (default_web_client_id, genere par le
 * plugin) ; il est lu a l'execution pour que l'appli compile meme si ce fichier n'a pas encore ce client.
 */
object GoogleSignIn {
    enum class Err { CANCELLED, NO_ACCOUNT, NOT_CONFIGURED, OTHER }

    fun webClientId(c: Context): String? {
        val id = c.resources.getIdentifier("default_web_client_id", "string", c.packageName)
        return if (id != 0) c.getString(id).takeIf { it.isNotBlank() } else null
    }

    fun getIdToken(a: Activity, onToken: (String) -> Unit, fail: (Err) -> Unit) {
        val web = webClientId(a)
        if (web == null) { fail(Err.NOT_CONFIGURED); return }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(web)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        try {
            CredentialManager.create(a).getCredentialAsync(
                a, request, CancellationSignal(), ContextCompat.getMainExecutor(a),
                object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                    override fun onResult(result: GetCredentialResponse) {
                        val cred = result.credential
                        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                onToken(GoogleIdTokenCredential.createFrom(cred.data).idToken)
                            } catch (e: Throwable) {
                                fail(Err.OTHER)
                            }
                        } else fail(Err.OTHER)
                    }

                    override fun onError(e: GetCredentialException) {
                        fail(when (e) {
                            is GetCredentialCancellationException -> Err.CANCELLED
                            is NoCredentialException -> Err.NO_ACCOUNT
                            else -> Err.OTHER
                        })
                    }
                }
            )
        } catch (e: Throwable) {
            fail(Err.OTHER)
        }
    }

    /** Oublie le compte Google choisi (deconnexion). */
    fun clear(a: Activity) {
        try {
            CredentialManager.create(a).clearCredentialStateAsync(
                androidx.credentials.ClearCredentialStateRequest(), CancellationSignal(), ContextCompat.getMainExecutor(a),
                object : CredentialManagerCallback<Void?, androidx.credentials.exceptions.ClearCredentialException> {
                    override fun onResult(result: Void?) {}
                    override fun onError(e: androidx.credentials.exceptions.ClearCredentialException) {}
                }
            )
        } catch (e: Throwable) {
        }
    }
}
