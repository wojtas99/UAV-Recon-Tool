package com.atakmap.android.plugintemplate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FlightsDataAdapter(
    private var items: List<FlightSession>,
    private val onClick: (FlightSession) -> Unit
) : RecyclerView.Adapter<FlightsDataAdapter.SessionVH>() {

    inner class SessionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txt: TextView = itemView.findViewById(android.R.id.text1)
        fun bind(session: FlightSession) {
            txt.text = session.id
            itemView.setOnClickListener { onClick(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return SessionVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SessionVH, position: Int) {
        holder.bind(items[position])
    }
}



