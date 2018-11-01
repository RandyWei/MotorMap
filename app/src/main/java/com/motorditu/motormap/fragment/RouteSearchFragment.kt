package com.motorditu.motormap.fragment

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.PopupWindow
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.model.*
import com.amap.api.navi.view.RouteOverLay
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.services.route.*
import com.autonavi.tbt.TrafficFacilityInfo
import com.google.android.material.tabs.TabLayout
import com.motorditu.motormap.MainActivity
import com.motorditu.motormap.R
import com.motorditu.motormap.activity.RouteNaviActivity
import com.motorditu.motormap.adapter.TipAdapter
import com.motorditu.motormap.extensions.screenWidth
import kotlinx.android.synthetic.main.route_search_fragment_layout.*
import kotlinx.android.synthetic.main.routes_select_tab_item_layout.view.*
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast


class RouteSearchFragment : KBaseFragment(), RouteSearch.OnRouteSearchListener, AMapNaviListener {


    private var tips: MutableList<Tip>? = null
    private var tipAdapter: TipAdapter? = null
    private var currentEditText: EditText? = null
    private var textWatcher: TextWatcher? = null

    private var fromPoint: LatLonPoint? = null
    private var toPoint: LatLonPoint? = null

    private var routeSearchListener: RouteSearchListener? = null

    private var amapNavi: AMapNavi? = null

    //查询路线规则

    private var congestion = true //躲避拥堵
    private var avoidhightspeed = true //不走高速
    private var cost = true //避免收费
    private var hightspeed = false //高速优先

    /**
     * 路线的权值，重合路线情况下，权值高的路线会覆盖权值低的路线
     */
    private var zindex = 1

    fun setRouteSearchListener(routeSearchListener: RouteSearchListener) {
        this.routeSearchListener = routeSearchListener
    }

    fun setFromPoint(fromPoint: LatLonPoint) {
        this.fromPoint = fromPoint
    }

    companion object {
        fun newInstance(): RouteSearchFragment {
            return RouteSearchFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.route_search_fragment_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setLayoutAnimation()
        toolbar.setNavigationOnClickListener {
            (view.context as MainActivity).onBackPressed()
        }

        amapNavi = AMapNavi.getInstance(view.context)
        amapNavi?.addAMapNaviListener(this)

        //默认起点
        fromPoint?.let {
            from_edit_text.setText("我的位置")
        }

        textWatcher = TextWatcher()

        //点击起点终点设置
        from_edit_text.setOnFocusChangeListener { _, isFocus ->
            if (isFocus) {
                //tab_layout.visibility = View.GONE
                currentEditText = from_edit_text
                from_edit_text.addTextChangedListener(textWatcher)
            } else {
                //tab_layout.visibility = View.VISIBLE
                from_edit_text.removeTextChangedListener(textWatcher)
                currentEditText = null
            }
        }

        to_edit_text.setOnFocusChangeListener { _, isFocus ->
            if (isFocus) {
                //tab_layout.visibility = View.GONE
                currentEditText = to_edit_text
                to_edit_text.addTextChangedListener(textWatcher)
            } else {
                // tab_layout.visibility = View.VISIBLE
                to_edit_text.removeTextChangedListener(textWatcher)
                currentEditText = null
            }
        }

        from_edit_text.setOnEditorActionListener { textView, actionId, event ->
            searchRoute()
            true
        }
        to_edit_text.setOnEditorActionListener { textView, actionId, event ->
            searchRoute()
            true
        }

        routes_select_tab_layout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(p0: TabLayout.Tab?) {}

            override fun onTabUnselected(p0: TabLayout.Tab?) {}

            override fun onTabSelected(tab: TabLayout.Tab?) {
                changeRoute(tab?.position ?: 0)
            }
        })

