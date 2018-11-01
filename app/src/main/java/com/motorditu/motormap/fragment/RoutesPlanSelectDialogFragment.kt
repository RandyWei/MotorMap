package com.motorditu.motormap.fragment

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.motorditu.motormap.R
import com.motorditu.motormap.extensions.screenWidth
import kotlinx.android.synthetic.main.routes_plan_select_layout.view.*

class RoutesPlanSelectDialogFragment : DialogFragment() {

    private var x = 0
    private var y = 0

    private var congestion = true //躲避拥堵
    private var avoidhightspeed = true //不走高速
    private var cost = true //避免收费
    private var hightspeed = false //高速优先

    private var onDismissListener: ((congestion: Boolean, avoidhightspeed: Boolean, cost: Boolean, hightspeed: Boolean) -> Unit)? = null

    companion object {
        fun newInstance(x: Int, y: Int, congestion: Boolean, avoidhightspeed: Boolean, cost: Boolean, hightspeed: Boolean): RoutesPlanSelectDialogFragment {
            val routesPlanSelectDialogFragment = RoutesPlanSelectDialogFragment()
            routesPlanSelectDialogFragment.x = x
            routesPlanSelectDialogFragment.y = y
            routesPlanSelectDialogFragment.congestion = congestion
            routesPlanSelectDialogFragment.avoidhightspeed = avoidhightspeed
            routesPlanSelectDialogFragment.cost = cost
            routesPlanSelectDialogFragment.hightspeed = hightspeed
            return routesPlanSelectDialogFragment
        }
    }

    fun setOnDismissListener(onDismissListener: ((congestion: Boolean, avoidhightspeed: Boolean, cost: Boolean, hightspeed: Boolean) -> Unit)) {
        this.onDismissListener = onDismissListener
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        onDismissListener?.invoke(congestion, avoidhightspeed, cost, hightspeed)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.routes_plan_select_layout, container)
        refreshSelected(view)

        view.avoid_congestion.setOnClickListener {
            congestion = !view.avoid_congestion.isSelected
            refreshSelected(view)
        }
        view.avoid_charges.setOnClickListener {
            if (hightspeed)
                hightspeed = false
            avoidhightspeed = !view.avoid_charges.isSelected
            refreshSelected(view)
        }
        view.not_high_speed.setOnClickListener {
            if (hightspeed)
                hightspeed = false
            cost = !view.not_high_speed.isSelected
            refreshSelected(view)
        }
        view.high_speed_priority.setOnClickListener {
            if (avoidhightspeed)
                avoidhightspeed = false
            if (cost)
                cost = false
            hightspeed = !view.high_speed_priority.isSelected
            refreshSelected(view)
        }
        return view
    }

    private fun refreshSelected(view: View) {
        view.avoid_congestion.isSelected = congestion
        view.avoid_charges.isSelected = avoidhightspeed
        view.not_high_speed.isSelected = cost
        view.high_speed_priority.isSelected = hightspeed
    }

    override fun onStart() {
        super.onStart()
        context?.let { context ->
            val win = dialog.window
            // 一定要设置Background，如果不设置，window属性设置无效
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val params = win.attributes
            params.gravity = Gravity.TOP
            params.width = context.screenWidth() - 32
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.x = x
            params.y = y
            win.attributes = params
        }

    }

    interface OnDismissListener {
        fun onDismiss(congestion: Boolean, avoidhightspeed: Boolean, cost: Boolean, hightspeed: Boolean)
    }
}