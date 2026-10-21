package com.feishin.mobile.ui.login

import com.feishin.mobile.core.common.Result
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P8.4-D: every connect/probe failure lands on one specific, secret-free line. */
class LoginErrorMapperTest {

    @Test
    fun `bad password maps to wrong credentials`() {
        val f = LoginErrorMapper.map(Result.Err("Wrong username or password."))
        assertEquals(LoginErrorMapper.WRONG_CREDENTIALS, f.message)
        assertNull(f.details)
    }

    @Test
    fun `unreachable host maps to the connection hint`() {
        val f = LoginErrorMapper.map(
            Result.Err("Server not found.", UnknownHostException("music.example")),
        )
        assertEquals(LoginErrorMapper.UNREACHABLE, f.message)
    }

    @Test
    fun `timeout maps to the connection hint`() {
        val f = LoginErrorMapper.map(Result.Err("timeout", SocketTimeoutException()))
        assertEquals(LoginErrorMapper.UNREACHABLE, f.message)
    }

    @Test
    fun `broken tls maps to the self-signed hint`() {
        val f = LoginErrorMapper.map(
            Result.Err("handshake", SSLHandshakeException("trust anchor not found")),
        )
        assertEquals(LoginErrorMapper.CERTIFICATE, f.message)
        assertTrue(f.message.contains("Allow self-signed certificate"))
    }

    @Test
    fun `invalid url maps to the url example`() {
        val f = LoginErrorMapper.map(Result.Err("Invalid server URL."))
        assertEquals(LoginErrorMapper.BAD_URL, f.message)
    }

    @Test
    fun `empty username maps to the username hint`() {
        val f = LoginErrorMapper.map(Result.Err("Username is empty."))
        assertEquals(LoginErrorMapper.EMPTY_USERNAME, f.message)
    }

    @Test
    fun `non navidrome response maps to the server hint`() {
        val html = Result.Err(
            "Server returned an unexpected response (is this a Subsonic/Navidrome server?)",
        )
        assertEquals(LoginErrorMapper.NOT_NAVIDROME, LoginErrorMapper.map(html).message)
    }

    @Test
    fun `unknown failure keeps the raw text behind details`() {
        val f = LoginErrorMapper.map(Result.Err("Server error (code 70)."))
        assertEquals("Couldn't connect to the server.", f.message)
        assertEquals("Server error (code 70).", f.details)
    }

    @Test
    fun `plain io failure maps to unreachable`() {
        val f = LoginErrorMapper.map(Result.Err("Network error (connection reset).", IOException()))
        assertEquals(LoginErrorMapper.UNREACHABLE, f.message)
    }

    @Test
    fun `empty error message still produces a message`() {
        val f = LoginErrorMapper.map(Result.Err(""))
        assertEquals(LoginErrorMapper.UNREACHABLE, f.message)
        assertNull(f.details)
    }

    @Test
    fun `details redact password shaped parameters`() {
        val f = LoginErrorMapper.map(Result.Err("Bad request: /rest/ping?u=bob&p=hunter2&v=1.16"))
        assertEquals("Couldn't connect to the server.", f.message)
        assertTrue(!f.details!!.contains("hunter2"))
        assertTrue(f.details!!.contains("p=***"))
    }
}
