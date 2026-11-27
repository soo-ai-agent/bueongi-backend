package io.soo.bueongi.storage.db.safetyspot.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun File.listJsonFiles(): List<File> {
    val jsonFiles: ArrayList<File> = ArrayList()
    for (file in listFiles().orEmpty()) {
        if (file.isFile && file.name.endsWith(".json")) {
            jsonFiles.add(file)
        }
    }
    jsonFiles.sortBy { it.name }
    return jsonFiles
}

internal fun File.listCsvFiles(): List<File> {
    val csvFiles: ArrayList<File> = ArrayList()
    for (file in listFiles().orEmpty()) {
        if (file.isFile && (file.name.endsWith(".csv") || file.name.endsWith(".csv.gz"))) {
            csvFiles.add(file)
        }
    }
    csvFiles.sortBy { it.name }
    return csvFiles
}

// 쓰는 도중 서버가 죽어도 반쯤 쓴 파일이 남지 않게, 임시 파일에 다 쓴 뒤 한 번에 바꿔 끼운다.
internal fun File.writeBytesSafely(bytes: ByteArray) {
    val temp: File = File.createTempFile("$nameWithoutExtension-", ".tmp", parentFile)
    try {
        Files.write(temp.toPath(), bytes)
        Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        temp.delete()
    }
}
