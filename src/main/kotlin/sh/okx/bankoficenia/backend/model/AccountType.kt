package sh.okx.bankoficenia.backend.model

enum class AccountType {
    // Normal debit
    DIVIDEND,
    EXPENSE,
    ASSET,

    // Normal credit
    LIABILITY,
    EQUITY,
    REVENUE,
    ;

    fun isNormalDebit(): Boolean {
        return this == DIVIDEND
                || this == EXPENSE
                || this == ASSET
    }
}