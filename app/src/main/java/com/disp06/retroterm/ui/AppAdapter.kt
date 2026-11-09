package com.disp06.retroterm.ui

import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.disp06.retroterm.AppEntry

class AppAdapter(
    private val palette: Palette,
    private val s: Float,
    private val onLaunch: (AppEntry) -> Unit,
    private val onToggleFavorite: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    class Holder(val row: AppRowView) : RecyclerView.ViewHolder(row)

    private var items: List<AppEntry> = emptyList()
    private var favs: Set<String> = emptySet()

    init { setHasStableIds(true) }

    fun submit(list: List<AppEntry>, favorites: Set<String>) {
        items = list
        favs = favorites
        notifyDataSetChanged()
    }

    fun indexOf(key: String) = items.indexOfFirst { it.key == key }

    override fun getItemCount() = items.size
    override fun getItemId(position: Int) = items[position].key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = AppRowView(parent.context, s)
        row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (ROW_H * s).toInt())
        row.isClickable = true
        row.isLongClickable = true
        return Holder(row)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val e = items[position]
        h.row.bind(e, position, palette, e.key in favs)
        h.row.setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onLaunch(e) }
        h.row.setOnLongClickListener { it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onToggleFavorite(e); true }
    }

    companion object { const val ROW_H = 64f }
}
