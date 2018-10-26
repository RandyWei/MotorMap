package com.motorditu.motormap.overlay

import android.graphics.Color

import com.amap.api.maps.AMap
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.DriveStep
import com.amap.api.services.route.TMC
import com.motorditu.motormap.R
import com.motorditu.motormap.utils.AMapUtil

import java.util.ArrayList


/**
 * 导航路线图层类。
 */
class DrivingRouteOverlay
/**
 * 根据给定的参数，构造一个导航路线图层类对象。
 *
 * @param amap      地图对象。
 * @param path 导航路线规划方案。
 * @param context   当前的activity对象。
 */
(amap: AMap, private val drivePath: DrivePath?, start: LatLonPoint,
 end: LatLonPoint, private val throughPointList: List<LatLonPoint>?) : RouteOverlay() {
    private val throughPointMarkerList = ArrayList<Marker>()
    private var throughPointMarkerVisible = true
    private var tmcs: MutableList<TMC>? = null
    private var mPolylineOptions: PolylineOptions? = null
    private var mPolylineOptionscolor: PolylineOptions? = null
    private var isColorfulline = true

    /**
     * 设置路线宽度
     *
     * @param mWidth 路线宽度，取值范围：大于0
     */
    public override var routeWidth = 25f

    private var mLatLngsOfPath: MutableList<LatLng>? = null

    override val latLngBounds: LatLngBounds
        get() {
            val b = LatLngBounds.builder()
            b.include(LatLng(startPoint!!.latitude, startPoint!!.longitude))
            b.include(LatLng(endPoint!!.latitude, endPoint!!.longitude))
            if (this.throughPointList != null && this.throughPointList.isNotEmpty()) {
                for (i in this.throughPointList.indices) {
                    b.include(LatLng(
                            this.throughPointList[i].latitude,
                            this.throughPointList[i].longitude))
                }
            }
            return b.build()
        }

    private val throughPointBitDes: BitmapDescriptor
        get() = BitmapDescriptorFactory.fromResource(R.drawable.amap_through)

    fun setColorfulline(isColorfulline: Boolean) {
        this.isColorfulline = isColorfulline
    }

    init {
        mAMap = amap
        startPoint = AMapUtil.convertToLatLng(start)
        endPoint = AMapUtil.convertToLatLng(end)
    }

    /**
     * 添加驾车路线添加到地图上显示。
     */
    fun addToMap() {
        initPolylineOptions()
        try {
            if (mAMap == null) {
                return
            }

            if (routeWidth == 0f || drivePath == null) {
                return
            }
            mLatLngsOfPath = ArrayList()
            tmcs = ArrayList()
            val drivePaths = drivePath.steps
            mPolylineOptions!!.add(startPoint)
            for (step in drivePaths) {
                val latlonPoints = step.polyline
                val tmclist = step.tmCs
                tmcs!!.addAll(tmclist)
                addDrivingStationMarkers(step, convertToLatLng(latlonPoints[0]))
                for (latlonpoint in latlonPoints) {
                    mPolylineOptions!!.add(convertToLatLng(latlonpoint))
                    mLatLngsOfPath!!.add(convertToLatLng(latlonpoint))
                }
            }
            mPolylineOptions!!.add(endPoint)
            if (startMarker != null) {
                startMarker!!.remove()
                startMarker = null
            }
            if (endMarker != null) {
                endMarker!!.remove()
                endMarker = null
            }
            addStartAndEndMarker()
            addThroughPointMarker()
            if (isColorfulline && tmcs!!.size > 0) {
                colorWayUpdate(tmcs)
                showcolorPolyline()
            } else {
                textureWayUpdate(tmcs)
                showPolyline()
            }

        } catch (e: Throwable) {
            e.printStackTrace()
        }

    }

    /**
     * 初始化线段属性
     */
    private fun initPolylineOptions() {

        mPolylineOptions = null

        mPolylineOptions = PolylineOptions()
        mPolylineOptions!!.color(driveColor).width(routeWidth)

        //加入对应的颜色,使用setCustomTextureList 即表示使用多纹理；
        mPolylineOptions!!.customTexture = BitmapDescriptorFactory.fromResource(R.drawable.map_alr)

    }

    private fun showPolyline() {
        addPolyLine(mPolylineOptions)
    }

    private fun showcolorPolyline() {
        addPolyLine(mPolylineOptionscolor)

    }

    /**
     * 根据不同的路段拥堵情况展示不同的纹理
     *
     * @param tmcSection
     */
    private fun textureWayUpdate(tmcSection: List<TMC>?) {
        if (mAMap == null) {
            return
        }
        if (tmcSection == null || tmcSection.isEmpty()) {
            return
        }
        var segmentTrafficStatus: TMC
        mPolylineOptions = null
        mPolylineOptions = PolylineOptions()
        mPolylineOptions!!.color(driveColor).width(routeWidth)

        //用一个数组来存放纹理
        val texTuresList = ArrayList<BitmapDescriptor>()
        texTuresList.add(BitmapDescriptorFactory.fromResource(R.drawable.map_alr))
        texTuresList.add(BitmapDescriptorFactory.fromResource(R.drawable.map_alr_yellow))
        texTuresList.add(BitmapDescriptorFactory.fromResource(R.drawable.map_alr_red))
        texTuresList.add(BitmapDescriptorFactory.fromResource(R.drawable.map_alr_dark_red))

        //指定某一段用某个纹理，对应texTuresList的index即可, 四个点对应三段颜色
        val texIndexList = ArrayList<Int>()
        texIndexList.add(0)//对应上面的第0个纹理

        mPolylineOptions!!.add(startPoint)
        texIndexList.add(0)//对应上面的第0个纹理
        mPolylineOptions!!.add(AMapUtil.convertToLatLng(tmcSection[0].polyline[0]))
        for (i in tmcSection.indices) {
            segmentTrafficStatus = tmcSection[i]
            val bitmapDescriptor = getTures(segmentTrafficStatus.status)
            val mployline = segmentTrafficStatus.polyline
            for (j in 1 until mployline.size) {
                mPolylineOptions!!.add(AMapUtil.convertToLatLng(mployline[j]))
                texIndexList.add(bitmapDescriptor)
            }
        }
        mPolylineOptions!!.add(endPoint)
        texIndexList.add(0)//对应上面的第0个纹理
        mPolylineOptions!!.customTextureList = texTuresList
        mPolylineOptions!!.customTextureIndex = texIndexList
    }

    private fun getTures(status: String): Int {
        return when (status) {
            "畅通" -> 0
            "缓行" -> 1
            "拥堵" -> 2
            "严重拥堵" -> 3
            else -> 0
        }
    }

    /**
     * 根据不同的路段拥堵情况展示不同的颜色
     *
     * @param tmcSection
     */
    private fun colorWayUpdate(tmcSection: List<TMC>?) {
        if (mAMap == null) {
            return
        }
        if (tmcSection == null || tmcSection.isEmpty()) {
            return
        }
        var segmentTrafficStatus: TMC
        mPolylineOptionscolor = null
        mPolylineOptionscolor = PolylineOptions()
        mPolylineOptionscolor!!.width(routeWidth)
        val colorList = ArrayList<Int>()
        mPolylineOptionscolor!!.add(startPoint)
        mPolylineOptionscolor!!.add(AMapUtil.convertToLatLng(tmcSection[0].polyline[0]))
        colorList.add(driveColor)
        for (i in tmcSection.indices) {
            segmentTrafficStatus = tmcSection[i]
            val color = getColor(segmentTrafficStatus.status)
            val mployline = segmentTrafficStatus.polyline
            for (j in 1 until mployline.size) {
                mPolylineOptionscolor!!.add(AMapUtil.convertToLatLng(mployline[j]))
                colorList.add(color)
            }
        }
        mPolylineOptionscolor!!.add(endPoint)
        colorList.add(driveColor)
        mPolylineOptionscolor!!.colorValues(colorList)
    }

    private fun getColor(status: String): Int {
        return when (status) {
            "畅通" -> Color.parseColor("#00ba21")
            "缓行" -> Color.YELLOW
            "拥堵" -> Color.RED
            "严重拥堵" -> Color.parseColor("#5b0622")
            else -> Color.parseColor("#537edc")
        }
    }

    fun convertToLatLng(point: LatLonPoint): LatLng {
        return LatLng(point.latitude, point.longitude)
    }

    /**
     * @param driveStep
     * @param latLng
     */
    private fun addDrivingStationMarkers(driveStep: DriveStep, latLng: LatLng) {
        addStationMarker(MarkerOptions()
                .position(latLng)
                .title("\u65B9\u5411:" + driveStep.action
                        + "\n\u9053\u8DEF:" + driveStep.road)
                .snippet(driveStep.instruction).visible(nodeIconVisible)
                .anchor(0.5f, 0.5f).icon(driveBitmapDescriptor))
    }

    fun setThroughPointIconVisibility(visible: Boolean) {
        try {
            throughPointMarkerVisible = visible
            if (this.throughPointMarkerList.size > 0) {
                for (i in this.throughPointMarkerList.indices) {
                    this.throughPointMarkerList[i].isVisible = visible
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

    }

    private fun addThroughPointMarker() {
        if (this.throughPointList != null && this.throughPointList.isNotEmpty()) {
            var latLonPoint: LatLonPoint? = null
            for (i in this.throughPointList.indices) {
                latLonPoint = this.throughPointList[i]
                throughPointMarkerList.add(mAMap!!
                        .addMarker(MarkerOptions()
                                .position(
                                        LatLng(latLonPoint
                                                .latitude, latLonPoint
                                                .longitude))
                                .visible(throughPointMarkerVisible)
                                .icon(throughPointBitDes)
                                .title("\u9014\u7ECF\u70B9")))
            }
        }
    }

    /**
     * 去掉DriveLineOverlay上的线段和标记。
     */
    override fun removeFromMap() {
        try {
            super.removeFromMap()
            if (this.throughPointMarkerList.size > 0) {
                for (i in this.throughPointMarkerList.indices) {
                    this.throughPointMarkerList[i].remove()
                }
                this.throughPointMarkerList.clear()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

    }

    companion object {

        /**
         * 获取两点间距离
         *
         * @param start
         * @param end
         * @return
         */
        fun calculateDistance(start: LatLng, end: LatLng): Int {
            val x1 = start.longitude
            val y1 = start.latitude
            val x2 = end.longitude
            val y2 = end.latitude
            return calculateDistance(x1, y1, x2, y2)
        }

        fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Int {
            var x1 = x1
            var y1 = y1
            var x2 = x2
            var y2 = y2
            val NF_pi = 0.01745329251994329 // 弧度 PI/180
            x1 *= NF_pi
            y1 *= NF_pi
            x2 *= NF_pi
            y2 *= NF_pi
            val sinx1 = Math.sin(x1)
            val siny1 = Math.sin(y1)
            val cosx1 = Math.cos(x1)
            val cosy1 = Math.cos(y1)
            val sinx2 = Math.sin(x2)
            val siny2 = Math.sin(y2)
            val cosx2 = Math.cos(x2)
            val cosy2 = Math.cos(y2)
            val v1 = DoubleArray(3)
            v1[0] = cosy1 * cosx1 - cosy2 * cosx2
            v1[1] = cosy1 * sinx1 - cosy2 * sinx2
            v1[2] = siny1 - siny2
            val dist = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2])

            return (Math.asin(dist / 2) * 12742001.5798544).toInt()
        }


        //获取指定两点之间固定距离点
        fun getPointForDis(sPt: LatLng, ePt: LatLng, dis: Double): LatLng {
            val lSegLength = calculateDistance(sPt, ePt).toDouble()
            val preResult = dis / lSegLength
            return LatLng((ePt.latitude - sPt.latitude) * preResult + sPt.latitude, (ePt.longitude - sPt.longitude) * preResult + sPt.longitude)
        }
    }
}