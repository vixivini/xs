package com.example.xs.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object MediaDownloadManager {

    /**
     * Baixa a mídia em resolução máxima e salva na pasta pública de Downloads/Galeria
     */
    fun downloadMedia(context: Context, url: String) {
        try {
            // Inteligência 2026: Garante que estamos pegando a versão FULL e não a THUMBNAIL
            val finalUrl = if (url.contains("/thumbnails/")) {
                url.replace("/thumbnails/", "/full/").replace(".thumb.", ".")
            } else url

            val request = DownloadManager.Request(Uri.parse(finalUrl))
                .setTitle("Baixando mídia XS")
                .setDescription("Salvando arquivo em alta resolução...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", Constants.USER_AGENT)
                .addRequestHeader("Referer", MediaUtils.getReferer(url))

            // Define o nome do arquivo baseado na URL
            val fileName = "XS_${System.currentTimeMillis()}_" + finalUrl.substringAfterLast("/").substringBefore("?")
            
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            Toast.makeText(context, "Download iniciado! Verifique suas notificações.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao iniciar download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
