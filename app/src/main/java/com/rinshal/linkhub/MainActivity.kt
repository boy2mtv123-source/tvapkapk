package com.rinshal.linkhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// The link list is read from this file in your GitHub repo.
private const val LIST_URL =
    "https://raw.githubusercontent.com/boy2mtv123-source/tvapkapk/main/links.json"

private val LIME = 0xFFC6F135.toInt()
private val MUTED = 0xFFC8C8C8.toInt()
private const val DIM = 0x4D000000
private const val COLS = 5

class MainActivity : Activity() {
    data class Link(val name: String, val url: String)

    private var links: List<Link> = emptyList()
    private lateinit var grid: GridLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        links = loadCache()

        val frame = FrameLayout(this)

        val wall = ImageView(this)
        wall.setImageResource(R.drawable.wallpaper)
        wall.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(wall, FrameLayout.LayoutParams(-1, -1))

        val dim = View(this)
        dim.setBackgroundColor(DIM)
        frame.addView(dim, FrameLayout.LayoutParams(-1, -1))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(48), dp(32), dp(48), dp(24))

        val title = TextView(this)
        title.text = "Link Hub"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)

        val hint = TextView(this)
        hint.text = "OK: open   |   Hold OK: open inside app"
        hint.textSize = 13f
        hint.setTextColor(MUTED)
        hint.setPadding(0, dp(4), 0, dp(20))
        root.addView(hint)

        grid = GridLayout(this)
        grid.columnCount = COLS
        val sv = ScrollView(this)
        sv.addView(grid)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        render(0)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Download links.json in the background; update the screen only if it changed.
    private fun refresh() {
        Thread {
            try {
                val c = URL("$LIST_URL?t=${System.currentTimeMillis()}")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val text = c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                val parsed = parse(text)
                if (parsed.isNotEmpty()) {
                    runOnUiThread {
                        if (!isFinishing && parsed != links) {
                            links = parsed
                            getSharedPreferences("links", MODE_PRIVATE)
                                .edit().putString("json", text).apply()
                            render(0)
                        }
                    }
                }
            } catch (e: Exception) {
                // offline or bad JSON: keep showing the saved list
            }
        }.start()
    }

    private fun parse(text: String): List<Link> {
        val out = mutableListOf<Link>()
        val a = JSONArray(text)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val u = o.optString("url").trim()
            if (u.isNotEmpty()) out.add(Link(o.optString("name").trim().ifEmpty { u }, u))
        }
        return out
    }

    private fun loadCache(): List<Link> {
        val s = getSharedPreferences("links", MODE_PRIVATE).getString("json", null)
        if (s != null) {
            try {
                val p = parse(s)
                if (p.isNotEmpty()) return p
            } catch (e: Exception) {
            }
        }
        return listOf(
            Link("YouTube", "youtube.com"),
            Link("Google", "google.com"),
            Link("Wikipedia", "wikipedia.org")
        )
    }

    private fun fix(u: String) =
        if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"

    private fun open(l: Link) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fix(l.url))))
        } catch (e: ActivityNotFoundException) {
            openInApp(l)
        }
    }

    private fun openInApp(l: Link) {
        startActivity(Intent(this, WebActivity::class.java).putExtra("url", fix(l.url)))
    }

    private fun render(focus: Int) {
        grid.removeAllViews()
        for (i in links.indices) {
            val l = links[i]
            grid.addView(tile(l.name.take(1).uppercase(), l.name, l.url, { open(l) }, { openInApp(l) }))
        }
        grid.getChildAt(focus.coerceIn(0, grid.childCount - 1))?.requestFocus()
    }

    private fun tile(
        icon: String, label: String, url: String,
        click: () -> Unit, longClick: () -> Unit
    ): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.isFocusable = true
        t.isClickable = true
        t.background = tileBg()
        t.setOnClickListener { click() }
        t.setOnLongClickListener { longClick(); true }

        val box = FrameLayout(this)
        val letter = TextView(this)
        letter.text = icon
        letter.textSize = 34f
        letter.setTextColor(LIME)
        letter.typeface = Typeface.DEFAULT_BOLD
        letter.gravity = Gravity.CENTER
        box.addView(letter, FrameLayout.LayoutParams(-1, -1))

        val img = ImageView(this)
        img.visibility = View.GONE
        img.setPadding(dp(6), dp(6), dp(6), dp(6))
        img.background = shape(0xFFF2F2F2.toInt(), 0x00000000, 0, 10)
        box.addView(img, FrameLayout.LayoutParams(-1, -1))

        t.addView(box, LinearLayout.LayoutParams(dp(44), dp(44)))
        loadFavicon(url, img, letter)

        val tv = TextView(this)
        tv.text = label
        tv.textSize = 15f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.setPadding(dp(8), dp(6), dp(8), 0)
        t.addView(tv)

        val lp = GridLayout.LayoutParams()
        lp.width = dp(150)
        lp.height = dp(100)
        lp.setMargins(dp(8), dp(8), dp(8), dp(8))
        t.layoutParams = lp
        return t
    }

    private fun loadFavicon(url: String, img: ImageView, letter: TextView) {
        val host = Uri.parse(fix(url)).host ?: return
        val f = File(filesDir, "fav_" + host.replace(Regex("[^A-Za-z0-9.]"), "_") + ".png")

        fun show(bmp: Bitmap) {
            img.setImageBitmap(bmp)
            img.visibility = View.VISIBLE
            letter.visibility = View.GONE
        }

        if (f.exists()) {
            val cached = BitmapFactory.decodeFile(f.path)
            if (cached != null) { show(cached); return }
        }

        Thread {
            try {
                val c = URL("https://www.google.com/s2/favicons?domain=$host&sz=128")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                val bytes = c.inputStream.use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    f.writeBytes(bytes)
                    runOnUiThread { show(bmp) }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun shape(fill: Int, stroke: Int, w: Int, r: Int = 12): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        if (w > 0) g.setStroke(dp(w), stroke)
        return g
    }

    private fun tileBg(): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_focused), shape(0xCC232323.toInt(), LIME, 3))
        s.addState(intArrayOf(android.R.attr.state_pressed), shape(0xCC232323.toInt(), LIME, 3))
        s.addState(intArrayOf(), shape(0xB3101010.toInt(), 0x33FFFFFF, 1))
        return s
    }
}
