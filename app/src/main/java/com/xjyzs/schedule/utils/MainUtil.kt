package com.xjyzs.schedule.utils

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xjyzs.schedule.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneId

fun fetchToken(viewModel: MainViewModel): String {
    viewModel.isLoading = true
    val process = ProcessBuilder("su", "-M").start()
    val outputStream = process.outputStream
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    outputStream.write("ls /data/user/0/com.tencent.mm/files/mmkv/; echo __END__\n".toByteArray())
    outputStream.flush()
    val files = reader.lineSequence()
        .takeWhile { it != "__END__" }
        .filter { it.startsWith("AppBrandMMKVStorage") && !it.endsWith("crc") }
        .toList()
    if (files.isEmpty()) {
        throw Exception("请启动海大在线")
    }
    val re =
        Regex("""https://wx.dlmu.edu.cn/weapp/redirect\?clientid=.*?&token=(?<token>.*?)&go=https""")
    var token = ""
    for (i in files) {
        outputStream.write("cat \"/data/user/0/com.tencent.mm/files/mmkv/$i\"; echo __END__\n".toByteArray())
        outputStream.flush()
        val str = readBytesUntil(process.inputStream, "__END__").toString(Charsets.US_ASCII)
        val matchResult = re.findAll(str)
        token = matchResult.lastOrNull()?.groups["token"]?.value ?: ""
    }
    return "Bearer $token"
}

private fun readBytesUntil(ins: InputStream, marker: String): ByteArray {
    val markerBytes = marker.toByteArray()
    val mLen = markerBytes.size
    val buffer = ByteArrayOutputStream()
    val window = ArrayDeque<Byte>(mLen)

    while (true) {
        val b = ins.read()
        if (b == -1) break

        buffer.write(b)
        window.addLast(b.toByte())
        if (window.size > mLen) window.removeFirst()
        if (window.size == mLen) {
            var matched = true
            var i = 0
            for (wb in window) {
                if (wb != markerBytes[i]) {
                    matched = false
                    break
                }
                i++
            }
            if (matched) {
                val arr = buffer.toByteArray()
                return arr.copyOf(arr.size - mLen)
            }
        }
    }
    return buffer.toByteArray()
}

suspend fun parseJson(
    str: String,
    viewModel: MainViewModel,
    context: Context,
    pref: SharedPreferences,
    isFromNetwork: Boolean
) {
    try {
        val gson = Gson()
        val jsonObject = gson.fromJson(str, JsonObject::class.java)
        if (jsonObject.get("code").asInt == 200) {
            if (isFromNetwork) {
                pref.edit {
                    putString("coursesJson", str)
                    putLong("lastRefresh", System.currentTimeMillis())
                }
            }
            val jsonObjectData =
                jsonObject.get("data").asJsonObject
            viewModel.semesterBeginAt = jsonObjectData.get("semesterBeginAt").asLong
            if (Instant.ofEpochMilli(viewModel.semesterBeginAt)
                    .atZone(ZoneId.systemDefault())
                    .dayOfWeek.value != 1
            ) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "semesterBeginAt 获取失败！", Toast.LENGTH_SHORT).show()
                }
            }
            if (!viewModel.weekModified) {
                viewModel.week =
                    ((System.currentTimeMillis() - viewModel.semesterBeginAt) / 86400000 / 7 + 1).toInt()
            }
            val localCourses = jsonObjectData.get("courses").asJsonArray
            for (i in localCourses) {
                viewModel.courses.add(i.asJsonObject)
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, jsonObject.get("msg").asString, Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
        }
    }
}