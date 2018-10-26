package com.motorditu.motormap

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.SparseArray
import android.view.*
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.fragment.app.transaction
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviViewOptions
import com.amap.api.navi.model.*
import com.amap.api.navi.view.RouteOverLay
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.DriveRouteResult
import com.autonavi.tbt.TrafficFacilityInfo
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.motorditu.motormap.adapter.TipAdapter
import com.motorditu.motormap.fragment.RouteSearchFragment
import com.motorditu.motormap.overlay.DrivingRouteOverlay
import com.motorditu.motormap.overlay.PoiOverlay
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import org.json.JSONObject


class MainActivity : AppCompatActivity(), AnkoLogger, RouteSearchFragment.RouteSearchListener {

    private var tipAdapter: TipAdapter? = null
    private var tips: MutableList<Tip>? = null
    private val ROUTE_FRAGMENT_TAG = "RouteSearchFragment"
    private var defaultWindowAttr: WindowManager.LayoutParams? = null
    private var currentLocation: Location? = null


    private var amap: AMap? = null

    /**
     * 保存当前算好的路线
     */
    private val routeOverlays = SparseArray<RouteOverLay>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        translucent()
        //在activity执行onCreate时执行mMapView.onCreate(savedInstanceState)，创建地图
        map.onCreate(savedInstanceState)//必须重写
        amap = map.map


        setLocationStyle()

        search_view.isIconified = true
        search_view.onActionViewExpanded()
        search_view.clearFocus()
        search_view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                info { "onQueryTextSubmit:$query" }

                val keyWordQuery = PoiSearch.Query(query, "", "北京")// 第一个参数表示搜索字符串，第二个参数表示poi搜索类型，第三个参数表示poi搜索区域（空字符串代表全国）
                keyWordQuery.pageSize = 10
                keyWordQuery.pageNum = 0

                val poiSearch = PoiSearch(this@MainActivity, keyWordQuery)
                poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {

                    }

