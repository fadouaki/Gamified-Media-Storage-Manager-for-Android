package compress.joshattic.us.swipetoclean.navigation

sealed class Route(val route: String) {
    object Splash : Route("splash")
    object Onboarding : Route("onboarding")
    object Dashboard : Route("dashboard")
    object SwipeArena : Route("swipe_arena?startIndex={startIndex}") {
        fun createRoute(startIndex: Int = 0) = "swipe_arena?startIndex=$startIndex"
    }
    object ReviewBin : Route("review_bin/{filter}") {
        fun createRoute(filter: String = "all") = "review_bin/$filter"
    }
    object CompressQueue : Route("compress_queue")
}
