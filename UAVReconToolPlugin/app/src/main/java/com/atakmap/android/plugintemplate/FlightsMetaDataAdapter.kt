package com.atakmap.android.plugintemplate

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.plugintemplate.plugin.R

class FlightsMetaDataAdapter(
    private val items: List<TabViewDropDown.TestFragment.FlightMetaData>
) : RecyclerView.Adapter<FlightsMetaDataAdapter.MetaVH>() {

    inner class MetaVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.meta_icon)
        val keyTv: TextView   = v.findViewById(R.id.meta_key)
        val valTv: TextView   = v.findViewById(R.id.meta_value)

        @RequiresApi(Build.VERSION_CODES.M)
        fun bind(m: TabViewDropDown.TestFragment.FlightMetaData) {
            keyTv.text = m.key
            valTv.text = m.value
            icon.setImageDrawable(m.iconDrawable)
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, i: Int) = MetaVH(
        LayoutInflater.from(p.context)
            .inflate(R.layout.item_flight_meta, p, false)
    )
    override fun getItemCount() = items.size
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(h: MetaVH, pos: Int) = h.bind(items[pos])
}

