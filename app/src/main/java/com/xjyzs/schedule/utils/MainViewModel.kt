package com.xjyzs.schedule.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject

class MainViewModel : ViewModel() {
    var isLoading by mutableStateOf(true)
    val courses = mutableStateListOf<JsonObject>()
    var semesterBeginAt by mutableLongStateOf(0)
    var week by mutableIntStateOf(1)
    var defaultWeek by mutableIntStateOf(1)
    var weekModified by mutableStateOf(false)
    var enablePageSwitchAnimation by mutableStateOf(false)
}

data class CourseUiModel(
    val name: String,
    val teacher: String,
    val classroom: String,
    val weeks: Int,
    val label: String,
    val weekStrategy: Int,
    val startSection: Int,
    val endSection: Int,
    val range: String,
    val id: String,
    val teacherId: String,
    val semester: String,
    val significantLevel: SignificantLevel,

    val displayText: String,
    val cardColor: Color,
    val textColor: Color
)