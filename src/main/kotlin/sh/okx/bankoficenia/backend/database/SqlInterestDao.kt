package sh.okx.bankoficenia.backend.database

import java.math.BigDecimal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

internal object InterestPaymentsTable : Table("interest_payments") {
    val paidOn = datetime("paid").apply {
        defaultExpression(CurrentDateTime)
        isNotNull()
    }
}

internal object AccumulatedInterestTable : Table("accumulated_interest") {
    val account = long("account").apply {
        isNotNull()
    }
    val amount = decimal("amount", 10, 4).apply {
        isNotNull()
    }
    val earnedOn = datetime("paid").apply {
        defaultExpression(CurrentDateTime)
        isNotNull()
    }
}

fun BankDao.calculateAccruedInterestFor(
    account: Long
): BigDecimal {
    val amount: BigDecimal = AccumulatedInterestTable
        .select(AccumulatedInterestTable.amount)
        .where { AccumulatedInterestTable.account eq account }
        .sumOf { it[AccumulatedInterestTable.amount] }

    if (amount != BigDecimal.ZERO) {
        AccumulatedInterestTable.deleteWhere { this.account eq account }
    }

    return amount
}
