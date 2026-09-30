package com.texfi.w0y.data

/**
 * Чистая часть сверки: из трёх наборов — «было у обоих» ([base]), «здесь»
 * ([local]) и «в аккаунте» ([remote]) — получается, что куда перенести.
 * Сети и базы тут нет, поэтому правила проверяются обычным тестом.
 */
object SyncMerge {
    data class Plan(
        /** Добавлено здесь — добавить в аккаунт. */
        val pushAdd: Set<String>,
        /** Убрано здесь — убрать в аккаунте. */
        val pushRemove: Set<String>,
        /** Добавлено в аккаунте — добавить здесь. */
        val pullAdd: Set<String>,
        /** Убрано в аккаунте — убрать здесь. */
        val pullRemove: Set<String>,
    )

    /**
     * [remoteComplete] — прочитан ли список аккаунта целиком. Неполный список
     * не даёт права ни убирать здесь (трека там могло просто не оказаться
     * на прочитанных страницах), ни добавлять туда (он мог быть на
     * непрочитанных). Пустой ответ при непустом снимке читается как
     * сбой разбора, а не как «в аккаунте стёрли всё».
     */
    fun plan(base: Set<String>, local: Set<String>, remote: Set<String>, remoteComplete: Boolean): Plan {
        val trusted = remoteComplete && !(remote.isEmpty() && base.isNotEmpty())
        return Plan(
            pushAdd = if (remoteComplete) (local - base) - remote else emptySet(),
            pushRemove = (base - local) intersect remote,
            pullAdd = (remote - base) - local,
            pullRemove = if (trusted) ((base - remote) intersect local) - (local - base) else emptySet(),
        )
    }
}
