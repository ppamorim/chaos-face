package com.github.ppamorim.chaos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.view.View

class SettingsActivity: Activity() {

  val recyclerView: RecyclerView by lazy { findViewById(R.id.recycler_view) as RecyclerView }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    val colorsName = resources.getStringArray(R.array.colors_accent_name)
    val colorsAccent = resources.getIntArray(R.array.colors_accent_values)

    if (colorsName.size != colorsAccent.size) {
      throw IllegalStateException("Count of colors is different of count of names")
    }

    val colors: Array<Color> = colorsAccent
        .mapIndexed { index, value -> Color(value, colorsName[index]) }
        .toTypedArray()

    recyclerView.adapter = ColorAdapter(colors, onClickListener)

  }

  private val onClickListener = View.OnClickListener {
    (it.tag as? Int)?.let { position ->

      resources.getIntArray(R.array.colors_accent_values)[position].let {

        val intent = Intent()
        intent.putExtra(ACCENT_COLOR, it)
        intent.action = CHANGE_COLOR
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        sendBroadcast(intent)

        val sharedPref = getSharedPreferences("com.github.ppamorim.chaos", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(ACCENT_COLOR, it)
        editor.commit()

        println("notified")

      }
    }
  }

}