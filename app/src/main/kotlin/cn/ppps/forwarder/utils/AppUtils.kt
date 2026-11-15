package cn.ppps.forwarder.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import cn.ppps.forwarder.App
import java.text.Collator
import java.util.Locale

data class AppInfo(
    val name: String,
    val icon: Drawable,
    val packageName: String,
    val packagePath: String,
    val versionName: String,
    val versionCode: Int,
    val isSystem: Boolean,
    val uid: Int
)

data class AppLabel(
    val name: String,
    val packageName: String
)

@SuppressLint("StaticFieldLeak")
@Suppress("DEPRECATION")
object AppUtils {

    fun getAppsInfo(): List<AppInfo> {
        val packageManager = App.context.packageManager ?: return emptyList()
        val appsInfo = mutableListOf<AppInfo>()

        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            try {
                val packageInfo = packageManager.getPackageInfo(app.packageName, 0)
                val appInfo = AppInfo(
                    app.loadLabel(packageManager).toString(),
                    app.loadIcon(packageManager),
                    app.packageName,
                    app.sourceDir,
                    packageInfo?.versionName ?: "Unknown",
                    packageInfo?.versionCode ?: 0,
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    app.uid
                )
                appsInfo.add(appInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                Log.e("AppUtils", "getAppsInfo: ${e.message}")
            }
        }

        return appsInfo
    }

    //多选列表用的轻量应用信息：只列出有启动入口的应用，且不加载图标（全量+图标在应用多的机器上明显变慢）
    fun getLaunchableAppsInfo(): List<AppLabel> {
        val packageManager = App.context.packageManager ?: return emptyList()
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labels = LinkedHashMap<String, String>()
        for (resolveInfo in packageManager.queryIntentActivities(launchIntent, 0)) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            if (labels.containsKey(packageName)) continue
            labels[packageName] = resolveInfo.loadLabel(packageManager).toString()
        }
        //按拼音排，直接按字符串排是码点序（天气、小米、应用…看着像乱序），中文用户找不到
        val collator = Collator.getInstance(Locale.CHINA)
        return labels.map { AppLabel(it.value, it.key) }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    //包名对应的应用名；取不到时原样返回包名，不凭空造名字
    fun getAppName(packageName: String): String {
        if (packageName.isBlank()) return packageName
        val packageManager = App.context.packageManager
        return try {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppUtils", "getAppName: ${e.message}")
            packageName
        }
    }

    fun getAppVersionCode(): Int {
        return getAppVersionCode(App.context.packageName)
    }

    private fun getAppVersionCode(packageName: String?): Int {
        if (packageName.isNullOrBlank()) {
            return -1
        }
        return try {
            val pm: PackageManager = App.context.packageManager
            val pi: PackageInfo = pm.getPackageInfo(packageName, 0)
            pi.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            Log.e("AppUtils", "getAppVersionCode: ${e.message}")
            -1
        }
    }

    fun getAppPackageName(): String {
        return App.context.packageName
    }

    fun getAppVersionName(): String {
        return getAppVersionName(App.context.packageName)
    }

    private fun getAppVersionName(packageName: String): String {
        if (packageName.isBlank()) {
            return ""
        }
        return try {
            val pm: PackageManager = App.context.packageManager
            val pi: PackageInfo = pm.getPackageInfo(packageName, 0)
            pi.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            Log.e("AppUtils", "getAppVersionName: ${e.message}")
            ""
        }
    }

    /*fun openApp(packageName: String) {
        val packageManager = App.context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            App.context.startActivity(intent)
        }
    }*/

}