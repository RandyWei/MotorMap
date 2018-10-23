package com.motorditu.motormap.fragment

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.amap.api.services.core.AMapException
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.motorditu.motormap.MainActivity
import com.motorditu.motormap.R
import com.motorditu.motormap.adapter.TipAdapter
import kotlinx.android.synthetic.main.route_search_fragment_layout.*


class RouteSearchFragment : KBaseFragment() {
    private var tips: MutableList<Tip>? = null
    private var tipAdapter: TipAdapter? = null
    private var currentEditText: EditText? = null

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

        //点击起点终点设置
        from_edit_text.setOnFocusChangeListener { _, isFocus ->
            if (isFocus) {
                currentEditText = from_edit_text
                tab_layout.visibility = View.GONE
            } else {
                currentEditText = null
                tab_layout.visibility = View.VISIBLE
            }
        }
        from_edit_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable?) {
                searchInputtips(view.context, editable.toString())
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }
        })
        to_edit_text.setOnFocusChangeListener { _, isFocus ->
            if (isFocus) {
                currentEditText = to_edit_text
                tab_layout.visibility = View.GONE
            } else {
                currentEditText = null
                tab_layout.visibility = View.VISIBLE
            }
        }
        to_edit_text.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable?) {
                searchInputtips(view.context, editable.toString())
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })
    }

    private fun searchInputtips(context: Context, query: String) {
        if (tips == null)
            tips = mutableListOf()

        if (null == tipAdapter) {
            tipAdapter = TipAdapter(tips)
            recycler_view.layoutManager = LinearLayoutManager(context)
            recycler_view.adapter = tipAdapter
            recycler_view.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            tipAdapter?.setOnItemClickListener { view, tip ->
                currentEditText?.setText(tip.name)
                currentEditText?.clearFocus()
                //让父布局获得焦点。否则在华为p10上无法返回
                app_bar.requestFocus()
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


}