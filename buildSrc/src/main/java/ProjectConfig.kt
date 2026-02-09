import com.android.build.gradle.LibraryExtension
import java.io.File
import java.util.Properties

object ProjectConfig {
    const val minSdk = 23
    const val compileSdk = 36
    const val targetSdk = 36
    const val packageName = "eu.darken.octi"

    object Version {
        val versionProperties = Properties().apply {
            File("version.properties").inputStream().use { load(it) }
        }
        val major = versionProperties.getProperty("project.versioning.major").toInt()
        val minor = versionProperties.getProperty("project.versioning.minor").toInt()
        val patch = versionProperties.getProperty("project.versioning.patch").toInt()
        val build = versionProperties.getProperty("project.versioning.build").toInt()

        val name = "${major}.${minor}.${patch}-rc${build}"
        val code = major * 10000000 + minor * 100000 + patch * 1000 + build * 10
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