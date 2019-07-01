@file:JvmName("Installer")

package club.issizler.okyanus.installer

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.io.File
import java.io.FileOutputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.*
import kotlin.system.exitProcess


const val MC_VERSION = "1.14.3"
const val DATA_ROOT = ".okyanus"
const val FINISH_JAR = "server.jar"

val SERVER_JAR = "$DATA_ROOT${File.separator}server.jar"
val LOADER_JAR = "$DATA_ROOT${File.separator}fabric-loader.jar"
val LIBS_FOLDER = "$DATA_ROOT${File.separator}libs"

fun main() {
    if (!File(SERVER_JAR).exists()) {
        println(":: Getting Minecraft data")
        val serverUrl = getServerURL()

        println(":: Downloading the latest Minecraft server for $MC_VERSION")
        File(".okyanus").mkdir()
        download(serverUrl, SERVER_JAR)
    }

    println(":: Getting Fabric data")
    val loaderUrl = getLoaderURL()

    if (!File(LOADER_JAR).exists()) {
        println(":: Downloading the latest Fabric Loader")
        download("$loaderUrl.jar", LOADER_JAR)
    }

    println(":: Getting loader library data")
    val loaderLibs = getLoaderLibs(loaderUrl)

    println(":: Downloading the loader libraries")
    loaderLibs.forEach {
        val root = it.string("url") ?: throw Error("Invalid loader library response (no url)")
        val name = it.string("name") ?: throw Error("Invalid loader library response (no name)")
        val path = "$LIBS_FOLDER${File.separator}$name.jar"
        val url = mavenNameToPath(name)

        if (File(path).exists())
            return@forEach

        println("  => $name")
        File(LIBS_FOLDER).mkdirs()
        download("$root/$url.jar", path)
    }

    println(":: Downloading mappings")
    val root = "https://maven.fabricmc.net"
    val path = "$LIBS_FOLDER${File.separator}net.fabricmc.intermediary.$MC_VERSION.jar"
    val url = mavenNameToPath("net.fabricmc:intermediary:$MC_VERSION")
    download("$root/$url.jar", path)

    println(":: Applying libraries")
    FileSystems.newFileSystem(Paths.get(File(LOADER_JAR).toURI()), null).use { loaderFs ->
        File(LIBS_FOLDER).listFiles()?.forEach {
            println("  => $it")
            copyLibrary(it, loaderFs)
        }
    }

    println(":: Final touches")
    File("fabric-server-launcher.properties").writeText("serverJar=${SERVER_JAR.replace(File.separator, "/")}")
    File(LOADER_JAR).renameTo(File(FINISH_JAR))

    File(LIBS_FOLDER).deleteRecursively()
    File(LOADER_JAR).delete()

    println("=== The Fabric Server has successfully been installed!")
    println("=== Run the $FINISH_JAR file to start your server")
    println("=== Okyanus Installer ===")
}


private fun copyLibrary(library: File, loaderFs: FileSystem) {
    FileSystems.newFileSystem(Paths.get(library.toURI()), null).use { libFs ->
        libFs.rootDirectories.forEach { dir -> copyDir(dir, loaderFs.getPath("/")) }
    }
}

fun copyDir(from: Path, to: Path) {
    Files.walk(from).forEachOrdered {
        if (!it.toString().contains("MANIFEST.MF")) {
            try {
                Files.copy(
                    it,
                    to.relativize(it), // to.relativize(it) might break under Windows
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: NegativeArraySizeException) {
                /* ignore */
            } catch (e: FileAlreadyExistsException) {
                /* ignore */
            } catch (e: Throwable) {
                throw Error(e)
            }
        }
    }
}

fun mavenNameToPath(name: String): String {
    val spname = name.split(":")

    val path = spname[0].replace('.', '/') + "/" + spname[1] + "/" + spname[2]
    val fname = spname[1] + "-" + spname[2]

    try {
        return "$path/$fname"
    } catch (e: MalformedURLException) {
        System.err.println("Malformed library URL!")
        e.printStackTrace()
        exitProcess(1)
    }
}


fun getLoaderLibs(loaderUrl: String): List<JsonObject> {
    val obj = downloadJson("$loaderUrl.json")
    val libList = mutableListOf<JsonObject>()

    val root = obj.obj("libraries") ?: throw Error("Invalid loader library response (no libraries)")
    libList.addAll(root.array<JsonObject>("common") ?: throw Error("Invalid loader library response (no common)"))
    libList.addAll(root.array<JsonObject>("server") ?: throw Error("Invalid loader library response (no server)"))

    return libList
}

fun getLoaderURL(): String {
    val verObj = getLoaderVersionObj()

    return "https://maven.fabricmc.net/${mavenNameToPath(
        verObj.string("maven") ?: throw Error("Invalid loader API response (no maven)")
    )}"
}

fun getLoaderVersionObj(): JsonObject {
    val obj = downloadJsonArray("https://meta.fabricmc.net/v2/versions/loader")

    @Suppress("UNCHECKED_CAST")
    return (obj as JsonArray<JsonObject>).find {
        it.boolean("stable") ?: throw Error("Invalid loader API response (no stable)")
    } ?: throw Error("Invalid loader API response (no stable releases)")
}


private fun getServerURL(): String {
    val verObj = getServerVersionObj()

    val serverObj = verObj.obj("downloads")?.obj("server")
    return serverObj?.string("url")
        ?: throw Error("Invalid version data")
}

private fun getServerVersionObj(): JsonObject {
    val obj = downloadJson("https://launchermeta.mojang.com/mc/game/version_manifest.json")
    val ver = obj.array<JsonObject>("versions")?.find { it.string("id").equals(MC_VERSION) }?.string("url")
        ?: throw Error("Invalid launcher meta")

    return downloadJson(ver)
}


private fun downloadJson(url: String) = Parser.default().parse(URL(url).openStream().bufferedReader()) as JsonObject
private fun downloadJsonArray(url: String) =
    Parser.default().parse(URL(url).openStream().bufferedReader()) as JsonArray<*>

private fun download(url: String, path: String) =
    FileOutputStream(path).channel.transferFrom(Channels.newChannel(URL(url).openStream()), 0, Long.MAX_VALUE)
