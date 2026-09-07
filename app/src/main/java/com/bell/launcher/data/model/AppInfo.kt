package com.bell.launcher.data.model

import kotlinx.serialization.Serializable

/** Встановлений додаток (одна launcher-активність). */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val system: Boolean = false,
) {
    val key: String get() = "$packageName/$activityName"
    fun toRef(): AppRef = AppRef(packageName, activityName, label)
}

/** Посилання на додаток, яке зберігається у розкладці. */
@Serializable
data class AppRef(
    val packageName: String,
    val activityName: String = "",
    val label: String = "",
) {
    val key: String get() = "$packageName/$activityName"
}
