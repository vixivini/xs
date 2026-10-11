package com.example.xs.extractor

interface MediaExtractorPlugin {
    /**
     * Verifica se este plugin sabe lidar com a URL fornecida.
     */
    fun canHandle(url: String): Boolean
    
    /**
     * Entra na página e devolve a lista de links REAIS brutos (.mp4, .jpg, etc) em alta resolução.
     */
    suspend fun extract(url: String): List<String>
}
