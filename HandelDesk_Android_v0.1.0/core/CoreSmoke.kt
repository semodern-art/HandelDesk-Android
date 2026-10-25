import de.handeltheke.handeldesk.core.*

fun main() {
    check(HandelthekeSeoPolicy.slug("Original Kärcher Ladegerät 5.5V 2A") == "original-karcher-ladegerat-5-5v-2a")
    check(HandelthekeSeoPolicy.slug("оригинальный пульт управления") == "originalnyj-pult-upravleniya")
    val q = SearchSyntax.parse("Samsung \"BN59-01232B RMCTPJ1AP2\" -analog model:BN59 OR model:BN60")
    check(q.errors.isEmpty())
    check(q.excluded.size == 1)
    check(q.positiveGroups.last().size == 2)
    check(ThreeWayMerge.compare(20, 22, 21).status == MergeStatus.CONFLICT)
    println("HandelDesk core smoke: OK")
}
