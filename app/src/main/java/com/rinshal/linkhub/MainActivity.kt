package com.rinshal.linkhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// The link list is read from this file in your GitHub repo.
private const val LIST_URL =
    "https://raw.githubusercontent.com/boy2mtv123-source/tvapkapk/main/links.json"

private const val UA =
    "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

private val LIME = 0xFFC6F135.toInt()
private val MUTED = 0xFFC8C8C8.toInt()
private const val DIM = 0x4D000000
private const val COLS = 5

class MainActivity : Activity() {
    data class Link(val name: String, val url: String, val imgsrc: String)

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

        val time = TextClock(this)
        time.format12Hour = "hh:mm a"
        time.format24Hour = "HH:mm"
        time.textSize = 56f
        time.setTextColor(Color.WHITE)
        time.typeface = Typeface.DEFAULT_BOLD
        root.addView(time)

        val date = TextClock(this)
        date.format12Hour = "EEEE, dd MMMM yyyy"
        date.format24Hour = "EEEE, dd MMMM yyyy"
        date.textSize = 18f
        date.setTextColor(MUTED)
        date.setPadding(0, 0, 0, dp(24))
        root.addView(date)

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
            val o = a.optJSONObject(i) ?: continue
            val u = o.optString("url").trim()
            if (u.isNotEmpty()) {
                out.add(
                    Link(
                        o.optString("name").trim().ifEmpty { u },
                        u,
                        o.optString("imgsrc").trim()
                    )
                )
            }
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
            Link("YouTube", "youtube.com", ""),
            Link("Google", "google.com", ""),
            Link("Wikipedia", "wikipedia.org", "")
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
        for (l in links) {
            grid.addView(tile(l, { open(l) }, { openInApp(l) }))
        }
        grid.getChildAt(focus.coerceIn(0, grid.childCount - 1))?.requestFocus()
    }

    private fun tile(l: Link, click: () -> Unit, longClick: () -> Unit): View {
        val poster = l.imgsrc.isNotEmpty()

        val t = FrameLayout(this)
        t.isFocusable = true
        t.isClickable = true
        t.background = tileBg()
        t.contentDescription = l.name
        t.setOnClickListener { click() }
        t.setOnLongClickListener { longClick(); true }

        // Site icon (or first letter) in the middle. Also the placeholder for posters.
        val box = FrameLayout(this)
        val letter = TextView(this)
        letter.text = l.name.take(1).uppercase()
        letter.textSize = 40f
        letter.setTextColor(LIME)
        letter.typeface = Typeface.DEFAULT_BOLD
        letter.gravity = Gravity.CENTER
        box.addView(letter, FrameLayout.LayoutParams(-1, -1))

        val icon = ImageView(this)
        icon.visibility = View.GONE
        icon.setPadding(dp(6), dp(6), dp(6), dp(6))
        icon.background = shape(0xFFF2F2F2.toInt(), 0x00000000, 0, 10)
        box.addView(icon, FrameLayout.LayoutParams(-1, -1))

        t.addView(box, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        loadFavicon(l.url, icon, letter)

        // Optional 3:4 poster image on top.
        if (poster) {
            val p = ImageView(this)
            p.scaleType = ImageView.ScaleType.CENTER_CROP
            p.visibility = View.GONE
            p.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
            p.clipToOutline = true
            val pl = FrameLayout.LayoutParams(-1, -1)
            pl.setMargins(dp(3), dp(3), dp(3), dp(3))
            t.addView(p, pl)
            loadPoster(l.imgsrc, p)
        }

        val lp = GridLayout.LayoutParams()
        lp.width = dp(if (poster) 120 else 150)
        lp.height = dp(if (poster) 160 else 100)
        lp.setMargins(dp(8), dp(8), dp(8), dp(8))
        lp.setGravity(Gravity.TOP)
        t.layoutParams = lp
        return t
    }

    private fun decodeScaled(b: ByteArray, w: Int, h: Int): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        return BitmapFactory.decodeByteArray(b, 0, b.size, o2)
    }

    private fun loadPoster(src: String, img: ImageView) {
        val f = File(filesDir, "img_" + Integer.toHexString(src.hashCode()) + ".bin")

        fun show(bmp: Bitmap) {
            img.setImageBitmap(bmp)
            img.visibility = View.VISIBLE
        }

        val cached = if (f.exists()) decodeScaled(f.readBytes(), 360, 480) else null
        if (cached != null) { show(cached); return }

        Thread {
            try {
                val c = URL(src).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 12000
                c.setRequestProperty("User-Agent", UA)
                val bytes = c.inputStream.use { it.readBytes() }
                val bmp = decodeScaled(bytes, 360, 480)
                if (bmp != null) {
                    f.writeBytes(bytes)
                    runOnUiThread { show(bmp) }
                }
            } catch (e: Exception) {
            }
        }.start()
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
