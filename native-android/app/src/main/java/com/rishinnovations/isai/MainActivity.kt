package com.rishinnovations.isai
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
class MainActivity:ComponentActivity(){private val launcher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){};override fun onCreate(b:Bundle?){super.onCreate(b);launcher.launch(if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO,Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE));setContent{IsaiApp()}}}