        //路线计算方式按钮
        routes_plan_button.setOnClickListener {
            val location = IntArray(2)
            routes_plan_button.getLocationOnScreen(location)
            val dialog = RoutesPlanSelectDialogFragment.newInstance(0, location[1], congestion, avoidhightspeed, cost, hightspeed)
            dialog.show(fragmentManager, "routes_plan")
            dialog.setOnDismissListener { congestion, avoidhightspeed, cost, hightspeed ->
                this.congestion = congestion
                this.avoidhightspeed = avoidhightspeed
                this.cost = cost
                this.hightspeed = hightspeed

                var str = ""
                if (this.congestion)
                    str = "躲避拥堵"
                if (this.avoidhightspeed)
                    str += "不走高速"
                if (this.cost)
                    str += "避免收费"
                if (this.hightspeed)
                    str += "高速优先"

                if (this.congestion && this.avoidhightspeed && this.cost)
                    str = "智能推荐"

                str += " >"

                routes_plan_button.text = str
                searchRoute()
            }
        }

        start_nav_button.setOnClickListener {
            it.context.startActivity<RouteNaviActivity>()
        }
    }

    private fun searchRoute() {
        if (TextUtils.isEmpty(from_edit_text.editableText.toString())) {
            view!!.context.toast("请输入起点")
            return
        }
        if (TextUtils.isEmpty(to_edit_text.editableText.toString())) {
            view!!.context.toast("请输入终点")
            return
        }
        //view!!.context.toast("搜索从${from_edit_text.editableText},${fromPoint.toString()}到${to_edit_text.editableText},${toPoint.toString()}的路线")

        //导航路线计算
        val startList = mutableListOf<NaviLatLng>()
        startList.add(NaviLatLng(fromPoint?.latitude ?: 0.toDouble(), fromPoint?.longitude
                ?: 0.toDouble()))

        /**
         * 终点坐标集合［建议就一个终点］
         */
        val endList = mutableListOf<NaviLatLng>()
        endList.add(NaviLatLng(toPoint?.latitude ?: 0.toDouble(), toPoint?.longitude
                ?: 0.toDouble()))

        /**
         * 途径点坐标集合
         */
        val wayList = mutableListOf<NaviLatLng>()

        //地图路线计算
        val routeSearch = RouteSearch(view!!.context)
        routeSearch.setRouteSearchListener(this)

        val fromAndTo = RouteSearch.FromAndTo(fromPoint, toPoint)
        //0->摩托车；1->驾车；2->骑行；3->步行
        when (tab_layout.selectedTabPosition) {
            0 -> {
                /*
                 * strategyFlag转换出来的值都对应PathPlanningStrategy常量，用户也可以直接传入PathPlanningStrategy常量进行算路。
                 * 如:mAMapNavi.calculateDriveRoute(mStartList, mEndList, mWayPointList,PathPlanningStrategy.DRIVING_DEFAULT);
                 */
                /**
                 * 方法:
                 *   int strategy=mAMapNavi.strategyConvert(congestion, avoidhightspeed, cost, hightspeed, multipleroute);
                 * 参数:
                 * @congestion 躲避拥堵
                 * @avoidhightspeed 不走高速
                 * @cost 避免收费
                 * @hightspeed 高速优先
                 * @multipleroute 多路径
                 *
                 * 说明:
                 *      以上参数都是boolean类型，其中multipleroute参数表示是否多条路线，如果为true则此策略会算出多条路线。
                 * 注意:
                 *      不走高速与高速优先不能同时为true
                 *      高速优先与避免收费不能同时为true
                 */
                amapNavi?.let { amapNavi ->
                    var strategyFlag = 0
                    try {
                        strategyFlag = amapNavi.strategyConvert(congestion, avoidhightspeed, cost, hightspeed, true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (strategyFlag >= 0) {
//                        val carNumber = editText.getText().toString()
//                        val carInfo = AMapCarInfo()
//                        //设置车牌
//                        carInfo.carNumber = carNumber
//                        //设置车牌是否参与限行算路
//                        carInfo.isRestriction = true
//                        amapNavi.setCarInfo(carInfo)
                        amapNavi.calculateDriveRoute(startList, endList, wayList, strategyFlag)
                        view?.context?.toast("策略:$strategyFlag")
                    }
                }

            }
            1 -> {
                //第一个参数表示路径规划的起点和终点，第二个参数表示驾车模式，第三个参数表示途经点，第四个参数表示避让区域，第五个参数表示避让道路
                val driveRouteQuery = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DRIVING_MULTI_STRATEGY_FASTEST_SHORTEST_AVOID_CONGESTION, null, null, null)
                routeSearch.calculateDriveRouteAsyn(driveRouteQuery)
            }
            2 -> {

            }
            3 -> {

            }
        }
    }

    //---地图路线搜索回调
    override fun onDriveRouteSearched(driverRouteResult: DriveRouteResult?, errorCode: Int) {
        if (errorCode == AMapException.CODE_AMAP_SUCCESS) {
            if (driverRouteResult?.paths != null) {
                Log.i("", driverRouteResult.toString())
                routeSearchListener?.onDriveRouteSearched(driverRouteResult)
            }
        } else {
            view?.context?.toast(errorCode.toString())
        }
    }

    override fun onBusRouteSearched(busRouteResult: BusRouteResult?, errorCode: Int) {
    }

    override fun onRideRouteSearched(rideRouteResult: RideRouteResult?, errorCode: Int) {
    }

    override fun onWalkRouteSearched(walkRouteResult: WalkRouteResult?, errorCode: Int) {
    }


    //----导航路线搜索回调
    override fun onCalculateRouteSuccess(ints: IntArray?) {
        val paths = amapNavi?.naviPaths
        if (ints != null && paths != null) {
            routeSearchListener?.onCalculateRouteSuccess(ints, paths)
            showRoutesSelectLayout(ints, paths)
        } else
            view?.context?.toast("路线搜索出错")
    }

    override fun onCalculateRouteSuccess(aMapCalcRouteResult: AMapCalcRouteResult?) {
        if (aMapCalcRouteResult?.errorCode == AMapException.CODE_AMAP_SUCCESS) {
            Log.i("", aMapCalcRouteResult.toString())
            routeSearchListener?.onCalculateRouteSuccess(aMapCalcRouteResult)
        } else {
            view?.context?.toast(aMapCalcRouteResult?.errorDetail.toString())
        }
    }

    override fun onCalculateRouteFailure(errorCode: Int) {
        view?.context?.toast(errorCode.toString())
    }

    override fun onCalculateRouteFailure(aMapCalcRouteResult: AMapCalcRouteResult?) {
        view?.context?.toast(aMapCalcRouteResult?.errorCode.toString())
    }

    private var routeOverlays: SparseArray<RouteOverLay>? = null

    fun setRouteOverlays(routeOverlays: SparseArray<RouteOverLay>) {
        this.routeOverlays = routeOverlays
        changeRoute(0)
    }

    private fun changeRoute(routeIndex: Int) {
        routeOverlays?.let { routeOverlays ->
            /**
             * 计算出来的路径只有一条
             */
            if (routeOverlays.size() == 1) {
                amapNavi?.selectRouteId(routeOverlays.keyAt(0))
                return
            }

            if (routeIndex >= routeOverlays.size()) {
                return
            }
            val routeID = routeOverlays.keyAt(routeIndex)
            //突出选择的那条路
            for (i in 0 until routeOverlays.size()) {
                val key = routeOverlays.keyAt(i)
                routeOverlays.get(key).setTransparency(0.5f)
            }
            routeOverlays.get(routeID).setTransparency(1f)
            /**把用户选择的那条路的权值弄高，使路线高亮显示的同时，重合路段不会变的透明**/
            routeOverlays.get(routeID).setZindex(zindex++)

            //必须告诉AMapNavi 你最后选择的哪条路
            amapNavi?.selectRouteId(routeID)

            /**选完路径后判断路线是否是限行路线**/
            val info = amapNavi?.naviPath?.restrictionInfo
            if (!TextUtils.isEmpty(info?.restrictionTitle)) {
                view?.context?.toast(info!!.restrictionTitle)
            }
        }

    }

    private fun showRoutesSelectLayout(ints: IntArray, paths: java.util.HashMap<Int, AMapNaviPath>) {
        routes_select_tab_layout.removeAllTabs()
        for (i in ints.indices) {
            val path = paths[ints[i]]
            if (path != null) {
                LayoutInflater.from(view?.context).inflate(R.layout.routes_select_tab_item_layout, null)?.let { tabView ->
                    tabView.route_scheme_name.text = path.labels
                    tabView.route_scheme_time.text = "${path.allTime / 60}分钟"
                    tabView.route_scheme_distance.text = "${path.allLength / 1000}公里"
                    tabView.route_scheme_light_count.text = "${path.lightList.size}"
                    if (path.tollCost > 0)
                        tabView.route_scheme_money.text = "¥ ${path.tollCost}"
                    val tabItem = routes_select_tab_layout.newTab()
                    tabItem.tag = path
                    tabItem.customView = tabView
                    tabItem.view?.setPadding(2)
                    routes_select_tab_layout.addTab(tabItem)
                }
            }
        }
        routes_plan_button.visibility = View.VISIBLE
        routes_select_layout.visibility = View.VISIBLE
        routes_select_tools_layout.visibility = View.VISIBLE

    }

    private fun hideRoutesSelectLayout() {
        routes_select_layout.visibility = View.GONE
        routes_plan_button.visibility = View.GONE
        routes_select_tools_layout.visibility = View.GONE
    }

    inner class TextWatcher : android.text.TextWatcher {

        override fun afterTextChanged(editable: Editable?) {
            searchInputtips(view!!.context, editable.toString())
        }

        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

        }
    }

    private fun searchInputtips(context: Context, query: String) {

        if (TextUtils.isEmpty(query)) {
            tips?.clear()
            tipAdapter?.notifyDataSetChanged()
            recycler_view.visibility = View.GONE
            return
        }

        if (tips == null)
            tips = mutableListOf()

        if (null == tipAdapter) {
            tipAdapter = TipAdapter(tips)
            recycler_view.layoutManager = LinearLayoutManager(context)
            recycler_view.adapter = tipAdapter
            recycler_view.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            tipAdapter?.setOnItemClickListener { view, tip ->

                currentEditText?.removeTextChangedListener(textWatcher)
                currentEditText?.setText(tip.name)
                if (currentEditText == from_edit_text) {
                    fromPoint = tip.point
                } else {
                    toPoint = tip.point
                }
                currentEditText?.clearFocus()
                //让父布局获得焦点。否则在华为p10上无法返回
                app_bar.requestFocus()

                tips?.clear()
                tipAdapter?.notifyDataSetChanged()
                recycler_view.visibility = View.GONE

                //搜索路线
                searchRoute()
            }
        }

        val inputtipsQuery = InputtipsQuery(query, "北京")
        val inputtips = Inputtips(context, inputtipsQuery)
        inputtips.setInputtipsListener { _tips, rCode ->
            if (rCode == AMapException.CODE_AMAP_SUCCESS) {// 正确返回
                tips?.clear()
                tips?.addAll(_tips)
                tipAdapter?.notifyDataSetChanged()
                recycler_view.visibility = View.VISIBLE
            }
        }
        inputtips.requestInputtipsAsyn()
    }

    override fun onFragmentFirstVisible() {
        super.onFragmentFirstVisible()
    }

    override fun onFragmentVisibleChange(isVisible: Boolean) {
        super.onFragmentVisibleChange(isVisible)
    }

    override fun onBackPressed(): Boolean {
        if (from_edit_text.hasFocus()) {
            from_edit_text.clearFocus()
            //让父布局获得焦点。否则在华为p10上无法返回
            app_bar.requestFocus()
            return true
        }

        if (to_edit_text.hasFocus()) {
            to_edit_text.clearFocus()
            //让父布局获得焦点。否则在华为p10上无法返回
            app_bar.requestFocus()
            return true
        }
        return false
    }

    private fun setLayoutAnimation() {
        val animation = AnimationUtils.loadAnimation(this.context, R.anim.route_search_fragment_translate_y_to_zero)
        app_bar.animation = animation
        app_bar.animate()
    }

    fun hideFragmentAnim(animationListener: AnimationListener) {
        val animation = AnimationUtils.loadAnimation(this.context, R.anim.route_search_fragment_translate_y_to_minus)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(anim: Animation?) {

            }

            override fun onAnimationEnd(anim: Animation?) {
                animationListener.onAnimationEnd(anim)
            }

            override fun onAnimationStart(anim: Animation?) {

            }
        })
        app_bar.startAnimation(animation)
    }

    interface AnimationListener {
        fun onAnimationEnd(anim: Animation?)
    }

    interface RouteSearchListener {
        fun onDriveRouteSearched(driverRouteResult: DriveRouteResult?)
        fun onCalculateRouteSuccess(aMapCalcRouteResult: AMapCalcRouteResult?)
        fun onCalculateRouteSuccess(ints: IntArray, paths: HashMap<Int, AMapNaviPath>)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        /**
         * 当前页面只是展示地图，activity销毁后不需要再回调导航的状态
         */
        amapNavi?.removeAMapNaviListener(this)
        amapNavi?.destroy()
    }


    /**
     * ************************************************** 在算路页面，以下接口全不需要处理，在以后的版本中我们会进行优化***********************************************************************************************
     **/

    override fun onInitNaviFailure() {
    }

    override fun onInitNaviSuccess() {
    }

    override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {

    }

    override fun onServiceAreaUpdate(amapServiceAreaInfos: Array<out AMapServiceAreaInfo>?) {
    }

    override fun onEndEmulatorNavi() {
    }

    override fun onArrivedWayPoint(p0: Int) {
    }

    override fun onArriveDestination() {
    }

    override fun onPlayRing(p0: Int) {
    }

    override fun onTrafficStatusUpdate() {
    }

    override fun onGpsOpenStatus(p0: Boolean) {
    }

    override fun updateAimlessModeCongestionInfo(aimLessModeCongestionInfo: AimLessModeCongestionInfo?) {
    }

    override fun showCross(aMapNaviCross: AMapNaviCross?) {
    }

    override fun onGetNavigationText(p0: Int, p1: String?) {
    }

    override fun onGetNavigationText(p0: String?) {
    }

    override fun updateAimlessModeStatistics(aimLessModeStat: AimLessModeStat?) {
    }

    override fun hideCross() {
    }


    override fun onReCalculateRouteForTrafficJam() {
    }

    override fun updateIntervalCameraInfo(aMapNaviCameraInfo1: AMapNaviCameraInfo?, aMapNaviCameraInfo2: AMapNaviCameraInfo?, p2: Int) {
    }

    override fun hideLaneInfo() {
    }

    override fun onNaviInfoUpdated(aMapNaviInfo: AMapNaviInfo?) {
    }

    override fun showModeCross(aMapModelCross: AMapModelCross?) {
    }

    override fun updateCameraInfo(p0: Array<out AMapNaviCameraInfo>?) {
    }

    override fun hideModeCross() {
    }

    override fun onLocationChange(p0: AMapNaviLocation?) {
    }

    override fun onReCalculateRouteForYaw() {
    }

    override fun onStartNavi(p0: Int) {
    }

    override fun notifyParallelRoad(p0: Int) {
    }

    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {
    }

    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {
    }

    override fun OnUpdateTrafficFacility(p0: TrafficFacilityInfo?) {
    }

    override fun onNaviRouteNotify(p0: AMapNaviRouteNotifyData?) {
    }

    override fun showLaneInfo(p0: Array<out AMapLaneInfo>?, p1: ByteArray?, p2: ByteArray?) {
    }

    override fun showLaneInfo(p0: AMapLaneInfo?) {
    }

}