package com.gachiga.app.domain

/**
 * 여러 차량 합산 정산. 순수 Kotlin.
 *
 * 모든 차량의 비용을 더해 전체 인원으로 나눈다. 1인 부담은 10원 단위로 내린다.
 * - 동승자는 자기가 탄 차의 운전자에게 1인 부담액을 보낸다.
 * - 운전자끼리는 "낸 돈 - 그 차 인원 몫"의 차이만 주고받는다.
 * - 10원 미만 나머지는 비용이 가장 큰 차량의 운전자가 부담한다.
 */
object GroupSettlement {

    data class Car(val label: String, val costWon: Long, val headcount: Int)

    data class CarResult(
        val label: String,
        val costWon: Long,
        val headcount: Int,
        /** 이 차에 탄 사람들이 부담할 몫의 합 */
        val shareWon: Long,
        /** 낸 돈 - 몫. 양수면 다른 차에서 받을 돈, 음수면 보낼 돈 */
        val balanceWon: Long,
    )

    data class Transfer(val from: String, val to: String, val amountWon: Long)

    data class Result(
        val totalCostWon: Long,
        val headcount: Int,
        val perPersonWon: Long,
        val cars: List<CarResult>,
        /** 운전자끼리 주고받을 금액 */
        val transfers: List<Transfer>,
    )

    fun calculate(cars: List<Car>): Result {
        require(cars.isNotEmpty()) { "at least one car" }
        require(cars.all { it.headcount >= 1 && it.costWon >= 0 }) { "invalid car" }

        val total = cars.sumOf { it.costWon }
        val people = cars.sumOf { it.headcount }
        val perPerson = total / people / 10 * 10
        val remainder = total - perPerson * people
        val remainderIndex = cars.indices.maxBy { cars[it].costWon }

        val results = cars.mapIndexed { i, c ->
            val share = perPerson * c.headcount + if (i == remainderIndex) remainder else 0
            CarResult(c.label, c.costWon, c.headcount, share, c.costWon - share)
        }

        // 보낼 차량(음수)에서 받을 차량(양수)으로 차례로 맞춘다.
        val debtors = results.filter { it.balanceWon < 0 }.map { it.label to -it.balanceWon }.toMutableList()
        val creditors = results.filter { it.balanceWon > 0 }.map { it.label to it.balanceWon }.toMutableList()
        val transfers = ArrayList<Transfer>()
        var d = 0
        var c = 0
        while (d < debtors.size && c < creditors.size) {
            val amount = minOf(debtors[d].second, creditors[c].second)
            transfers += Transfer(debtors[d].first, creditors[c].first, amount)
            debtors[d] = debtors[d].first to debtors[d].second - amount
            creditors[c] = creditors[c].first to creditors[c].second - amount
            if (debtors[d].second == 0L) d++
            if (creditors[c].second == 0L) c++
        }
        return Result(total, people, perPerson, results, transfers)
    }
}
