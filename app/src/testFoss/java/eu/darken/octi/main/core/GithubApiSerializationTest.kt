package eu.darken.octi.main.core

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.OffsetDateTime

class GithubApiSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val testRelease = GithubApi.ReleaseInfo(
        name = "v1.0.0",
        tagName = "v1.0.0",
        isPreRelease = false,
        htmlUrl = "https://github.com/d4rken-org/octi/releases/tag/v1.0.0",
        publishedAt = OffsetDateTime.parse("2024-06-15T12:00:00Z"),
        body = "Release notes here",
        assets = listOf(
            GithubApi.ReleaseInfo.Asset(
                id = 12345,
                name = "app-foss-release.apk",
                label = "FOSS APK",
                size = 5000000,
                contentType = "application/vnd.android.package-archive",
                downloadUrl = "https://github.com/d4rken-org/octi/releases/download/v1.0.0/app-foss-release.apk",
            ),
        ),
    )

    @Test
    fun `round-trip serialization`() {
        val encoded = json.encodeToString(testRelease)
        val decoded = json.decodeFromString<GithubApi.ReleaseInfo>(encoded)
        decoded shouldBe testRelease
    }

    @Test
    fun `wire format stability`() {
        val encoded = json.encodeToString(testRelease)
        encoded.toComparableJson() shouldBe """
            {
                "name": "v1.0.0",
                "tag_name": "v1.0.0",
                "prerelease": false,
                "html_url": "https://github.com/d4rken-org/octi/releases/tag/v1.0.0",
                "published_at": "2024-06-15T12:00:00Z",
                "body": "Release notes here",
                "assets": [
                    {
                        "id": 12345,
                        "name": "app-foss-release.apk",
                        "label": "FOSS APK",
                        "size": 5000000,
                        "content_type": "application/vnd.android.package-archive",
                        "browser_download_url": "https://github.com/d4rken-org/octi/releases/download/v1.0.0/app-foss-release.apk"
                    }
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `backward compatibility - deserialize GitHub API response format`() {
        val githubJson = """
            {
                "name": "Release 0.9.0",
                "tag_name": "v0.9.0",
                "prerelease": true,
                "html_url": "https://github.com/example/releases/tag/v0.9.0",
                "published_at": "2024-03-01T10:30:00Z",
                "body": "Beta release",
                "assets": []
            }
        """
        val decoded = json.decodeFromString<GithubApi.ReleaseInfo>(githubJson)
        decoded.name shouldBe "Release 0.9.0"
        decoded.tagName shouldBe "v0.9.0"
        decoded.isPreRelease shouldBe true
        decoded.assets shouldBe emptyList()
    }

    @Test
    fun `forward compatibility - unknown fields are ignored`() {
        val futureJson = """
            {
                "name": "v2.0.0",
                "tag_name": "v2.0.0",
                "prerelease": false,
                "html_url": "https://example.com",
                "published_at": "2025-01-01T00:00:00Z",
                "body": "",
                "assets": [],
                "draft": false,
                "tarball_url": "https://example.com/tarball"
            }
        """
        val decoded = json.decodeFromString<GithubApi.ReleaseInfo>(futureJson)
        decoded.name shouldBe "v2.0.0"
    }
}
