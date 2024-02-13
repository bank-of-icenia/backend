package sh.okx.bankoficenia.backend

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * This is functionally an [apply] block that can return, and the return type can be different.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T, R> T.then(
    block: T.() -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block()
}
