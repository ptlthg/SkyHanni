package at.hannibal2.skyhanni.utils.collection

import java.util.Collections
import java.util.EnumMap
import java.util.PriorityQueue
import java.util.Queue
import java.util.WeakHashMap
import kotlin.math.ceil

object CollectionUtils {

    inline fun <reified T : Queue<E>, reified E> T.drainForEach(action: (E) -> Unit): T {
        while (true) action(this.poll() ?: break)
        return this
    }

    inline fun <reified E, reified L : MutableCollection<E>> Queue<E>.drainTo(list: L): L {
        while (true) list.add(this.poll() ?: break)
        return list
    }

    // Let garbage collector handle the removal of entries in this list
    fun <T> weakReferenceList(): MutableSet<T> = Collections.newSetFromMap(WeakHashMap<T, Boolean>())

    fun <T> MutableCollection<T>.filterToMutable(predicate: (T) -> Boolean) = filterTo(mutableListOf(), predicate)

    fun <T> List<T>.indexOfFirst(vararg args: T) = args.map { indexOf(it) }.firstOrNull { it != -1 }

    infix fun <K, V> MutableMap<K, V>.put(pairs: Pair<K, V>) {
        this[pairs.first] = pairs.second
    }

    // Taken and modified from Skytils
    @JvmStatic
    fun <T> T?.equalsOneOf(vararg other: T): Boolean {
        for (obj in other) {
            if (this == obj) return true
        }
        return false
    }

    fun <E> List<E>.getOrNull(index: Int): E? {
        return if (index in indices) {
            get(index)
        } else null
    }

    fun <T : Any> T?.toSingletonListOrEmpty(): List<T> {
        if (this == null) return emptyList()
        return listOf(this)
    }

    fun <K> MutableMap<K, Int>.addOrPut(key: K, number: Int): Int =
        this.merge(key, number, Int::plus)!! // Never returns null since "plus" can't return null

    fun <K> MutableMap<K, Long>.addOrPut(key: K, number: Long): Long =
        this.merge(key, number, Long::plus)!! // Never returns null since "plus" can't return null

    fun <K> MutableMap<K, Double>.addOrPut(key: K, number: Double): Double =
        this.merge(key, number, Double::plus)!! // Never returns null since "plus" can't return null

    fun <K> MutableMap<K, Float>.addOrPut(key: K, number: Float): Float =
        this.merge(key, number, Float::plus)!! // Never returns null since "plus" can't return null

    fun <K, N : Number> Map<K, N>.sumAllValues(): Double {
        if (values.isEmpty()) return 0.0

        return when (values.first()) {
            is Double -> values.sumOf { it.toDouble() }
            is Float -> values.sumOf { it.toDouble() }
            is Long -> values.sumOf { it.toLong() }.toDouble()
            else -> values.sumOf { it.toInt() }.toDouble()
        }
    }

    fun <K, V : Number> List<Map<K, V>>.sumByKey(): Map<K, Double> =
        flatMap { it.entries }.groupBy({ it.key }, { it.value.toDouble() }).mapValues { (_, values) -> values.sum() }

    fun <T, R> Sequence<IndexedValue<T>>.runningIndexedFold(initial: R, operation: (R, T) -> R): Sequence<IndexedValue<R>> =
        map { it.value }.runningFold(initial, operation).zip(map { it.index }) { value, index -> IndexedValue(index, value) }

    fun <T : Any> Sequence<T>.firstTwiceOf(a: (T) -> Boolean, b: (T) -> Boolean): Pair<T?, T?> {
        var firstA: T? = null
        var firstB: T? = null

        for (item in this) {
            if (firstA == null && a(item)) firstA = item
            if (firstB == null && b(item)) firstB = item
            if (firstA != null && firstB != null) break
        }
        return Pair(firstA, firstB)
    }

    /** Returns a map containing the count of occurrences of each distinct result of the [selector] function. */
    inline fun <T, K> Iterable<T>.countBy(selector: (T) -> K): Map<K, Int> {
        val map = mutableMapOf<K, Int>()
        for (item in this) {
            val key = selector(item)
            map.addOrPut(key, 1)
        }
        return map
    }

    fun List<String>.nextAfter(after: String, skip: Int = 1) = nextAfter({ it == after }, skip)

    fun List<String>.nextAfter(after: (String) -> Boolean, skip: Int = 1): String? {
        var missing = -1
        for (line in this) {
            if (after(line)) {
                missing = skip - 1
                continue
            }
            if (missing == 0) {
                return line
            }
            if (missing != -1) {
                missing--
            }
        }
        return null
    }

