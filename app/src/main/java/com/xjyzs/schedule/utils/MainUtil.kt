package com.xjyzs.schedule.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
        for (j in matchResult) {
            token = j.groups["token"]?.value ?: ""
            val jsonBase64 = token.split(".")[1]
            val jsonStr = String(Base64.decode(jsonBase64, Base64.DEFAULT))
            val jsonObject = Gson().fromJson(jsonStr, JsonObject::class.java)
            val expireTime = jsonObject.get("exp").asLong
            if (expireTime > System.currentTimeMillis() / 1000) {
                break
            }
        }
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
                    ((System.currentTimeMillis() - viewModel.semesterBeginAt) / 604800000 + 1).toInt()
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


@Composable
fun AnimatedExpandDialog(
    showDialog: Boolean,
    buttonRect: Rect,
    rootSize: IntSize,
    onDismissRequest: () -> Unit,
    titleText: String,
    confirmButton: @Composable (() -> Unit)={},
    dismissButton: @Composable (() -> Unit)={},
    text:@Composable (()-> Unit)
) {
    BackHandler(enabled = showDialog) {
        onDismissRequest()
    }

    val animationSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    val intOffsetAnimationSpec =
        tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)

    AnimatedVisibility(
        visible = showDialog,
        enter = fadeIn(animationSpec),
        exit = fadeOut(animationSpec)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismissRequest() }
                }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showDialog,
            enter = fadeIn(animationSpec) +
                    scaleIn(initialScale = 0.1f, animationSpec = animationSpec) +
                    slideIn(
                        animationSpec = intOffsetAnimationSpec,
                        initialOffset = {
                            IntOffset(
                                x = (buttonRect.center.x - rootSize.width / 2f).toInt(),
                                y = (buttonRect.center.y - rootSize.height / 2f).toInt()
                            )
                        }
                    ),
            exit = fadeOut(animationSpec) +
                    scaleOut(targetScale = 0.1f, animationSpec = animationSpec) +
                    slideOut(
                        animationSpec = intOffsetAnimationSpec,
                        targetOffset = {
                            IntOffset(
                                x = (buttonRect.center.x - rootSize.width / 2f).toInt(),
                                y = (buttonRect.center.y - rootSize.height / 2f).toInt()
                            )
                        }
                    )
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .padding(all = 24.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
//                    Text(
//                        text = contentText,
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
                    text()
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}


fun Modifier.clickToExpand(onClick: (Rect) -> Unit): Modifier = composed {
    var myRect by remember { mutableStateOf(Rect.Zero) }

    this
        .onGloballyPositioned { coordinates ->
            myRect = coordinates.boundsInRoot()
        }
        .clickable {
            onClick(myRect)
        }
}