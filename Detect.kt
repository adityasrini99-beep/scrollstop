package app.scrollstop

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Recognises the Shorts / Reels screens.
 *
 * YouTube and Instagram don't publish these view IDs, so they can change when
 * those apps update. If counting ever stops working, these lists are the only
 * thing that needs updating.
 */
object Detect {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    /** Short key used for storage. */
    fun appFor(pkg: String): String? = when (pkg) {
        YOUTUBE -> "yt"
        INSTAGRAM -> "ig"
        else -> null
    }

    /** Any of these on screen means we are inside Shorts / Reels. */
    private val screenIds = mapOf(
        YOUTUBE to listOf(
            "$YOUTUBE:id/reel_recycler",
            "$YOUTUBE:id/reel_player_page_container",
            "$YOUTUBE:id/reel_watch_player",
            "$YOUTUBE:id/reel_progress_bar",
        ),
        INSTAGRAM to listOf(
            "$INSTAGRAM:id/clips_viewer_view_pager",
            "$INSTAGRAM:id/clips_video_container",
            "$INSTAGRAM:id/clips_viewer_container",
            "$INSTAGRAM:id/clips_media_component",
        ),
    )

    /** The vertical pager you swipe to go to the next video. */
    private val pagerIds = mapOf(
        YOUTUBE to setOf(
            "$YOUTUBE:id/reel_recycler",
            "$YOUTUBE:id/reel_player_page_container",
        ),
        INSTAGRAM to setOf(
            "$INSTAGRAM:id/clips_viewer_view_pager",
        ),
    )

    /** true / false, or null when the screen can't be read right now. */
    fun isReelScreen(root: AccessibilityNodeInfo?, pkg: String): Boolean? {
        if (root == null) return null
        for (id in screenIds[pkg].orEmpty()) {
            val found = try {
                root.findAccessibilityNodeInfosByViewId(id)
            } catch (e: Exception) {
                null
            }
            if (!found.isNullOrEmpty()) return true
        }
        return false
    }

    enum class Scroll { PAGER, FULLSCREEN_LIST, OTHER }

    /**
     * PAGER: the scroll came from the known Shorts/Reels pager.
     * FULLSCREEN_LIST: an unknown, almost full-screen vertical list (a fallback
     * in case the IDs changed); only counted when we already know we're in Reels.
     */
    fun classifyScroll(src: AccessibilityNodeInfo, pkg: String, screenHeight: Int): Scroll {
        val id = src.viewIdResourceName
        if (id != null && id in pagerIds[pkg].orEmpty()) return Scroll.PAGER
        val cls = src.className?.toString() ?: ""
        if (cls.contains("ViewPager") || cls.contains("RecyclerView")) {
            val r = Rect()
            src.getBoundsInScreen(r)
            if (r.height() >= screenHeight * 0.75) return Scroll.FULLSCREEN_LIST
        }
        return Scroll.OTHER
    }
}
