package sh.okx.bankoficenia.backend.plugins

import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class Extensions : AbstractExtension() {
    override fun getFilters(): MutableMap<String, Filter> {
        return mutableMapOf(
            "niceDate" to NiceDate()
        )
    }
}

class NiceDate : Filter {
    override fun getArgumentNames(): MutableList<String> {
        return mutableListOf()
    }

    override fun apply(
        input: Any?,
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int
    ): Any {
        if (input is LocalDateTime) {
            return DateTimeFormatter.RFC_1123_DATE_TIME.format(input.atOffset(ZoneOffset.UTC))
        }
        throw IllegalArgumentException("input is not a LocalDateTime!")
    }
}
