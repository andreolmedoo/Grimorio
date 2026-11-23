package com.example.grimorio

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HogwartsCharacter(val name: String?, val species: String?, val house: String?)

fun interface CharacterSource {
    fun search(name: String): HogwartsCharacter?
}

/** Called only by the ViewModel's background executor. */
class CharacterRepository(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : CharacterSource {
    override fun search(name: String): HogwartsCharacter? {
        val query = URLEncoder.encode(name, "UTF-8")
        val connection = openConnection(
            URL("https://hogwarts-api.com/api/characters?search=$query&page_size=1")
        )
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val data = JSONObject(body).getJSONArray("data")
            if (data.length() == 0) return null
            val character = data.getJSONObject(0)
            fun field(key: String): String? = (character.opt(key) as? String)
                ?.trim()?.takeIf { it.isNotEmpty() }
            return HogwartsCharacter(field("name"), field("species"), field("house"))
        } finally {
            connection.disconnect()
        }
    }
}
