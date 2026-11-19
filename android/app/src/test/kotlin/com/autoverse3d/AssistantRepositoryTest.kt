package com.autoverse3d

import com.autoverse3d.core.database.ConfigurationEntity
import com.autoverse3d.core.database.ModuleProgressEntity
import com.autoverse3d.core.network.AutoVerseApi
import com.autoverse3d.data.AppContent
import com.autoverse3d.data.AssistantRepository
import com.autoverse3d.data.AssistantResult
import com.autoverse3d.data.ContentSource
import com.autoverse3d.domain.ContentParser
import com.autoverse3d.domain.ContentSearch
import com.autoverse3d.domain.ModuleProgress
import com.autoverse3d.domain.model.AssistantRequest
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val repo = File("../..").canonicalFile
private fun read(p: String) = File(repo, p).readText()

private object TestContent : ContentSource {
    private val content by lazy {
        val learning = ContentParser.learning(read("shared/content/learning_modules.json"))
        val components = ContentParser.components(read("shared/data/components.json"))
        val hotspots = ContentParser.hotspots(read("shared/data/hotspots.json"))
        val ev = ContentParser.evLab(read("shared/content/ev_lab.json"))
        AppContent(ContentParser.vehicles(read("shared/data/vehicles.json")), components, hotspots,
            ContentParser.customization(read("shared/data/customization_options.json")), ContentParser.cameraPresets(read("shared/data/camera_presets.json")),
            learning, ev, emptyMap(), ContentSearch.buildIndex(learning, components, hotspots, ev))
    }
    override suspend fun content() = content
}

class AssistantRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repoUnderTest: AssistantRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(ContentParser.json.asConverterFactory("application/json".toMediaType())).build().create(AutoVerseApi::class.java)
        repoUnderTest = AssistantRepository(api, TestContent)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun buildsRequestAndParsesResponse() = runTest {
        server.enqueue(MockResponse().setBody("""{"answer":"Discs and pads.","sources":[{"id":"component:urbanx_front_brake","title":"Front brake assembly","kind":"component"}],"scope":"vehicle_specific","safetyNotice":null,"refused":false,"provider":"mock","disclaimer":"d","schemaVersion":1}"""))
        val r = repoUnderTest.ask("  How do brakes work?  ", "urbanx_suv", "urbanx_front_brake")
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("How do brakes work?", body["question"]!!.jsonPrimitive.content)
        assertEquals("urbanx_suv", body["vehicleId"]!!.jsonPrimitive.content)
        assertEquals("urbanx_front_brake", body["componentId"]!!.jsonPrimitive.content)
        assertTrue(r is AssistantResult.Online)
        assertEquals("vehicle_specific", (r as AssistantResult.Online).response.scope)
    }

    @Test fun rateLimitFallsBackToOfflineContent() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "42").setBody("""{"error":{"code":"rate_limited","message":"x"}}"""))
        val r = repoUnderTest.ask("what does the inverter do", null, null) as AssistantResult.RateLimited
        assertEquals(42, r.retryAfterSeconds)
        assertTrue(r.matches.first().document.title.contains("Inverter"))
    }

    @Test fun providerOutageFallsBackOffline() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(repoUnderTest.ask("radiator", null, null) is AssistantResult.Offline)
    }

    @Test fun networkFailureFallsBackOffline() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val r = repoUnderTest.ask("crumple zone", null, null) as AssistantResult.Offline
        assertTrue(r.matches.isNotEmpty())
    }

    @Test fun rejectsEmptyAndOverlongQuestionsWithoutNetwork() = runTest {
        assertTrue(repoUnderTest.ask("   ", null, null) is AssistantResult.Invalid)
        assertTrue(repoUnderTest.ask("x".repeat(501), null, null) is AssistantResult.Invalid)
        assertEquals(0, server.requestCount)
    }

    @Test fun requestOmitsAbsentContext() {
        assertEquals("""{"question":"q"}""", ContentParser.json.encodeToString(AssistantRequest.serializer(), AssistantRequest("q")))
    }
}

class EntityMappingTest {
    @Test fun configurationRoundTrip() {
        val c = ContentParser.json.decodeFromString<com.autoverse3d.domain.model.VehicleConfiguration>(read("shared/examples/configuration.example.json"))
        assertEquals(c, ConfigurationEntity.from(c).toDomain())
    }

    @Test fun progressRoundTrip() {
        val p = ModuleProgress("ev_fundamentals", setOf("ev_motor", "ev_battery"), 75, true, "2026.11.1")
        assertEquals(p, ModuleProgressEntity.from(p).toDomain())
        assertEquals("ev_battery,ev_motor", ModuleProgressEntity.from(p).sectionsRead)
        assertEquals(emptySet<String>(), ModuleProgressEntity.from(ModuleProgress("m")).toDomain().sectionsRead)
    }
}
