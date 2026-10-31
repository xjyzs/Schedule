package com.xjyzs.schedule

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xjyzs.schedule.ui.theme.ScheduleTheme
import com.xjyzs.schedule.utils.AnimatedExpandDialog
import com.xjyzs.schedule.utils.CourseDetailRow
import com.xjyzs.schedule.utils.CourseUiModel
import com.xjyzs.schedule.utils.MainViewModel
import com.xjyzs.schedule.utils.SignificantLevel
import com.xjyzs.schedule.utils.clickToExpand
import com.xjyzs.schedule.utils.fetchToken
import com.xjyzs.schedule.utils.getTokenFromServer
import com.xjyzs.schedule.utils.parseId
import com.xjyzs.schedule.utils.parseJson
import com.xjyzs.schedule.utils.uploadToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            ScheduleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainUI(
                        modifier = Modifier.padding(innerPadding), viewModel
                    )
                }
            }
        }
    }
}


@Composable
fun MainUI(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var buttonRect by remember { mutableStateOf(Rect.Zero) }
    var addButtonRect by remember { mutableStateOf(Rect.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var dialogExpanded by remember { mutableStateOf(false) }
    var rootDialogExpanded by remember { mutableStateOf(false) }

    var currentCourse by remember { mutableStateOf("") }
    var currentTeacher by remember { mutableStateOf("") }
    var currentClassroom by remember { mutableStateOf("") }
    var currentLabel by remember { mutableStateOf("") }
    var currentId by remember { mutableStateOf("") }
    var currentTeacherId by remember { mutableStateOf("") }
    var currentSemester by remember { mutableStateOf("") }

    var modifyExpanded by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf("") }
    var expireTime by remember { mutableLongStateOf(0) }
    var clientType by remember { mutableIntStateOf(0) }
    var serverUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val pref = context.getSharedPreferences("main", Context.MODE_PRIVATE)
    var authorization by remember { mutableStateOf("") }
    var load by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = viewModel.week - 1, pageCount = { 2147483647 })
    val formatter = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneId.systemDefault())
    val formatterFull =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
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
    val h = 90
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
        // 服务端同步
        try {
            clientType = pref.getInt("clientType", 0)
            serverUrl = pref.getString("serverUrl", "") ?: ""
        } catch (_: Exception) {
        }

        viewModel.viewModelScope.launch(Dispatchers.IO) {
            authorization = pref.getString("authorization", "")!!
            getDetails(authorization)
            if (System.currentTimeMillis() > expireTime * 1000) { // 已过期
                if (clientType == 2) {
                    val serverAuthorization = getTokenFromServer(serverUrl)
                    if (serverAuthorization.length > 10) authorization = serverAuthorization
                } else {
                    try {
                        authorization = fetchToken(viewModel)
                    } catch (e: Exception) {
                        if (e.message?.contains("denied") == true || e.message?.contains("run program \"su\"") == true) {
                            if (!pref.getBoolean("doNotShowRootDialog", false)) {
                                rootDialogExpanded = true
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context, e.message, Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    viewModel.isLoading = false
                }
                if (authorization != pref.getString("authorization", "")) {
                    pref.edit {
                        putString("authorization", authorization)
                    }
                    if (clientType == 1) uploadToken(authorization, serverUrl)
                }
                getDetails(authorization)
            }
            if (authorization.length <= 10) {
                viewModel.viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context, "请点击右上角 + 按钮添加 authorization", Toast.LENGTH_SHORT
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
            val request =
                Request.Builder().url("https://wx.dlmu.edu.cn/weapp/api/v1/academic/courseTake")
                    .get().addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID}; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/116.0.0.0 Mobile Safari/537.36 XWEB/1160117 MMWEBSDK/20250201 MMWEBID/8832 MicroMessenger/8.0.60.2840(0x28003C40) WeChat/arm64 Weixin GPVersion/1 NetType/WIFI Language/zh_CN ABI/arm64 MiniProgramEnv/android"
                    ).addHeader("Accept-Encoding", "gzip,compress,br,deflate").addHeader(
                        "authorization", authorization
                    ).addHeader("charset", "utf-8").addHeader("x-client-version", "1.4.18")
                    .addHeader("content-type", "application/json").addHeader(
                        "Referer", "https://servicewechat.com/wx3bf7324a6ede01d3/86/page-frame.html"
                    ).build()
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
        if (viewModel.week != pagerState.currentPage + 1) {
            if (viewModel.enablePageSwitchAnimation) {
                pagerState.animateScrollToPage(viewModel.week - 1)
            } else {
                pagerState.scrollToPage(viewModel.week - 1)
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (viewModel.week != pagerState.currentPage + 1) {
            viewModel.weekModified = true
            viewModel.week = pagerState.currentPage + 1
        }
    }
    // 获取屏幕尺寸
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootSize = it.size })
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = {
            if (viewModel.week == viewModel.defaultWeek) modifyExpanded = true
            else viewModel.week = viewModel.defaultWeek
        }, modifier = Modifier.onGloballyPositioned { coordinates ->
            addButtonRect = coordinates.boundsInRoot()
        }) {
            if (viewModel.week == viewModel.defaultWeek) Icon(Icons.Default.Add, null)
            else Icon(Icons.AutoMirrored.Filled.KeyboardReturn, null)
        }
    }
    if (rootDialogExpanded) {
        AlertDialog(
            onDismissRequest = { rootDialogExpanded = false },
            title = { Text("「推荐」从海大在线自动获取 authorization") },
            text = {
                Column {
                    Text("该功能需要 root 权限")
                    Text("需要在 3 天内成功登录过海大在线")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pref.edit {
                        putBoolean(
                            "doNotShowRootDialog", true
                        )
                    }; rootDialogExpanded = false
                }) { Text("不再提示") }
            })
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton({
                if (viewModel.week > 1) {
                    viewModel.enablePageSwitchAnimation =
                        true; viewModel.week -= 1; viewModel.weekModified = true
                }
            }) {
                Text("<上一周")
            }
            Spacer(Modifier.size(36.dp))
            Text("第 ${viewModel.week} 周", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.size(36.dp))
            TextButton({
                viewModel.enablePageSwitchAnimation =
                    true; viewModel.week += 1; viewModel.weekModified = true
            }) {
                Text("下一周>")
            }
            Spacer(Modifier.weight(1f))
        }
        Row {
            Spacer(Modifier.size(36.dp))
            var c = 0
            for (i in weekLst.indices) {
                val date =
                    formatter.format(Instant.ofEpochMilli(((viewModel.week - 1) * 604800000L + viewModel.semesterBeginAt + c)))
                Text(
                    "${weekLst[i]}\n${date}",
                    color = if (date == LocalDate.now()
                            .format(DateTimeFormatter.ofPattern("MM-dd"))
                    ) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
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
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    var c = 1
                    for (i in timeLst) {
                        Box(
                            modifier = Modifier.height(h.dp), contentAlignment = Alignment.Center
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


                val mergedCourses = remember(viewModel.courses.toList()) {
                    viewModel.courses.map { json ->
                        val id = json.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val parsed = parseId(id)
                        val name = json.get("name").asString
                        val classroom = json.get("classroom").asString
                        CourseUiModel(
                            name = name,
                            teacher = json.get("teacher").asString,
                            classroom = classroom,
                            weeks = json.get("weeks").asString.toIntOrNull() ?: 1,
                            label = json.get("label")?.takeIf { !it.isJsonNull }?.asString ?: "获取失败",
                            weekStrategy = json.get("weekStrategy")
                                ?.takeIf { !it.isJsonNull }?.asInt ?: 2,
                            startSection = json.get("startSection").asInt,
                            endSection = json.get("endSection").asInt,
                            range = json.get("range").asString,
                            id = id,
                            teacherId = parsed.teacherId ?: "获取失败",
                            semester = parsed.semester ?: "获取失败",
                            significantLevel = parsed.significantLevel ?: SignificantLevel.MEDIUM,

                            displayText = "${name}@${classroom}".replace(
                                "（", "("
                            ).replace("）", ")").toCharArray().joinToString("\u200B"),
                            cardColor = when (parsed.significantLevel?: SignificantLevel.MEDIUM) {
                                SignificantLevel.LOW -> Color(0xFFDDF4E8)
                                SignificantLevel.MEDIUM -> Color(0xFFFFE8D6)
                                SignificantLevel.HIGH -> Color(0xFFD6E4FF)
                            },
                            textColor = when (parsed.significantLevel?: SignificantLevel.MEDIUM) {
                                SignificantLevel.LOW -> Color(0xFF4D7C59)
                                SignificantLevel.MEDIUM -> Color(0xFF9C5353)
                                SignificantLevel.HIGH -> Color(0xFF2E4A6E)
                            },

                            )
                    }.groupBy { course ->
                        // 是否为同一节课
                        "${course.name}_${course.classroom}_${course.weeks}_${course.startSection}_${course.endSection}_${course.range}"
                    }.values.map { duplicates ->
                        // 合并
                        val base = duplicates.first()
                        base.copy(
                            teacher = duplicates.map { it.teacher }.distinct().joinToString(", "),
                            teacherId = duplicates.map { it.teacherId }.distinct()
                                .joinToString(", ")
                        )
                    }
                }


                HorizontalPager(state = pagerState) { pageIndex ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(h.dp * timeLst.size)
                    ) {
                        mergedCourses.forEach { i ->
                            if (i.range.getOrElse(
                                    pageIndex,
                                    { '0' }) != '0' && (i.weekStrategy == 2 || i.weekStrategy == 1 && (pageIndex + 1) % 2 == 1 || i.weekStrategy == 0 && (pageIndex + 1) % 2 == 0)
                            ) {
                                Row {
                                    if (i.weeks != 1) Spacer(Modifier.weight(i.weeks.toFloat() - 1))

                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .padding(1.dp)
                                    ) {
                                        if (i.startSection != 1) Spacer(Modifier.height(h * (i.startSection - 1).dp))
                                        Spacer(Modifier.height(0.5.dp))
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    color = i.cardColor
                                                )
                                                .clickToExpand { rect ->
                                                    currentTeacher = i.teacher
                                                    currentCourse = i.name
                                                    currentClassroom = i.classroom
                                                    currentLabel = i.label
                                                    currentId = i.id
                                                    currentTeacherId = i.teacherId
                                                    currentSemester = i.semester
                                                    buttonRect = rect
                                                    dialogExpanded = true
                                                }
                                                .height(
                                                    (h - 1) * (i.endSection - i.startSection + 1).dp
                                                )
                                                .fillMaxWidth()) {
                                            Column(
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    i.displayText,
                                                    fontSize = 14.sp,
                                                    color = i.textColor,
                                                    lineHeight = 20.sp
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        i.teacher,
                                                        fontSize = 14.sp,
                                                        color = i.textColor
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(0.5.dp))
                                    }
                                    if (i.weeks != 7) {
                                        Spacer(Modifier.weight((7 - i.weeks).toFloat()))
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
    AnimatedExpandDialog(dialogExpanded, buttonRect, rootSize, titleText = currentCourse, text = {
        SelectionContainer {
            Column {
                CourseDetailRow("教师", currentTeacher)
                CourseDetailRow("教室", currentClassroom)
                CourseDetailRow("上课周", currentLabel)
                CourseDetailRow("教师 ID", currentTeacherId, false)
                CourseDetailRow("学期", currentSemester, false)
                CourseDetailRow("ID", currentId, false)
            }
        }
    }, onDismissRequest = { dialogExpanded = false })

    AnimatedExpandDialog(
        modifyExpanded,
        addButtonRect,
        rootSize,
        onDismissRequest = { modifyExpanded = false },
        titleText = "修改 authorization",
        text = {
            Column {
                OutlinedTextField(authorization, {
                    authorization = it
                    getDetails(authorization)
                }, placeholder = { Text("Bearer") })
                val options = listOf("None", "发送端", "接收端")
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                            index = index, count = options.size
                        ), onClick = {
                            if (clientType != index) {
                                clientType = index
                                pref.edit(commit = true) {
                                    putInt("clientType", clientType)
                                }
                            }
                        }, selected = index == clientType, label = { Text(label) })
                    }
                }
                if (clientType != 0) {
                    OutlinedTextField(serverUrl, {
                        serverUrl = it
                        pref.edit {
                            putString("serverUrl", serverUrl)
                        }
                    }, placeholder = { Text("https://example.com/exp/") })
                }
                val lastRefreshTime = pref.getLong("lastRefresh", 0)
                if (lastRefreshTime > 0) {
                    Text(
                        "上次刷新: ${
                            formatterFull.format(
                                Instant.ofEpochMilli(lastRefreshTime)
                            )
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (id.isNotEmpty()) {
                    Text(
                        "账号: $id",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expireTime > 0) {
                    Text(
                        "失效时间: ${formatterFull.format(Instant.ofEpochMilli(expireTime * 1000))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        dismissButton = { TextButton({ modifyExpanded = false }) { Text("取消") } })
}
