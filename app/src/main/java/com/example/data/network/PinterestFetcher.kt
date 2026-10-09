package com.example.data.network

import android.util.Log
import com.example.model.PinterestBoardInfo
import com.example.model.PinterestPin
import com.example.model.PinFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class PinterestFetcher(
    private val apiTokenProvider: () -> String?
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                .build()
            chain.proceed(request)
        }
        .build()

    data class ParsedBoardUrl(
        val username: String,
        val boardName: String,
        val canonicalUrl: String
    )

    suspend fun parseAndResolveUrl(rawUrl: String): ParsedBoardUrl = withContext(Dispatchers.IO) {
        var cleanUrl = rawUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }

        // Handle shortened pin.it URLs by following redirect
        if (cleanUrl.contains("pin.it/")) {
            val req = Request.Builder().url(cleanUrl).build()
            client.newCall(req).execute().use { response ->
                cleanUrl = response.request.url.toString()
            }
        }

        val uri = URI(cleanUrl)
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("無効なURLドメインです。")
        
        if (!host.contains("pinterest.") && !host.contains("pin.it")) {
            throw IllegalArgumentException("PinterestのURLを入力してください（例: https://www.pinterest.jp/username/board-name/）")
        }

        val pathSegments = uri.path.split("/").filter { it.isNotBlank() }
        if (pathSegments.size < 2) {
            throw IllegalArgumentException("ボードのURL形式が不正です。ユーザー名とボード名が含まれているか確認してください。")
        }

        val username = pathSegments[0]
        val boardName = pathSegments[1]

        if (username.equals("pin", ignoreCase = true) || username.equals("search", ignoreCase = true) || username.equals("today", ignoreCase = true)) {
            throw IllegalArgumentException("個別のピンや検索結果ではなく、ボードのURLを入力してください。")
        }

        val canonical = "https://www.pinterest.com/$username/$boardName/"
        ParsedBoardUrl(username, boardName, canonical)
    }

    suspend fun fetchBoardPins(
        rawUrl: String,
        maxPinsLimit: Int = 500,
        bookmark: String? = null
    ): PinFetchResult = withContext(Dispatchers.IO) {
        val parsed = parseAndResolveUrl(rawUrl)
        val apiToken = apiTokenProvider()?.trim()

        if (!apiToken.isNullOrEmpty()) {
            try {
                return@withContext fetchPinsViaApiV5(parsed, apiToken, maxPinsLimit, bookmark)
            } catch (e: Exception) {
                Log.w("PinterestFetcher", "API v5 failed, falling back to web fetcher: ${e.message}")
            }
        }

        // Fallback or Primary Web Scraper / JSON Feed Fetcher
        return@withContext fetchPinsViaWebScraper(parsed, maxPinsLimit, bookmark)
    }

    private fun fetchPinsViaApiV5(
        parsed: ParsedBoardUrl,
        apiToken: String,
        maxPinsLimit: Int,
        bookmark: String?
    ): PinFetchResult {
        // Pinterest API v5 endpoint for board pins
        val urlBuilder = StringBuilder("https://api.pinterest.com/v5/boards/${parsed.username}/${parsed.boardName}/pins?page_size=100")
        if (!bookmark.isNullOrEmpty()) {
            urlBuilder.append("&bookmark=").append(bookmark)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Authorization", "Bearer $apiToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("APIエラー (HTTP ${response.code}): Access Tokenをご確認ください。")
            }

            val bodyStr = response.body?.string() ?: throw IllegalStateException("Pinterest APIからデータを受信できませんでした。")
            val json = JSONObject(bodyStr)
            val items = json.optJSONArray("items") ?: JSONArray()
            val nextBookmark = json.optString("bookmark", null)

            val pinsList = mutableListOf<PinterestPin>()
            var skipped = 0

            for (i in 0 until items.length()) {
                if (pinsList.size >= maxPinsLimit) break
                val item = items.getJSONObject(i)
                val id = item.optString("id", System.currentTimeMillis().toString() + i)
                val title = item.optString("title", "Pin $id")
                val desc = item.optString("description", "")
                val media = item.optJSONObject("media")
                val images = media?.optJSONObject("images") ?: item.optJSONObject("images")

                var originalUrl = ""
                var thumbUrl = ""
                var w = 0
                var h = 0

                if (images != null) {
                    val orig = images.optJSONObject("originals") ?: images.optJSONObject("1200x") ?: images.optJSONObject("600x")
                    if (orig != null) {
                        originalUrl = orig.optString("url", "")
                        w = orig.optInt("width", 0)
                        h = orig.optInt("height", 0)
                    }
                    val thumb = images.optJSONObject("236x") ?: images.optJSONObject("170x") ?: orig
                    if (thumb != null) {
                        thumbUrl = thumb.optString("url", originalUrl)
                    }
                }

                if (originalUrl.isNotBlank() && isAllowedDomain(originalUrl)) {
                    pinsList.add(
                        PinterestPin(
                            id = id,
                            title = title,
                            description = desc,
                            originalImageUrl = convertToOriginalsQuality(originalUrl),
                            thumbnailUrl = thumbUrl.ifBlank { originalUrl },
                            width = w,
                            height = h,
                            pinUrl = "https://www.pinterest.com/pin/$id/"
                        )
                    )
                } else {
                    skipped++
                }
            }

            val boardInfo = PinterestBoardInfo(
                username = parsed.username,
                boardName = parsed.boardName,
                title = "${parsed.username} / ${parsed.boardName}",
                description = "Pinterest Board",
                pinCount = pinsList.size
            )

            return PinFetchResult(
                boardInfo = boardInfo,
                pins = pinsList,
                skippedCount = skipped,
                hasMore = !nextBookmark.isNullOrEmpty() && pinsList.size < maxPinsLimit,
                nextBookmark = nextBookmark,
                totalAvailableInBoard = pinsList.size
            )
        }
    }

    private fun fetchPinsViaWebScraper(
        parsed: ParsedBoardUrl,
        maxPinsLimit: Int,
        bookmark: String?
    ): PinFetchResult {
        // Pinterest Web Resource Feed API
        val resourceUrl = "https://www.pinterest.com/resource/BoardFeedResource/get/"
        val sourceUrl = "/${parsed.username}/${parsed.boardName}/"

        val optionsJson = JSONObject().apply {
            put("board_id", "")
            put("board_url", sourceUrl)
            put("current_page", 1)
            put("field_set_key", "react_grid_pin")
            put("filter_section_pins", false)
            put("layout", "default")
            put("page_size", 50)
            put("redux_normalize_feed", true)
            if (!bookmark.isNullOrEmpty()) {
                put("bookmarks", JSONArray().put(bookmark))
            }
        }

        val requestUrl = "$resourceUrl?source_url=${sourceUrl}&data={\"options\":$optionsJson}"

        val request = Request.Builder()
            .url(requestUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .build()

        var pinsList = mutableListOf<PinterestPin>()
        var skipped = 0
        var nextBookmark: String? = null
        var boardTitle = "${parsed.username} / ${parsed.boardName}"

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Try alternative direct JSON endpoint
                    return fetchPinsViaDirectBoardJson(parsed)
                }

                val bodyStr = response.body?.string() ?: return fetchPinsViaDirectBoardJson(parsed)
                val root = JSONObject(bodyStr)
                val resourceData = root.optJSONObject("resource_response")
                val data = resourceData?.opt("data")

                val bookmarksArray = resourceData?.optJSONArray("bookmarks")
                if (bookmarksArray != null && bookmarksArray.length() > 0) {
                    nextBookmark = bookmarksArray.getString(0)
                    if (nextBookmark == "-end-") nextBookmark = null
                }

                if (data is JSONArray) {
                    for (i in 0 until data.length()) {
                        val pinObj = data.getJSONObject(i)
                        val pin = parsePinJsonObject(pinObj)
                        if (pin != null) {
                            pinsList.add(pin)
                        } else {
                            skipped++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PinterestFetcher", "Resource API failed, attempting direct board JSON: ${e.message}")
            return fetchPinsViaDirectBoardJson(parsed)
        }

        if (pinsList.isEmpty()) {
            return fetchPinsViaDirectBoardJson(parsed)
        }

        val boardInfo = PinterestBoardInfo(
            username = parsed.username,
            boardName = parsed.boardName,
            title = boardTitle,
            description = "Pinterest Board",
            pinCount = pinsList.size
        )

        return PinFetchResult(
            boardInfo = boardInfo,
            pins = pinsList,
            skippedCount = skipped,
            hasMore = !nextBookmark.isNullOrEmpty() && pinsList.size < maxPinsLimit,
            nextBookmark = nextBookmark,
            totalAvailableInBoard = pinsList.size
        )
    }

    private fun fetchPinsViaDirectBoardJson(parsed: ParsedBoardUrl): PinFetchResult {
        // Fallback: Web Page HTML scraping / JSON embedded state extraction
        val boardPageUrl = "https://www.pinterest.com/${parsed.username}/${parsed.boardName}/"
        val request = Request.Builder().url(boardPageUrl).build()

        val pinsList = mutableListOf<PinterestPin>()
        var skipped = 0

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Pinterestボードを取得できませんでした。(HTTP ${response.code}) URLが正しいか、公開ボードかご確認ください。")
            }

            val html = response.body?.string() ?: throw IllegalStateException("ボードページの取得に失敗しました。")

            // Regex extract img src URLs from i.pinimg.com
            val pattern = Regex("""https://i\.pinimg\.com/[0-9x]+/[a-f0-9/]+\.(jpg|jpeg|png|webp)""")
            val matches = pattern.findAll(html).map { it.value }.distinct().toList()

            for ((idx, url) in matches.withIndex()) {
                if (isAllowedDomain(url)) {
                    val origUrl = convertToOriginalsQuality(url)
                    val id = "web_${idx + 1}"
                    pinsList.add(
                        PinterestPin(
                            id = id,
                            title = "Pin #${idx + 1}",
                            description = "Pinterest Pin Image",
                            originalImageUrl = origUrl,
                            thumbnailUrl = url,
                            pinUrl = "https://www.pinterest.com/pin/$id/"
                        )
                    )
                } else {
                    skipped++
                }
            }
        }

        if (pinsList.isEmpty()) {
            throw IllegalStateException("ボード内に画像が見つかりませんでした。公開設定のボードであることをご確認ください。")
        }

        val boardInfo = PinterestBoardInfo(
            username = parsed.username,
            boardName = parsed.boardName,
            title = "${parsed.username} / ${parsed.boardName}",
            pinCount = pinsList.size
        )

        return PinFetchResult(
            boardInfo = boardInfo,
            pins = pinsList,
            skippedCount = skipped,
            hasMore = false,
            nextBookmark = null,
            totalAvailableInBoard = pinsList.size
        )
    }

    private fun parsePinJsonObject(pinObj: JSONObject): PinterestPin? {
        val id = pinObj.optString("id", "")
        if (id.isBlank()) return null

        val title = pinObj.optString("title").ifBlank { pinObj.optString("grid_title").ifBlank { "Pin $id" } }
        val description = pinObj.optString("description", "")
        val images = pinObj.optJSONObject("images") ?: return null

        var origUrl = ""
        var thumbUrl = ""
        var w = 0
        var h = 0

        val orig = images.optJSONObject("originals") ?: images.optJSONObject("1200x") ?: images.optJSONObject("736x")
        if (orig != null) {
            origUrl = orig.optString("url", "")
            w = orig.optInt("width", 0)
            h = orig.optInt("height", 0)
        }

        val thumb = images.optJSONObject("236x") ?: images.optJSONObject("170x") ?: orig
        if (thumb != null) {
            thumbUrl = thumb.optString("url", origUrl)
        }

        if (origUrl.isBlank() || !isAllowedDomain(origUrl)) {
            return null
        }

        return PinterestPin(
            id = id,
            title = title,
            description = description,
            originalImageUrl = convertToOriginalsQuality(origUrl),
            thumbnailUrl = thumbUrl.ifBlank { origUrl },
            width = w,
            height = h,
            pinUrl = "https://www.pinterest.com/pin/$id/"
        )
    }

    private fun isAllowedDomain(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            host == "i.pinimg.com" || host.endsWith(".pinimg.com")
        } catch (e: Exception) {
            false
        }
    }

    private fun convertToOriginalsQuality(url: String): String {
        // Upgrade size paths like /236x/, /470x/, /564x/, /736x/, /1200x/ to /originals/
        return url.replace(Regex("""/([0-9]+x|1200x)/"""), "/originals/")
    }
}
