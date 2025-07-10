package com.atakmap.android.plugintemplate

import com.atakmap.android.plugintemplate.plugin.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView

class DetectionAdapter(
    private val dataSet: ArrayList<TabViewDropDown.TestFragment.DataModel>,
    mContext: Context
) : ArrayAdapter<TabViewDropDown.TestFragment.DataModel>(mContext, R.layout.raw_item, dataSet) {

    private class ViewHolder {
        lateinit var txtName: TextView
        lateinit var checkBox: CheckBox
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (view, holder) = if (convertView == null) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.raw_item, parent, false)
            val h = ViewHolder().apply {
                txtName  = v.findViewById(R.id.txtName)
                checkBox = v.findViewById(R.id.checkBox)
            }
            v.tag = h
            v to h
        } else {
            convertView to (convertView.tag as ViewHolder)
        }

        val item = getItem(position)!!
        holder.txtName.text      = item.name
        holder.checkBox.isChecked = item.checked

        // 1) usuń poprzedni listener, żeby nie dublować
        holder.checkBox.setOnCheckedChangeListener(null)

        // 2) ustaw nowy listener
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (position == 0) {
                // "Select All" — rozciągnij na całą listę
                dataSet.forEach { it.checked = isChecked }
            } else {
                // pojedyncze przełączanie
                item.checked = isChecked
                // jeśli odznaczono cokolwiek poza select all, to też odznacz selectAll
                if (!isChecked && dataSet[0].checked) {
                    dataSet[0].checked = false
                }
                // a jeśli zaznaczono wszystkie "regularne" — zaznacz Select All
                val allChecked = dataSet.drop(1).all { it.checked }
                dataSet[0].checked = allChecked
            }
            // odśwież cały adapter, żeby zmiany się pokazały
            notifyDataSetChanged()
        }

        return view
    }
}
