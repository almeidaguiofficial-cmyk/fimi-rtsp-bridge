package com.b2tech.fimirtsp.streaming

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

/**
 * Encapsula o pedido de permissão do MediaProjection. Manter separado da UI
 * facilita testar o restante do serviço e deixa claro que a Activity só é o
 * caminho para o consentimento — cada sessão exige um result novo.
 */
class ProjectionController(private val context: Context) {

    private val manager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    /** Intent que a Activity dispara via ActivityResultContracts.StartActivityForResult. */
    fun createRequestIntent(): Intent = manager.createScreenCaptureIntent()
}
