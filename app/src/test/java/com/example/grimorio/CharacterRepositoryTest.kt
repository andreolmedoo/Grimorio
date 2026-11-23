package com.example.grimorio

import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class CharacterRepositoryTest {
    private class FakeConnection(
        val body: String,
        val status: Int = 200,
        val failure: IOException? = null
    ) : HttpURLConnection(URL("https://example.test")) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int {
            failure?.let { throw it }
            return status
        }
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    @Test fun partialNameIsEncodedAndFirstResultIsUsed() {
        val connection = FakeConnection("""{"data":[
            {"name":"Fred Weasley","species":"Human","house":"Gryffindor"},
            {"name":"George Weasley"}]}""")
        var requestedUrl = ""
        val result = CharacterRepository {
            requestedUrl = it.toString()
            connection
        }.search("Weasley & família")
        assertEquals(HogwartsCharacter("Fred Weasley", "Human", "Gryffindor"), result)
        assertEquals("https://hogwarts-api.com/api/characters?search=Weasley+%26+fam%C3%ADlia&page_size=1", requestedUrl)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
        assertTrue(connection.disconnected)
    }

    @Test fun emptyArrayMeansNotFound() {
        val connection = FakeConnection("""{"data":[]}""")
        assertNull(CharacterRepository { connection }.search("xyz"))
        assertTrue(connection.disconnected)
    }

    @Test fun missingNullAndBlankFieldsAreSafe() {
        listOf("{}", """{"name":null,"species":null,"house":null}""",
            """{"name":" ","species":" ","house":" "}""").forEach { fields ->
            val result = CharacterRepository { FakeConnection("""{"data":[$fields]}""") }.search("abc")
            assertEquals(HogwartsCharacter(null, null, null), result)
        }
    }

    @Test fun malformedResponseIsAnErrorAndDisconnects() {
        val connection = FakeConnection("""{"unexpected":[]}""")
        assertThrows(JSONException::class.java) { CharacterRepository { connection }.search("Harry") }
        assertTrue(connection.disconnected)
    }

    @Test fun serverErrorIsAnErrorAndDisconnects() {
        val connection = FakeConnection("", 503)
        assertThrows(IOException::class.java) { CharacterRepository { connection }.search("Harry") }
        assertTrue(connection.disconnected)
    }

    @Test fun timeoutIsAnErrorAndDisconnects() {
        val connection = FakeConnection("", failure = SocketTimeoutException())
        assertThrows(SocketTimeoutException::class.java) { CharacterRepository { connection }.search("Harry") }
        assertTrue(connection.disconnected)
    }
}
