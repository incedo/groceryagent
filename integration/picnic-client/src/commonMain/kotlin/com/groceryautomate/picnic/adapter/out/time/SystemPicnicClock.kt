package com.groceryautomate.picnic.adapter.out.time

import com.groceryautomate.picnic.application.port.out.PicnicClock
import kotlin.time.Clock

object SystemPicnicClock : PicnicClock {
    override fun nowIso8601(): String = Clock.System.now().toString()
}
