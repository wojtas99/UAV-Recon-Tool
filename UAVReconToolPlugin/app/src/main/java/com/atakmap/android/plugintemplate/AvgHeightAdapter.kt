package com.atakmap.android.plugintemplate

import com.atakmap.android.plugintemplate.plugin.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class AvgHeightAdapter(
    private val dataSet: ArrayList<TabViewDropDown.TestFragment.AvgHeightData>,
    mContext: Context
) : ArrayAdapter<TabViewDropDown.TestFragment.AvgHeightData>(mContext, R.layout.avg_height_item_list, dataSet) {

    private class ViewHolder {
        lateinit var label: TextView
        lateinit var avg_height: TextView
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (view, holder) = if (convertView == null) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.avg_height_item_list, parent, false)
            val h = ViewHolder().apply {
                label  = v.findViewById(R.id.label)
                avg_height = v.findViewById(R.id.avg_height)
            }
            v.tag = h
            v to h
        } else {
            convertView to (convertView.tag as ViewHolder)
        }

        val item = getItem(position)!!
        holder.label.text = item.label
        holder.avg_height.text = item.height

        return view
    }
}
