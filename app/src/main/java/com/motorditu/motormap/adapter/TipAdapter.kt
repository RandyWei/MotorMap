package com.motorditu.motormap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.services.help.Tip
import com.motorditu.motormap.R
import kotlinx.android.synthetic.main.tip_item_layout.view.*

class TipAdapter(private val list: MutableList<Tip>?) : RecyclerView.Adapter<TipAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipAdapter.ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tip_item_layout, parent, false))
    }

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list?.get(position)
        item?.let { tip ->
            with(holder.itemView) {
                name.text = tip.name
                address.text = tip.address
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}