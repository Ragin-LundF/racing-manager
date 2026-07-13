package io.github.raginlundf.racingmanager

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRouteTest {

    @Test
    fun `health endpoint returns UP status`() = testApplication {
        application { module() }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"UP\""))
    }

    @Test
    fun `readiness endpoint returns UP`() = testApplication {
        application { module() }

        val response = client.get("/api/v1/readiness")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"UP\""))
    }

    @Test
    fun `diagnostics endpoint requires authentication`() = testApplication {
        application { module() }

        val response = client.get("/api/v1/diagnostics")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `diagnostics endpoint returns bundle for an admin`() = testApplication {
        application { module() }

        // module() seeds the demo admin (admin/admin) on first run in this test
        // class's shared real DB (see other tests in this file) — log in with
        // those credentials rather than attempting a second, redundant setup.
        val loginBody = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin"}""")
        }.bodyAsText()
        val accessToken = """"accessToken":"([^"]+)"""".toRegex().find(loginBody)!!.groupValues[1]

        val response = client.get("/api/v1/diagnostics") {
            header("Authorization", "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"database\""))
        assertTrue(body.contains("\"events\""))
        assertTrue(body.contains("\"unfinishedHeats\""))
    }

    @Test
    fun `build-info endpoint returns application metadata`() = testApplication {
        application { module() }

        val response = client.get("/api/v1/build-info")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"name\":\"racingmanager\""))
        assertTrue(body.contains("\"version\":\"1.0-SNAPSHOT\""))
    }
}