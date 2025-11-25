package com.xjyzs.schedule

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xjyzs.schedule.ui.theme.ScheduleTheme
import com.xjyzs.schedule.utils.fetchToken
import com.xjyzs.schedule.utils.parseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class MainViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)
    val courses = mutableStateListOf<JsonObject>()
    var semesterBeginAt by mutableLongStateOf(0)
    var week by mutableIntStateOf(1)
    var weekModified by mutableStateOf(false)
    var enablePageSwitchAnimation by mutableStateOf(true)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            ScheduleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainUI(
                        modifier = Modifier.padding(innerPadding),
                        viewModel
                    )
                }
            }
        }
    }
}


@Composable
fun MainUI(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var dialogExpanded by remember { mutableStateOf(false) }
    var rootDialogExpanded by remember { mutableStateOf(false) }
    var currentCourse by remember { mutableStateOf("") }
    var currentTeacher by remember { mutableStateOf("") }
    var currentClassroom by remember { mutableStateOf("") }
    var currentCredit by remember { mutableStateOf("") }
    var modifyExpanded by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf("") }
    var expireTime by remember { mutableLongStateOf(0) }
    val context = LocalContext.current
    val pref = context.getSharedPreferences("main", Context.MODE_PRIVATE)
    var authorization by remember { mutableStateOf("") }
    var load by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = viewModel.week,
        pageCount = { 2147483647 }
    )
    val formatter = DateTimeFormatter.ofPattern("MM-dd")
        .withZone(ZoneId.systemDefault())
    val formatterFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    val timeLst = listOf(
        "08:00\n08:45",
        "08:50\n09:35",
        "10:00\n10:45",
        "10:50\n11:35",
        "13:30\n14:15",
        "14:20\n15:05",
        "15:30\n16:15",
        "16:20\n17:05",
        "18:00\n18:45",
        "18:50\n19:35"
    )
    val h = 100
    val weekLst = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    fun getDetails(authorization: String) {
        try {
            val jsonBase64 = authorization.split(".")[1]
            val jsonStr = String(Base64.decode(jsonBase64, Base64.DEFAULT))
            val jsonObject = Gson().fromJson(jsonStr, JsonObject::class.java)
            id = jsonObject.get("jti").asString
            expireTime = jsonObject.get("exp").asLong
        } catch (_: Exception) {
            id = ""
            expireTime = 0
        }
    }
    LaunchedEffect(Unit) {
        try {
            val str = pref.getString("coursesJson", "")!!
            if (str.isNotEmpty()) {
                parseJson(str, viewModel, context, pref, isFromNetwork = false)
                viewModel.isLoading = false
            }
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(Unit) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            authorization = pref.getString("authorization", "")!!
            getDetails(authorization)
            if (System.currentTimeMillis() > expireTime * 1000) {
                try {
                    authorization = fetchToken(viewModel)
                } catch (e: Exception) {
                    if (e.message?.contains("denied") == true) {
                        if (!pref.getBoolean("doNotShowRootDialog", false)) {
                            rootDialogExpanded = true
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                e.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                viewModel.isLoading = false
                pref.edit {
                    putString("authorization", authorization)
                }
                getDetails(authorization)
            }
            if (authorization.length <= 10) {
                viewModel.viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "请点击右上角 + 按钮添加 authorization",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            load = !load
        }
    }
    LaunchedEffect(load) {
        if (authorization.length > 10) {
            viewModel.isLoading = true
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://wx.dlmu.edu.cn/weapp/api/v1/academic/courseTake")
                .get()
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15; 22041216C Build/AP3A.240617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36 XWEB/1160117 MMWEBSDK/20250201 MMWEBID/8832 MicroMessenger/8.0.60.2840(0x28003C40) WeChat/arm64 Weixin GPVersion/1 NetType/WIFI Language/zh_CN ABI/arm64 MiniProgramEnv/android"
                )
                .addHeader("Accept-Encoding", "gzip,compress,br,deflate")
                .addHeader(
                    "authorization",
                    authorization
                )
                .addHeader("charset", "utf-8")
                .addHeader("x-client-version", "1.4.18")
                .addHeader("content-type", "application/json")
                .addHeader(
                    "Referer",
                    "https://servicewechat.com/wx3bf7324a6ede01d3/86/page-frame.html"
                )
                .build()
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val resp = client.newCall(request).execute().body.string()
                    parseJson(resp, viewModel, context, pref, isFromNetwork = true)
                } catch (_: Exception) {
                }
                viewModel.isLoading = false
            }
        } else {
            viewModel.isLoading = false
        }
    }
    LaunchedEffect(viewModel.week) {
        if (viewModel.week != pagerState.currentPage) {
            if (viewModel.enablePageSwitchAnimation) {
                pagerState.animateScrollToPage(viewModel.week)
            }else{
                pagerState.scrollToPage(viewModel.week)
                viewModel.enablePageSwitchAnimation=true
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (viewModel.week != pagerState.currentPage) {
            viewModel.weekModified = true
            viewModel.week = pagerState.currentPage
        }
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton({
            modifyExpanded = true
        }) {
            Icon(Icons.Default.Add, null)
        }
    }
    if (dialogExpanded) {
        AlertDialog(
            onDismissRequest = { dialogExpanded = false },
            title = { Text(currentCourse) },
            text = {
                Column {
                    Text("教师: $currentTeacher")
                    Text("教室: $currentClassroom")
                    Text("学分: $currentCredit")
                }
            },
            confirmButton = {})
    }
    if (rootDialogExpanded) {
        AlertDialog(
            onDismissRequest = { rootDialogExpanded = false },
            title = { Text("从海大在线自动获取 authorization") },
            text = {
                Text("该功能需要 root 权限")
            },
            confirmButton = {
                TextButton(onClick = {
                    pref.edit {
                        putBoolean(
                            "doNotShowRootDialog",
                            true
                        )
                    };rootDialogExpanded = false
                }) { Text("不再提示") }
            })
    }
    if (modifyExpanded) {
        AlertDialog(
            onDismissRequest = { modifyExpanded = false },
            title = { Text("修改 authorization") },
            text = {
                Column {
                    OutlinedTextField(
                        authorization,
                        {
                            authorization = it
                            getDetails(authorization)
                        },
                        placeholder = { Text("Bearer") })
                    val lastRefreshTime = pref.getLong("lastRefresh", 0)
                    if (lastRefreshTime > 0) {
                        Text(
                            "上次刷新: ${
                                formatterFull.format(
                                    Instant.ofEpochMilli(lastRefreshTime)
                                )
                            }"
                        )
                    }
                    if (id.isNotEmpty()) {
                        Text("账号: $id")
                    }
                    if (expireTime > 0) {
                        Text("失效时间: ${formatterFull.format(Instant.ofEpochMilli(expireTime * 1000))}")
                    }
                }
            },
            confirmButton = {
                TextButton({
                    pref.edit {
                        putString("authorization", authorization)
                        modifyExpanded = false
                        load = !load
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton({ modifyExpanded = false }) { Text("取消") } }
        )
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton({ viewModel.week -= 1 }) {
                Text("<上一周")
            }
            Spacer(Modifier.size(36.dp))
            Text("第 ${viewModel.week} 周", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.size(36.dp))
            TextButton({ viewModel.week += 1 }) {
                Text("下一周>")
            }
            Spacer(Modifier.weight(1f))
        }
        Row {
            Spacer(Modifier.size(36.dp))
            var c = 0
            for (i in weekLst) {
                Text(
                    "$i\n${formatter.format(Instant.ofEpochMilli(((viewModel.week - 1) * 604800000L + viewModel.semesterBeginAt + c)))}",
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                c += 86400000
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row {
                Column(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    var c = 1
                    for (i in timeLst) {
                        Box(
                            modifier = Modifier.height(h.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$c\n$i",
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        c += 1
                    }
                }
                HorizontalPager(state = pagerState) { week ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(h.dp * timeLst.size)
                    ) {
                        for (i in viewModel.courses) {
                            if (i.get("startWeek").asInt <= week && i.get("endWeek").asInt >= week && (i.get(
                                    "weekStrategy"
                                ).asInt != 1 || week % 2 == 1)
                            ) {
                                Row {
                                    if (i.get("weeks").asInt != 1) {
                                        Spacer(Modifier.weight(i.get("weeks").asFloat - 1))
                                    }
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(1.dp)
                                    ) {
                                        val localCredit = i.get("credits").asFloat
                                        if (i.get("startSection").asInt != 1) {
                                            Spacer(Modifier.height(h * (i.get("startSection").asInt - 1).dp))
                                        }
                                        Spacer(Modifier.height(0.5.dp))
                                        Box(
                                            Modifier
                                                .background(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (localCredit >= 4) {
                                                        Color(0xFFFFC90E)
                                                    } else if (localCredit == 3.0f) {
                                                        Color(0xFF3487FF)
                                                    } else if (localCredit == 2.0f) {
                                                        Color(0xFF6CE647)
                                                    } else if (localCredit == 1.0f) {
                                                        Color(0xFFA4FF90)
                                                    } else {
                                                        Color(0xFFC3C3C3)
                                                    }
                                                )
                                                .clickable {
                                                    currentTeacher = i.get("teacher").asString
                                                    currentCourse = i.get("name").asString
                                                    currentClassroom = i.get("classroom").asString
                                                    currentCredit = i.get("credits").asString
                                                    dialogExpanded = true
                                                }
                                                .height(
                                                    (h - 1) * (i.get("endSection").asInt - i.get(
                                                        "startSection"
                                                    ).asInt + 1).dp
                                                )
                                        ) {
                                            Column {
                                                Text(
                                                    "${i.get("name").asString}@${i.get("classroom").asString}".toCharArray()
                                                        .joinToString("\u200B"),
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    i.get(
                                                        "teacher"
                                                    ).asString,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF666666)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(0.5.dp))
                                    }
                                    if (i.get("weeks").asInt != 7) {
                                        Spacer(Modifier.weight(7 - i.get("weeks").asFloat))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (viewModel.isLoading) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(96.dp), horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(Modifier.size(36.dp))
        }
    }
}