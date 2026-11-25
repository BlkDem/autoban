package com.blkdem.autoban.lookup

import android.util.Log
import com.blkdem.autoban.data.Repository
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Поиск сведений о номере на zvonili.com с кэшем в БД.
 * Один запрос на номер одновременно: повторные вызовы ждут уже идущий.
 */
class NumberLookup(private val repo: Repository) {

    /** Запросы к сайту. */
    private val pool = Executors.newFixedThreadPool(2)
    /** Ожидание результата для фоновых вызовов — отдельно, чтобы не занимать потоки запросов. */
    private val waiters = Executors.newCachedThreadPool()
    private val inFlight = ConcurrentHashMap<String, Future<NumberReport?>>()

    fun supports(number: String?): Boolean = ZvoniliParser.isRussian(number)

    /** Свежая запись из кэша или null. */
    fun cached(number: String, now: Long = System.currentTimeMillis()): NumberReport? =
        repo.cachedReport(number)?.takeIf { now - it.fetchedAt < ttl(it) }

    /**
     * Сведения о номере: из кэша или с сайта. Ждёт не дольше [timeoutMs]; если не успели —
     * возвращает null, а запрос продолжается и заполнит кэш. null и при ошибке сети.
     */
    fun lookup(number: String, timeoutMs: Long, force: Boolean = false): NumberReport? {
        if (!supports(number)) return null
        if (!force) cached(number)?.let { return it }
        if (timeoutMs <= 0) return null
        val f = inFlight.computeIfAbsent(number) { n ->
            pool.submit<NumberReport?> {
                try { fetch(n)?.also(repo::cacheReport) } finally { inFlight.remove(n) }
            }
        }
        return try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            Log.i(TAG, "lookup $number: no answer in ${timeoutMs}ms")
            null
        } catch (e: Exception) {
            Log.w(TAG, "lookup $number failed: ${e.cause ?: e}")
            null
        }
    }

    /** Запустить поиск в фоне и вызвать [done] с результатом (null — не удалось). */
    fun lookupAsync(number: String, timeoutMs: Long = BACKGROUND_TIMEOUT_MS, done: (NumberReport?) -> Unit) {
        waiters.execute { done(runCatching { lookup(number, timeoutMs) }.getOrNull()) }
    }

    fun cleanup(now: Long = System.currentTimeMillis()) = repo.deleteReportsBefore(now - MAX_TTL_MS)

    private fun fetch(number: String): NumberReport? {
        val url = ZvoniliParser.url(number) ?: return null
        val started = System.currentTimeMillis()
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 5000
            c.readTimeout = 5000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
            val code = c.responseCode
            if (code != 200) {
                Log.w(TAG, "lookup $number: HTTP $code")
                return null
            }
            val html = c.inputStream.use { input ->
                // Всё нужное — в начале страницы; у популярных номеров она весит мегабайты.
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (out.size() < MAX_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }
            ZvoniliParser.parse(number, html).also {
                Log.i(TAG, "lookup $number: ${it.summary()} in ${System.currentTimeMillis() - started}ms")
            }
        } catch (e: Exception) {
            Log.w(TAG, "lookup $number: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val TAG = "Autoban"
        private const val MAX_BYTES = 200 * 1024
        const val BACKGROUND_TIMEOUT_MS = 12_000L
        private const val DAY = 86_400_000L
        private const val MAX_TTL_MS = 3 * DAY
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        /** Номера с отзывами перепроверяем раз в 3 дня, без отзывов — раз в сутки. */
        fun ttl(r: NumberReport): Long = if (r.found && r.reviews > 0) MAX_TTL_MS else DAY
    }
}
