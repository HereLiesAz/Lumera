package com.hereliesaz.illumera.remote_input

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.BindException
import java.util.UUID

class RemoteTextServerManager {
    private var server: RemoteTextServer? = null

    companion object {
        private const val PORT_START = 8080
        private const val PORT_END = 8090
    }

    suspend fun startServer(
        title: String,
        prompt: String,
        fieldLabel: String,
        helperUrl: String? = null,
        helperLabel: String? = null,
        secret: Boolean = true,
        onTextReceived: (String) -> Unit
    ): ServerInfo? = withContext(Dispatchers.IO) {
        val ip = NetworkUtils.getLocalIpAddress() ?: return@withContext null
        val pairingToken = UUID.randomUUID().toString()

        for (port in PORT_START..PORT_END) {
            try {
                val candidate = RemoteTextServer(
                    port = port,
                    pairingToken = pairingToken,
                    title = title,
                    prompt = prompt,
                    fieldLabel = fieldLabel,
                    helperUrl = helperUrl,
                    helperLabel = helperLabel,
                    secret = secret,
                    onTextReceived = onTextReceived
                )
                candidate.start()
                server = candidate
                return@withContext ServerInfo(ip, port, pairingToken)
            } catch (_: BindException) {
                continue
            } catch (e: Exception) {
                com.hereliesaz.illumera.crash.AppErrors.w("RemoteTextServerManager", "Port binding failed", e)
            }
        }
        null
    }

    fun stopServer() {
        server?.stop()
        server = null
    }
}
