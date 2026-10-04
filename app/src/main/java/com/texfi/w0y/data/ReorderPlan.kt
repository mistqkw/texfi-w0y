package com.texfi.w0y.data

/**
 * Какие перестановки «поставить A перед B» превращают текущий порядок в
 * нужный. Считается с конца: последний трек остаётся на месте, остальные
 * встают перед своим соседом справа, если ещё не стоят там. Без Android —
 * чтобы проверяться тестом.
 */
object ReorderPlan {
    fun moves(current: List<String>, desired: List<String>): List<Pair<String, String>> {
        val order = current.filter { it in desired.toSet() }.toMutableList()
        val target = desired.filter { it in order.toSet() }
        val result = mutableListOf<Pair<String, String>>()
        for (i in target.lastIndex - 1 downTo 0) {
            val item = target[i]
            val next = target[i + 1]
            val at = order.indexOf(item)
            val nextAt = order.indexOf(next)
            if (at + 1 == nextAt) continue
            order.removeAt(at)
            order.add(order.indexOf(next), item)
            result += item to next
        }
        return result
    }
}
