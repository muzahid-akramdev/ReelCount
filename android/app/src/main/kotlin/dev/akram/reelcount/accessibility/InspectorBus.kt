package dev.akram.reelcount.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

data class InspectorEvent(
    val wallMs: Long,
    val packageName: String,
    val eventType: String,
    val viewerActive: Boolean,
    val identity: String?,
    val lines: List<String>,
)

object InspectorBus {
    private val deque = ArrayDeque<InspectorEvent>()
    private val mutable = MutableStateFlow<List<InspectorEvent>>(emptyList())
    val events: StateFlow<List<InspectorEvent>> = mutable

    @Synchronized
    fun add(event: InspectorEvent) {
        deque.addFirst(event)
        while (deque.size > 40) deque.removeLast()
        mutable.value = deque.toList()
    }

    @Synchronized
    fun clear() {
        deque.clear()
        mutable.value = emptyList()
    }

    fun asText(events: List<InspectorEvent>): String =
        events.joinToString("\n\n") { event ->
            val header = "${event.packageName} ${event.eventType} viewer=${event.viewerActive} id=${event.identity ?: "-"}"
            header + "\n" + event.lines.joinToString("\n")
        }
}

object ServiceStatus {
    val running = MutableStateFlow(false)
}