                    override fun onPoiSearched(poiResult: PoiResult?, rCode: Int) {
                        if (rCode == AMapException.CODE_AMAP_SUCCESS) {
                            if (poiResult != null && poiResult.query != null) {// 搜索poi的结果
                                if (poiResult.query == keyWordQuery) {// 是否是同一条
                                    // 取得搜索到的poiitems有多少页
                                    val poiItems = poiResult.pois// 取得第一页的poiitem数据，页数从数字0开始
                                    val suggestionCities = poiResult
                                            .searchSuggestionCitys// 当搜索不到poiitem数据时，会返回含有搜索关键字的城市信息

                                    if (poiItems != null && poiItems.size > 0) {
                                        amap?.clear()// 清理之前的图标
                                        val poiOverlay = PoiOverlay(amap, poiItems)
                                        poiOverlay.removeFromMap()
                                        poiOverlay.addToMap()
                                        poiOverlay.zoomToSpan()
                                    } else if (suggestionCities != null && suggestionCities.size > 0) {
                                        //showSuggestCity(suggestionCities)
                                    } else {
                                        toast("没有找到相关数据.")
                                    }
                                }
                            } else {
                                toast("没有找到相关数据.")
                            }
                        } else {

                        }
                    }
                })
                poiSearch.searchPOIAsyn()

                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                info { "onQueryTextChange:$newText" }

                if (tips == null)
                    tips = mutableListOf()

                if (null == tipAdapter) {
                    tipAdapter = TipAdapter(tips)
                    tips_rv.layoutManager = LinearLayoutManager(this@MainActivity)
                    tips_rv.adapter = tipAdapter
                    tips_rv.addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
                }

                if (TextUtils.isEmpty(newText)) {
                    tips?.clear()
                    tipAdapter?.notifyDataSetChanged()
                    tips_cv.visibility = View.GONE
                } else {
                    val inputtipsQuery = InputtipsQuery(newText, "北京")
                    val inputtips = Inputtips(this@MainActivity, inputtipsQuery)
                    inputtips.setInputtipsListener { _tips, rCode ->
                        if (rCode == AMapException.CODE_AMAP_SUCCESS) {// 正确返回
                            info { _tips }
                            tips?.clear()
                            tips?.addAll(_tips)
                            tipAdapter?.notifyDataSetChanged()
                            tips_cv.visibility = View.VISIBLE
                        }
                    }
                    inputtips.requestInputtipsAsyn()
                }
                return false
            }
        })


        // 声明 多边形参数对象
        val polygonOptions6 = PolygonOptions()
        polygonOptions6.strokeWidth(10f) // 多边形的边框
                .strokeColor(Color.argb(100, 0, 0, 0)) // 边框颜色
                .fillColor(Color.argb(50, 255, 255, 0))   // 多边形的填充色

        "http://172.27.24.107:5000/testapi/api5".httpGet().responseJson { request, response, result ->
            when (result) {
                is Result.Success -> {
                    val arr = result.get().array()
                    for (index in 0 until arr.length()) {
                        val temp = arr[index] as JSONObject
                        val latLng = LatLng(temp.optString("latitude").toDouble(), temp.optString("longitude").toDouble())
                        // 添加 多边形的每个顶点（顺序添加）
                        polygonOptions6.add(latLng)
                    }
                    //amap.addPolygon(polygonOptions6)
                }
                is Result.Failure -> {
                    info("失败")
                }
            }
        }

        // 声明 多边形参数对象
        val polygonOptions5 = PolygonOptions()
        polygonOptions5.strokeWidth(10f) // 多边形的边框
                .strokeColor(Color.argb(100, 0, 0, 0)) // 边框颜色
                .fillColor(Color.argb(50, 100, 50, 0))   // 多边形的填充色
        "http://172.27.24.107:5000/testapi/api6".httpGet().responseJson { request, response, result ->
            when (result) {
                is Result.Success -> {
                    val arr = result.get().array()
                    for (index in 0 until arr.length()) {
                        val temp = arr[index] as JSONObject
                        val latLng = LatLng(temp.optString("latitude").toDouble(), temp.optString("longitude").toDouble())
                        // 添加 多边形的每个顶点（顺序添加）
                        polygonOptions5.add(latLng)
                    }
                    //amap.addPolygon(polygonOptions5)
                }
                is Result.Failure -> {
                    info("失败")
                }
            }
        }

        route_search_button.setOnClickListener { _ ->
            //resetStatusBar()
            val routeSearchFragment = RouteSearchFragment.newInstance()
            supportFragmentManager.transaction(allowStateLoss = true) {
                replace(R.id.fragment_container, routeSearchFragment, ROUTE_FRAGMENT_TAG)
            }
            route_search_button.hide()
            //VisibilityAwareImageButton.setVisibility
            routeSearchFragment.setRouteSearchListener(this)
            //设置默认起点为我的位置
            currentLocation?.let {
                routeSearchFragment.setFromPoint(LatLonPoint(it.latitude, it.longitude))
            }
        }

    }


    private fun showLocationButton(amap: AMap) {
        val uiSettings = amap.uiSettings
        uiSettings.isMyLocationButtonEnabled = true
    }

    private fun setLocationStyle() {
        amap?.let {
            //初始化定位蓝点样式类myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);
            // 连续定位、且将视角移动到地图中心点，定位点依照设备方向旋转，并且会跟随设备移动。（1秒1次定位）如果不设置myLocationType，默认也会执行此种模式。
            val locationStyle = MyLocationStyle()
            locationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)//定位一次，且将视角移动到地图中心点。
            locationStyle.interval(2000) //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
            it.myLocationStyle = locationStyle
            it.isMyLocationEnabled = true // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
            //设置希望展示的地图缩放级别
            val mCameraUpdate = CameraUpdateFactory.zoomTo(17.toFloat())
            it.animateCamera(mCameraUpdate)
            it.setOnMyLocationChangeListener { location ->
                //从location对象中获取经纬度信息，地址描述信息，建议拿到位置之后调用逆地理编码接口获取（获取地址描述数据章节有介绍）
                currentLocation = location
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        map.onResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideSoftInput() {
        try {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(this.currentFocus!!.windowToken, 0)
        } catch (e: Exception) {
            //e.printStackTrace();
        }
    }

    override fun onBackPressed() {
        hideSoftInput()
        var routeSearchFragment = supportFragmentManager.findFragmentByTag(ROUTE_FRAGMENT_TAG)
        if (null != routeSearchFragment) {
            routeSearchFragment = routeSearchFragment as RouteSearchFragment
            if (!routeSearchFragment.onBackPressed()) {
                routeSearchFragment.hideFragmentAnim(object : RouteSearchFragment.AnimationListener {
                    override fun onAnimationEnd(anim: Animation?) {
                        supportFragmentManager.transaction(allowStateLoss = true) {
                            remove(routeSearchFragment)
                        }
                        route_search_button.show()
                        clearRoute()
                    }
                })
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        map?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        map?.onDestroy()


    }

    override fun onDriveRouteSearched(driverRouteResult: DriveRouteResult?) {
        amap?.let { it ->
            driverRouteResult?.let { driverRouteResult ->
                val drivePath = driverRouteResult.paths[0] ?: return
                val drivingRouteOverlay = DrivingRouteOverlay(
                        it, drivePath, driverRouteResult.startPos,
                        driverRouteResult.targetPos,
                        null)
                drivingRouteOverlay.setNodeIconVisibility(false)//设置节点marker是否显示
                drivingRouteOverlay.setColorfulline(false)//是否用颜色展示交通拥堵情况，默认true
                drivingRouteOverlay.removeFromMap()
                drivingRouteOverlay.addToMap()
                drivingRouteOverlay.zoomToSpan()
            }
        }
    }

    override fun onCalculateRouteSuccess(aMapCalcRouteResult: AMapCalcRouteResult?) {

    }

    override fun onCalculateRouteSuccess(ints: IntArray, paths: HashMap<Int, AMapNaviPath>) {
        clearRoute()
        hideSoftInput()
        val routeOverlayOptions = getRouteOverlayOptions()

        for (i in ints.indices) {
            val path = paths[ints[i]]
            if (path != null) {
                drawRoutes(ints[i], path, routeOverlayOptions)
            }
        }
        val routeSearchFragment = supportFragmentManager.findFragmentByTag(ROUTE_FRAGMENT_TAG) as RouteSearchFragment
        routeSearchFragment.setRouteOverlays(routeOverlays)
    }

    private fun getRouteOverlayOptions(): RouteOverlayOptions {
        val routeOverlayOptions = RouteOverlayOptions()

        routeOverlayOptions.lineWidth = 25.toFloat()
        //设置交通状况情况良好下的纹理位图和默认情况下
        routeOverlayOptions.unknownTraffic = BitmapDescriptorFactory.fromResource(R.drawable.map_alr).bitmap
        routeOverlayOptions.smoothTraffic = BitmapDescriptorFactory.fromResource(R.drawable.map_alr).bitmap
        //设置交通状况迟缓下的纹理位图
        routeOverlayOptions.slowTraffic = BitmapDescriptorFactory.fromResource(R.drawable.map_alr_yellow).bitmap
        //设置交通状况拥堵下的纹理位图
        routeOverlayOptions.jamTraffic = BitmapDescriptorFactory.fromResource(R.drawable.map_alr_red).bitmap
        //设置交通状况非常拥堵下的纹理位图
        routeOverlayOptions.veryJamTraffic = BitmapDescriptorFactory.fromResource(R.drawable.map_alr_dark_red).bitmap
        return routeOverlayOptions
    }

    private fun drawRoutes(routeId: Int, path: AMapNaviPath, routeOverlayOptions: RouteOverlayOptions) {
        amap?.moveCamera(CameraUpdateFactory.changeTilt(0f))
        val routeOverLay = RouteOverLay(amap, path, this)

        routeOverLay.routeOverlayOptions = routeOverlayOptions
        routeOverLay.isTrafficLine = true
        routeOverLay.addToMap()

        routeOverlays.put(routeId, routeOverLay)
    }


    /**
     * 清除当前地图上算好的路线
     */
    private fun clearRoute() {
        for (i in 0 until routeOverlays.size()) {
            val routeOverlay = routeOverlays.valueAt(i)
            routeOverlay.removeFromMap()
        }
        routeOverlays.clear()
    }


    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        map.onSaveInstanceState(outState)
    }

    private fun resetStatusBar() {
        val window = window
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //设置状态栏文字颜色及图标为深色
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

    }

    private fun translucent() {

        val window = window

        defaultWindowAttr = window.attributes

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
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

    }
}
