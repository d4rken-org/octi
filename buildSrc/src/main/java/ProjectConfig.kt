import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import java.io.File
import java.util.Properties

object ProjectConfig {
    const val minSdk = 23
    const val compileSdk = 36
    const val targetSdk = 36
    const val packageName = "eu.darken.octi"

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val build: Int,
        val type: String,
    ) {
        val name: String
            get() = "$major.$minor.$patch-$type$build"
        val code: Int
            get() = major * 10000000 + minor * 100000 + patch * 1000 + build * 10
    }

    fun version(project: Project): Version {
        val propsPath = File(project.rootDir, "version.properties")
        require(propsPath.isFile) {
            "version.properties not found at: ${propsPath.absolutePath}"
        }
        val props = Properties().apply {
            propsPath.inputStream().use { load(it) }
        }
        fun intKey(key: String): Int {
            val raw = props.getProperty(key)
                ?: error("Missing key '$key' in ${propsPath.absolutePath}")
            if (raw.length > 1 && raw.startsWith("0")) {
                error("Key '$key' in ${propsPath.absolutePath} has leading zero: '$raw'")
            }
            return raw.toIntOrNull()
                ?: error("Key '$key' in ${propsPath.absolutePath} is not an integer: '$raw'")
        }
        fun stringKey(key: String): String {
            return props.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: error("Missing key '$key' in ${propsPath.absolutePath}")
        }
        val type = stringKey("project.versioning.type")
        require(type in setOf("rc", "beta")) {
            "Invalid 'project.versioning.type'='$type' in ${propsPath.absolutePath}, expected one of: rc, beta"
        }
        val major = intKey("project.versioning.major")
        val minor = intKey("project.versioning.minor")
        val patch = intKey("project.versioning.patch")
        val build = intKey("project.versioning.build")
        listOf("major" to major, "minor" to minor, "patch" to patch, "build" to build).forEach { (label, n) ->
            require(n in 0..99) {
                "version.properties '$label' out of range 0..99: $n (versionCode formula collapses at >=100)"
            }
        }
        return Version(
            major = major,
            minor = minor,
            patch = patch,
            build = build,
            type = type,
        )
    }
}

fun LibraryExtension.setupModuleBuildTypes() {
    buildTypes {
        debug {
            consumerProguardFiles("proguard-rules.pro")
        }
        create("beta") {
            consumerProguardFiles("proguard-rules.pro")
        }
        release {
            consumerProguardFiles("proguard-rules.pro")
        }
    }
}

fun com.android.build.api.dsl.SigningConfig.setupCredentials(
    signingPropsPath: File? = null
) {

    val keyStoreFromEnv = System.getenv("STORE_PATH")?.let { File(it) }

    if (keyStoreFromEnv?.exists() == true) {
        println("Using signing data from environment variables.")

        val missingVars = listOf("STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
            .filter { System.getenv(it).isNullOrBlank() }
        if (missingVars.isNotEmpty()) {
            println("WARNING: STORE_PATH is set but missing env vars: ${missingVars.joinToString()}")
        }

        storeFile = keyStoreFromEnv
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    } else {
        println("Using signing data from properties file: $signingPropsPath")
        val props = Properties().apply {
            signingPropsPath?.takeIf { it.canRead() }?.let { file ->
                file.inputStream().use { stream -> load(stream) }
            }
        }

        val keyStorePath = props.getProperty("release.storePath")?.let { File(it) }

        if (keyStorePath?.exists() == true) {
            storeFile = keyStorePath
            storePassword = props.getProperty("release.storePassword")
            keyAlias = props.getProperty("release.keyAlias")
            keyPassword = props.getProperty("release.keyPassword")
        } else {
            println("WARNING: No valid signing configuration found")
        }
    }
}