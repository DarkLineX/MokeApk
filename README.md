LSPtch源码学习


### 基本重打包功能实现

#### 0.Compose界面布局的使用

https://jetpackcompose.cn/docs/

#### 1.APP设置可读取权限文件夹

```kotlin
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    try {
        if (it.resultCode == Activity.RESULT_CANCELED) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: throw IOException("No data")
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        Log.i(TAG, "Storage directory: ${uri.path}")
    } catch (e: Exception) {
        Log.e(TAG, "Error when requesting saving directory", e)
    }
}
launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
```

#### 2.APK文件选取 不需要额外配置权限 compose 

```kotlin
val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { apks ->
        if (apks.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        Log.e(TAG,apks.toString()) //apk就是
}
    storageLauncher.launch(arrayOf("application/vnd.android.package-archive"))
```

#### 3.解析apk文件 协程的使用

runBlocking 不堵塞主线程运行协程

```kotlin
runBlocking {
            ApkRebirth.getAppInfoFromApks(apk_uri_list)
                .onSuccess {

                }
                .onFailure {

                }
        }
```



runCatching Kotlin中使用runcatching函数式处理错误

导入  implementation "androidx.documentfile:documentfile:1.0.1" 处理文件

withContext(Dispatchers.IO)  协程 Dispatchers来指定代码块所运行的线程

```kotlin
return withContext(Dispatchers.IO) {
            runCatching {
                var primary: ApplicationInfo? = null
                val splits = apks.mapNotNull { uri ->
                    
                }
                AppInfo(primary!!, label)
            }.recoverCatching { t ->
                cleanTmpApkDir()
                Log.e(TAG, "Failed to load apks", t)
                throw t
            }
        }
```