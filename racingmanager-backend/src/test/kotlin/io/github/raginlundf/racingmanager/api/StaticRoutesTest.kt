package io.github.raginlundf.racingmanager.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The embedded web UI is a single-page app: every route below `/` other than
    the API is rendered by the Angular router, so the backend has to answer
    those paths with the app shell rather than a 404. */
class StaticRoutesTest {

    @Test
    fun `client-side deep link is answered with the app shell`() = testApplication {
        application { configureStaticContent() }

        val response = client.get("/setup")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("<app-root"))
    }

    @Test
    fun `nested client-side deep link is answered with the app shell`() = testApplication {
        application { configureStaticContent() }

        val response = client.get("/racemanager/tenant")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("<app-root"))
    }

    @Test
    fun `an existing static file is still served as itself`() = testApplication {
        application { configureStaticContent() }

        val response = client.get("/index.html")

        assertEquals(expected = HttpStatusCode.OK, actual = response.status)
        assertTrue(actual = response.bodyAsText().contains("<app-root"))
    }

    @Test
    fun `an unknown api path is not swallowed by the fallback`() = testApplication {
        application { configureStaticContent() }

        val response = client.get("/api/v1/there-is-no-such-endpoint")

        assertEquals(expected = HttpStatusCode.NotFound, actual = response.status)
    }

    @Test
    fun `a missing asset stays a 404 instead of returning the shell`() = testApplication {
        application { configureStaticContent() }

        val response = client.get("/main-DOESNOTEXIST.js")

        assertEquals(expected = HttpStatusCode.NotFound, actual = response.status)
    }
}
