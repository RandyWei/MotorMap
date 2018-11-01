package com.motorditu.motormap.activity

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.enums.NaviType
import com.motorditu.motormap.R
import kotlinx.android.synthetic.main.activity_route_navi.*


class RouteNaviActivity : AppCompatActivity() {

    private var aMapNavi: AMapNavi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_navi)
//        translucent()

        map_nav_view.onCreate(savedInstanceState)

        aMapNavi = AMapNavi.getInstance(applicationContext)
        aMapNavi?.let {
            it.setUseInnerVoice(true)
            it.setEmulatorNaviSpeed(60)
            it.startNavi(NaviType.EMULATOR)
        }

    }

    override fun onResume() {
        super.onResume()
        map_nav_view.onResume()
    }

    override fun onPause() {
        super.onPause()
        map_nav_view.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        map_nav_view.onDestroy()
        aMapNavi?.stopNavi()
    }

    private fun translucent() {

        val window = window

        //设置透明状态栏,这样才能让 ContentView 向上
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        //需要设置这个 flag 才能调用 setStatusBarColor 来设置状态栏颜色
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val mContentView = findViewById<View>(Window.ID_ANDROID_CONTENT) as ViewGroup
        val mChildView = mContentView.getChildAt(0)
        if (mChildView != null) {
            //注意不是设置 ContentView 的 FitsSystemWindows, 而是设置 ContentView 的第一个子 View . 使其不为系统 View 预留空间.
            ViewCompat.setFitsSystemWindows(mChildView, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //设置状态栏文字颜色及图标为深色
            //window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

    }


}
