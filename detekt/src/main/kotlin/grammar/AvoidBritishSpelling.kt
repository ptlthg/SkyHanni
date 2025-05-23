package at.hannibal2.skyhanni.detektrules.grammar

import at.hannibal2.skyhanni.detektrules.SkyHanniRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * This rule reports all usages of the british spelling over the american spelling in the codebase,
 * this will ignore any type annotations, i.e., `@ConfigEditorColour` will not be reported.
 */
class AvoidBritishSpelling(config: Config) : SkyHanniRule(config) {
    override val issue = Issue(
        "AvoidBritishSpelling",
        Severity.Style,
        "Avoid using the british spelling over the american spelling.",
        Debt.FIVE_MINS,
    )

    private val scannedWords = mapOf(
        "colour" to "color",
        "armour" to "armor",
    )

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        val text =
            expression.text // Be aware .getText() returns the entire span of this template, including variable names contained within. This should be rare enough of a problem for us to not care about it.

        for (word in scannedWords) {
            if (text.contains(word.key, ignoreCase = true)) {
                expression.reportIssue("Avoid using the word '${word.key}' in code, '${word.value}' is preferred instead.")
            }
        }
        super.visitStringTemplateExpression(expression)
    }
}
