package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.SkyHanniEvent

class RawScoreboardUpdateEvent(val rawScoreboard: List<String>) : SkyHanniEvent()
