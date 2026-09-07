package com.bell.launcher.theme

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Підтримка встановлених icon pack у форматі ADW/Nova (`appfilter.xml`).
 * Використовується, якщо тема вказала `icons.iconPackPackage`.
 */
class IconPackLoader(private val context: Context, val packageName: String) {

    private var res: Resources? = null
    private val componentToDrawable = HashMap<String, String>()
    private var loaded = false

    fun load(): Boolean {
        if (loaded) return res != null
        loaded = true
        res = runCatching { context.packageManager.getResourcesForApplication(packageName) }
            .onFailure { Log.w(TAG, "Icon pack $packageName недоступний") }
            .getOrNull()
        val r = res ?: return false
        runCatching { parseFromXmlResource(r) || parseFromAssets() }
            .onFailure { Log.w(TAG, "appfilter parse failed", it) }
        return true
    }

    private fun parseFromXmlResource(r: Resources): Boolean {
        val id = r.getIdentifier("appfilter", "xml", packageName)
        if (id == 0) return false
        val parser = r.getXml(id)
        parse(parser)
        return componentToDrawable.isNotEmpty()
    }

    private fun parseFromAssets(): Boolean {
        val ctx = runCatching {
            context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return false
        val stream = runCatching { ctx.assets.open("appfilter.xml") }.getOrNull() ?: return false
        stream.use {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(it, null)
            parse(parser)
        }
        return componentToDrawable.isNotEmpty()
    }

    private fun parse(parser: XmlPullParser) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    componentToDrawable[normalize(component)] = drawable
                }
            }
            event = parser.next()
        }
    }

    private fun normalize(component: String): String = component
        .removePrefix("ComponentInfo{")
        .removeSuffix("}")

    fun getIcon(packageName: String, activityName: String): Drawable? {
        if (!load()) return null
        val r = res ?: return null
        val key = "$packageName/$activityName"
        val name = componentToDrawable[key]
            ?: componentToDrawable.entries.firstOrNull { it.key.startsWith("$packageName/") }?.value
            ?: return null
        val id = r.getIdentifier(name, "drawable", this.packageName)
        if (id == 0) return null
        @Suppress("DEPRECATION")
        return runCatching { r.getDrawable(id, null) }.getOrNull()
    }

    companion object {
        private const val TAG = "IconPackLoader"

        /** Список встановлених на пристрої icon pack. */
        fun installedPacks(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            val actions = listOf(
                "org.adw.launcher.THEMES",
                "com.novalauncher.THEME",
                "com.gau.go.launcherex.theme",
            )
            val found = LinkedHashMap<String, String>()
            actions.forEach { action ->
                val intent = android.content.Intent(action)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.GET_META_DATA).forEach { info ->
                    val pkg = info.activityInfo.packageName
                    found[pkg] = info.loadLabel(pm).toString()
                }
            }
            return found.map { it.key to it.value }
        }
    }
}
