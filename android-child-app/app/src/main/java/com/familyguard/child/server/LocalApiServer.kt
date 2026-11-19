package com.familyguard.child.server

import android.content.Context
import com.familyguard.child.collect.UsageCollector
import com.familyguard.child.data.AppDatabase
import com.familyguard.child.data.Prefs
import com.familyguard.child.util.TimeUtil
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

/**
 * Local-only HTTP server (phone-to-phone over Wi-Fi). Binds to all interfaces
 * so the parent's phone on the same network can reach it, but every data
 * endpoint requires a bearer token obtained by POSTing the parent PIN.
 *
 * No data ever leaves the LAN — there is no cloud component.
 */
class LocalApiServer(
    private val context: Context,
    port: Int
) : NanoHTTPD(port) {

    private val gson = Gson()
    private val prefs = Prefs.get(context)
    private val db = AppDatabase.get(context)

    // token -> expiry epoch millis
    private val tokens = HashMap<String, Long>()
    private val tokenTtlMs = 12 * 60 * 60 * 1000L // 12h

    fun startSafe() {
        start(SOCKET_READ_TIMEOUT, false)
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            if (session.method == Method.OPTIONS) {
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "").also { addCors(it) }
            }
            when {
                uri == "/" || uri == "/index.html" -> serveDashboard()
                uri == "/api/status" -> json(statusJson())           // unauthenticated, minimal
                uri == "/api/auth" -> handleAuth(session)
                uri.startsWith("/api/") -> handleAuthed(uri, session)
                else -> notFound()
            }
        } catch (t: Throwable) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"server_error"}"""
            )
        }.also { addCors(it) }
    }

    // ---- Auth ----------------------------------------------------------------

    private fun handleAuth(session: IHTTPSession): Response {
        if (session.method != Method.POST) return methodNotAllowed()
        val body = readBody(session)
        val pin = runCatching { gson.fromJson(body, AuthReq::class.java)?.pin }.getOrNull()
            ?: return json("""{"error":"bad_request"}""", Response.Status.BAD_REQUEST)
        if (!prefs.isPinSet || !prefs.verifyPin(pin)) {
            return json("""{"error":"invalid_pin"}""", Response.Status.UNAUTHORIZED)
        }
        val token = newToken()
        tokens[token] = System.currentTimeMillis() + tokenTtlMs
        return json(gson.toJson(AuthResp(token, tokenTtlMs / 1000)))
    }

    private fun handleAuthed(uri: String, session: IHTTPSession): Response {
        if (!isAuthorized(session)) {
            return json("""{"error":"unauthorized"}""", Response.Status.UNAUTHORIZED)
        }
        val params = session.parameters
        return when (uri) {
            "/api/summary" -> json(summaryJson())
            "/api/usage" -> {
                val days = params["days"]?.firstOrNull()?.toIntOrNull() ?: 7
                json(usageJson(days))
            }
            "/api/web" -> {
                val days = params["days"]?.firstOrNull()?.toIntOrNull() ?: 7
                json(webJson(days))
            }
            "/api/locations" -> {
                val days = params["days"]?.firstOrNull()?.toIntOrNull() ?: 3
                json(locationsJson(days))
            }
            else -> notFound()
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"]
        val fromHeader = header?.removePrefix("Bearer ")?.trim()
        val fromQuery = session.parameters["token"]?.firstOrNull()
        val token = (fromHeader?.takeIf { it.isNotBlank() }) ?: fromQuery ?: return false
        val expiry = tokens[token] ?: return false
        if (System.currentTimeMillis() > expiry) {
            tokens.remove(token)
            return false
        }
        return true
    }

    private fun newToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---- JSON builders -------------------------------------------------------

    private fun statusJson(): String = gson.toJson(
        mapOf(
            "app" to "FamilyGuard",
            "ok" to true,
            "pinRequired" to prefs.isPinSet,
            "serverTime" to System.currentTimeMillis()
        )
    )

    private fun summaryJson(): String = runBlocking {
        val todayStart = TimeUtil.todayStart()
        val today = db.usageDao().forDay(todayStart)
        val totalMs = today.sumOf { it.totalForegroundMs }
        val topApps = today.take(5).map {
            AppUsageDto(it.appLabel, it.packageName, it.totalForegroundMs, it.launchCount)
        }
        val lastLoc = db.locationDao().latest()
        val usageAccess = UsageCollector(context).hasUsageAccess()
        gson.toJson(
            SummaryDto(
                deviceModel = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE,
                generatedAt = System.currentTimeMillis(),
                todayScreenTimeMs = totalMs,
                topAppsToday = topApps,
                lastLocation = lastLoc?.let {
                    LocationDto(it.timestamp, it.latitude, it.longitude, it.accuracyM)
                },
                usageAccessGranted = usageAccess
            )
        )
    }

    private fun usageJson(days: Int): String = runBlocking {
        val since = TimeUtil.daysAgoStart(days)
        val rows = db.usageDao().since(since)
        // group by day
        val byDay = rows.groupBy { it.dayEpoch }.toSortedMap(compareByDescending { it })
        val out = byDay.map { (day, list) ->
            DayUsageDto(
                dayEpoch = day,
                totalMs = list.sumOf { it.totalForegroundMs },
                apps = list.sortedByDescending { it.totalForegroundMs }.map {
                    AppUsageDto(it.appLabel, it.packageName, it.totalForegroundMs, it.launchCount)
                }
            )
        }
        gson.toJson(mapOf("days" to out))
    }

    private fun webJson(days: Int): String = runBlocking {
        val since = TimeUtil.daysAgoStart(days)
        val rows = db.webVisitDao().since(since)
        val out = rows.map { WebDto(it.timestamp, it.url, it.host, it.browserPackage) }
        gson.toJson(mapOf("visits" to out))
    }

    private fun locationsJson(days: Int): String = runBlocking {
        val since = TimeUtil.daysAgoStart(days)
        val rows = db.locationDao().since(since)
        val out = rows.map { LocationDto(it.timestamp, it.latitude, it.longitude, it.accuracyM) }
        gson.toJson(mapOf("locations" to out))
    }

    // ---- Helpers -------------------------------------------------------------

    private fun serveDashboard(): Response {
        val html = runCatching {
            context.assets.open("dashboard.html").bufferedReader().use { it.readText() }
        }.getOrDefault("<h1>FamilyGuard</h1><p>Dashboard asset missing.</p>")
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun readBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private fun json(body: String, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json", body)

    private fun notFound(): Response =
        json("""{"error":"not_found"}""", Response.Status.NOT_FOUND)

    private fun methodNotAllowed(): Response =
        json("""{"error":"method_not_allowed"}""", Response.Status.METHOD_NOT_ALLOWED)

    private fun addCors(r: Response) {
        // Allow the dashboard/iOS app (same LAN) to call the API from fetch().
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    // ---- DTOs ----------------------------------------------------------------

    private data class AuthReq(val pin: String?)
    private data class AuthResp(val token: String, val expiresInSec: Long)

    private data class SummaryDto(
        val deviceModel: String,
        val androidVersion: String,
        val generatedAt: Long,
        val todayScreenTimeMs: Long,
        val topAppsToday: List<AppUsageDto>,
        val lastLocation: LocationDto?,
        val usageAccessGranted: Boolean
    )

    private data class DayUsageDto(val dayEpoch: Long, val totalMs: Long, val apps: List<AppUsageDto>)
    private data class AppUsageDto(val label: String, val pkg: String, val totalMs: Long, val launches: Int)
    private data class WebDto(val timestamp: Long, val url: String, val host: String, val browser: String)
    private data class LocationDto(val timestamp: Long, val lat: Double, val lon: Double, val accuracyM: Float)
}
