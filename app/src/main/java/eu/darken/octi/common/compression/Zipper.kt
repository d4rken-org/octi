package eu.darken.octi.common.compression

import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// https://stackoverflow.com/a/48598099/1251958
class Zipper {

    @Throws(Exception::class)
    fun zip(files: Array<String>, zipFile: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            for (i in files.indices) {
                log(TAG, VERBOSE) { "Compressing ${files[i]} into $zipFile" }
                BufferedInputStream(FileInputStream(files[i]), BUFFER).use { origin ->
                    val entry = ZipEntry(files[i].substring(files[i].lastIndexOf("/") + 1))
                    out.putNextEntry(entry)
                    origin.copyTo(out)
                }
            }
            out.finish()
        }
    }

    fun zipDirectory(sourceDir: File, outputZip: File) {
        val files = sourceDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.absolutePath }
            ?.toTypedArray() ?: return
        val tmpZip = File(outputZip.parentFile, "${outputZip.name}.tmp")
        try {
            zip(files, tmpZip.absolutePath)
        } catch (e: Exception) {
            tmpZip.delete()
            throw e
        }
        if (!tmpZip.renameTo(outputZip)) {
            log(TAG, ERROR) { "Failed to rename $tmpZip to $outputZip" }
            tmpZip.delete()
            throw IOException("Failed to finalize zip file: $outputZip")
        }
    }

    companion object {
        internal val TAG = logTag("Zipper")
        const val BUFFER = 2048
    }
}