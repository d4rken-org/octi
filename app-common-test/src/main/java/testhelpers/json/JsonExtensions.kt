package testhelpers.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.ByteString.Companion.encode
import okio.buffer
import okio.sink
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "    "
}

fun String.toComparableJson(): String {
    val element = Json.parseToJsonElement(this)
    return prettyJson.encodeToString(JsonElement.serializer(), element)
}

fun String.writeToFile(file: File) = encode().let { text ->
    require(!file.exists())
    file.parentFile?.mkdirs()
    file.createNewFile()
    file.sink().buffer().use { it.write(text) }
}
