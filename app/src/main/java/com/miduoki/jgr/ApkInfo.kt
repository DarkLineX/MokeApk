package com.miduoki.jgr

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object ApkInfo {


    class AppInfo(val app: ApplicationInfo, val label: String) {

    }

    suspend fun getAppInfoFromApks (apks: List<Uri>): Result<AppInfo> {
      return withContext(Dispatchers.IO) {
          runCatching {
              var primary: ApplicationInfo? = null
              //list mapNotNull 过滤掉转换之后为null的元素
              val splits = apks.mapNotNull { uri ->

                  val src = DocumentFile.fromSingleUri(jgrApp, uri)
                      ?: throw IOException("DocumentFile is null")

                  val dst = jgrApp.tmpApkDir.resolve(src.name!!)

                  //复制到私有目录下面
                  val input = jgrApp.contentResolver.openInputStream(uri)
                      ?: throw IOException("InputStream is null")
                  input.use {
                      dst.outputStream().use { output ->
                          input.copyTo(output)
                      }
                  }

                  if (primary == null) {
                      primary = jgrApp.packageManager.getPackageArchiveInfo(
                          dst.absolutePath,
                          0
                      )?.applicationInfo
                      if (primary != null) return@mapNotNull null else {
                          Log.e("JGR_TAG","primary is null in " + dst.absolutePath)
                      }
                  } else {
                      TODO()
                  }
              }
              //获取apk名字
              val label = jgrApp.packageManager.getApplicationLabel(primary!!).toString()
              AppInfo(primary!!, label)
          }.recoverCatching { t ->
              JLOG.e( "Failed to load apks", t)
              throw t
          }
      }
    }
}