    /**
     * Returns a sublist of this list, starting after the first occurrence of the specified element.
     *
     * @param after The element after which the sublist should start.
     * @param skip The number of elements to skip after the occurrence of `after` (default is 1).
     * @param amount The number of elements to include in the returned sublist (default is 1).
     * @return A list containing up to `amount` elements starting `skip` elements after the first occurrence of `after`,
     *         or an empty list if `after` is not found.
     */
    fun List<String>.sublistAfter(after: String, skip: Int = 1, amount: Int = 1): List<String> {
        val startIndex = indexOf(after)
        if (startIndex == -1) return emptyList()

        return this.drop(startIndex + skip).take(amount)
    }

    inline fun <reified T, reified K : MutableList<T>> K.transformAt(index: Int, transform: T.() -> T): K {
        this[index] = transform(this[index])
        return this
    }

    fun <T> MutableList<T>.addNotNull(element: T?) = element?.let { add(it) }

    fun <T> MutableList<T>.addAll(vararg elements: T) = addAll(elements)

    @Deprecated("use ConcurrentLinkedQueue or Mutex-like alternates", ReplaceWith(""))
    fun <K, V> Map<K, V>.editCopy(function: MutableMap<K, V>.() -> Unit) = toMutableMap().also { function(it) }.toMap()

    @Deprecated("use ConcurrentLinkedQueue or Mutex-like alternates", ReplaceWith(""))
    fun <T> List<T>.editCopy(function: MutableList<T>.() -> Unit) = toMutableList().also { function(it) }.toList()

    fun <K, V> Map<K, V>.moveEntryToTop(matcher: (Map.Entry<K, V>) -> Boolean): Map<K, V> {
        val entry = entries.find(matcher)
        if (entry != null) {
            val newMap = linkedMapOf(entry.key to entry.value)
            newMap.putAll(this)
            return newMap
        }
        return this
    }

    operator fun IntRange.contains(range: IntRange): Boolean = range.first in this && range.last in this

    fun <K, V : Comparable<V>> List<Pair<K, V>>.sorted(): List<Pair<K, V>> {
        return sortedBy { (_, value) -> value }
    }

    fun <K, V : Comparable<V>> Map<K, V>.sorted(): Map<K, V> {
        return toList().sorted().toMap()
    }

    fun <K, V : Comparable<V>> Map<K, V>.sortedDesc(): Map<K, V> {
        return toList().sorted().reversed().toMap()
    }

    fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean) = sequence {
        with(iterator()) {
            while (hasNext()) {
                val next = next()
                yield(next)
                if (!predicate(next)) break
            }
        }
    }

    inline fun <T, R> Iterator<T>.consumeWhile(block: (T) -> R): R? {
        while (hasNext()) {
            return block(next()) ?: continue
        }
        return null
    }

    inline fun <T> Iterator<T>.collectWhile(block: (T) -> Boolean): List<T> {
        return collectWhileTo(mutableListOf(), block)
    }

    inline fun <T, C : MutableCollection<T>> Iterator<T>.collectWhileTo(collection: C, block: (T) -> Boolean): C {
        while (hasNext()) {
            val element = next()
            if (block(element)) {
                collection.add(element)
            } else {
                break
            }
        }
        return collection
    }

    /** Updates a value if it is present in the set (equals), useful if the newValue is not reference equal with the value in the set */
    inline fun <reified T> MutableSet<T>.refreshReference(newValue: T) = if (this.contains(newValue)) {
        this.remove(newValue)
        this.add(newValue)
        true
    } else false

    fun <T> List<T?>.takeIfAllNotNull(): List<T>? = if (all { it != null }) filterNotNull() else null

    fun <T, C : Collection<T>> C.takeIfNotEmpty(): C? = takeIf { it.isNotEmpty() }

    fun <T> List<T>.toPair(): Pair<T, T>? = if (size == 2) this[0] to this[1] else null

    fun <T> Pair<T, T>.equalsIgnoreOrder(other: Pair<T, T>) = this.toSet() == other.toSet()

    fun <T> Pair<T, T>.toSet(): Set<T> = setOf(first, second)

    inline fun <reified K : Enum<K>, V> enumMapOf(): EnumMap<K, V> {
        return EnumMap<K, V>(K::class.java)
    }

    /** Splits the input into equal sized lists. If the list can't get divided clean by [subs] then the last entry gets reduced. e.g. 13/4 = [4,4,4,1]*/
    fun <T> Collection<T>.split(subs: Int = 2): List<List<T>> {
        if (this.isEmpty()) return listOf(emptyList())
        val list = this.chunked(ceil(this.size.toDouble() / subs.toDouble()).toInt()).toMutableList()
        while (list.size < subs) {
            list.add(emptyList())
        }
        return list
    }

    inline fun <K, V, R : Any> Map<K, V>.mapKeysNotNull(transform: (Map.Entry<K, V>) -> R?): Map<R, V> {
        val destination = LinkedHashMap<R, V>()
        for (element in this) {
            val newKey = transform(element)
            if (newKey != null) {
                destination[newKey] = element.value
            }
        }
        return destination
    }

    inline fun <T, C : Number, D : Number> Iterable<T>.sumOfPair(selector: (T) -> Pair<C, D>): Pair<Double, Double> {
        var sum = Pair(0.0, 0.0)
        for (element in this) {
            val add = selector(element)
            sum = sum.first + add.first.toDouble() to sum.second + add.second.toDouble()
        }
        return sum
    }

    inline fun <T, R> Iterable<T>.zipWithNext3(transform: (a: T, b: T, c: T) -> R): List<R> {
        val iterator = iterator()
        if (!iterator.hasNext()) return emptyList()
        var one = iterator.next()
        if (!iterator.hasNext()) return emptyList()
        var two = iterator.next()
        val result = mutableListOf<R>()
        while (iterator.hasNext()) {
            val next = iterator.next()
            result.add(transform(one, two, next))
            one = two
            two = next
        }
        return result
    }

    fun <T> Iterable<T>.zipWithNext3(): List<Triple<T, T, T>> {
        return zipWithNext3 { a, b, c -> Triple(a, b, c) }
    }

    fun <K, V : Any> Map<K?, V>.filterNotNullKeys(): Map<K, V> {
        @Suppress("UNCHECKED_CAST")
        return filterKeys { it != null } as Map<K, V>
    }

    /**
     * Inserts the element at the index or appends it to the end if out of bounds of the list.
     *
     * @param index index to insert at, or append if >= size
     * @param element element to insert or add
     */
    fun <E> MutableList<E>.addOrInsert(index: Int, element: E) {
        if (index < size) add(index, element) else add(element)
    }

    /**
     * If there is only one element in the iterator, returns it. Otherwise, returns the [defaultValue].
     */
    private fun <T> getOnlyElement(it: Iterator<T>, defaultValue: T): T {
        if (!it.hasNext()) return defaultValue
        val ret = it.next()
        if (it.hasNext()) return defaultValue
        return ret
    }

    fun <T> getOnlyElement(it: Iterable<T>, defaultValue: T): T {
        return getOnlyElement(it.iterator(), defaultValue)
    }

    fun <K, V> MutableMap<K, V>.add(pair: Pair<K, V>) {
        this[pair.first] = pair.second
    }

    fun <T> MutableList<T>.removeIf(predicate: (T) -> Boolean) {
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next())) {
                iterator.remove()
            }
        }
    }

    fun <K, V> MutableMap<K, V>.removeIfKey(predicate: (K) -> Boolean) {
        val iterator = this.entries.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next().key)) {
                iterator.remove()
            }
        }
    }

    fun <K, V> LinkedHashMap<K, V>.putAt(index: Int, key: K, value: V) {
        val entries = LinkedHashMap<K, V>()
        var currentIndex = 0

        for ((existingKey, existingValue) in this) {
            if (currentIndex == index) {
                entries[key] = value // Insert at the specified index
            }
            entries[existingKey] = existingValue
            currentIndex++
        }

        if (index >= size) {
            entries[key] = value // If index is out of range, append at the end
        }

        clear()
        putAll(entries)
    }

    fun <T> Collection<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
        for ((index, element) in this.withIndex()) {
            if (predicate(element)) {
                return index
            }
        }
        return null
    }

    fun <T> List<T>.insertAfterEach(extra: T): List<T> = buildList(size * 2) {
        for (item in this@insertAfterEach) {
            add(item)
            add(extra)
        }
    }

    class OrderedQueue<T> : PriorityQueue<WeightedItem<T>>() {
        fun add(item: T, weight: Double): Boolean = super.add(WeightedItem(item, weight))
        fun pollOrNull(): T? = poll()?.item
    }

    data class WeightedItem<T>(val item: T, val weight: Double) : Comparable<WeightedItem<T>> {
        override fun compareTo(other: WeightedItem<T>): Int = this.weight.compareTo(other.weight)
    }
}
