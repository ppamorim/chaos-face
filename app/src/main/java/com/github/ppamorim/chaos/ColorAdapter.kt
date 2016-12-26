package com.github.ppamorim.chaos

import android.support.v7.widget.RecyclerView.Adapter
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView

class ColorAdapter(
    val colors: Array<Color>,
    val onClickListener: OnClickListener):
    Adapter<ColorViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ColorViewHolder {
    val view = LayoutInflater.from(parent?.context)
        .inflate(R.layout.adapter_settings, parent, false)
    return ColorViewHolder(view, onClickListener)
  }

  override fun onBindViewHolder(holder: ColorViewHolder?, position: Int) {
    colors.getOrNull(position)?.let {
      holder?.loadData(it)
    }
  }

  override fun getItemCount() = colors.size

}

class ColorViewHolder(itemView: View, val clickListener: OnClickListener): ViewHolder(itemView) {

  fun loadData(color: Color) {
    (itemView as? TextView)?.let {
      it.setOnClickListener(clickListener)
      it.tag = adapterPosition
      it.setBackgroundColor(color.value)
      val textColor = when (adapterPosition) {
        0, 4, 5, 6, 7, 8 -> android.graphics.Color.BLACK
        else -> android.graphics.Color.WHITE
      }
      it.setTextColor(textColor)
      it.text = color.name
    }
  }

}
