package com.miduoki.jgr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miduoki.jgr.patch.ApkPatch
import com.miduoki.jgr.ui.theme.JGRTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JGRTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background,
                ) {
                    FAB();
                }
            }
        }


    }
}



@Composable
fun FAB() {
    val context = LocalContext.current
    // rememberLauncherForActivityResult 访问activity返回
    // OpenMultipleDocuments 打开文件返回结果
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { apk_uri_list ->
        if (apk_uri_list.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        runBlocking {
            ApkInfo.getAppInfoFromApks(apk_uri_list)
                .onSuccess {
                    val apkPaths =   listOf(it.app.sourceDir) + (it.app.splitSourceDirs ?: emptyArray())
                    val outPath = jgrApp.tmpApkDir.absolutePath
                    ApkPatch()
                        .doCommandLine(apkPaths,outPath)
                }
                .onFailure {

                }
        }
    }

    Column(Modifier.padding(10.dp)) {
        ExtendedFloatingActionButton(
            text = {
                Text(text = "选择APK")
            },
            icon = {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
            },
            onClick = {
                      storageLauncher.launch(arrayOf("application/vnd.android.package-archive"))
            },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}

//@Preview(showBackground = true)
//@Composable
//fun DefaultPreview() {
//    JGRTheme {
//        FAB();
//    }
//}