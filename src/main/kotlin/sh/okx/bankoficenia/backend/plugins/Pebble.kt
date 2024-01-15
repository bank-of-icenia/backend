package sh.okx.bankoficenia.backend.plugins

import io.pebbletemplates.pebble.attributes.AttributeResolver
import io.pebbletemplates.pebble.extension.*
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.operator.BinaryOperator
import io.pebbletemplates.pebble.operator.UnaryOperator
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import io.pebbletemplates.pebble.tokenParser.TokenParser
import java.util.*

class Extensions : Extension {
    override fun getFilters(): MutableMap<String, Filter> {
        return mutableMapOf()
    }

    override fun getTests(): MutableMap<String, Test> {
        return mutableMapOf()
    }

    override fun getFunctions(): MutableMap<String, Function> {
        return mutableMapOf(
            "randomid" to RandomId()
        )
    }

    override fun getTokenParsers(): MutableList<TokenParser> {
        return mutableListOf()
    }

    override fun getBinaryOperators(): MutableList<BinaryOperator> {
        return mutableListOf()
    }

    override fun getUnaryOperators(): MutableList<UnaryOperator> {
        return mutableListOf()
    }

    override fun getGlobalVariables(): MutableMap<String, Any> {
        return mutableMapOf()
    }

    override fun getNodeVisitors(): MutableList<NodeVisitorFactory> {
        return mutableListOf()
    }

    override fun getAttributeResolver(): MutableList<AttributeResolver> {
        return mutableListOf()
    }
}

class RandomId : Function {
    override fun getArgumentNames(): MutableList<String> {
        return mutableListOf()
    }

    override fun execute(
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int
    ): Any {
        return UUID.randomUUID().toString().replace("-", "")
    }
}
