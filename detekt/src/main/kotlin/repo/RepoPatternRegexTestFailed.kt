package at.hannibal2.skyhanni.detektrules.repo

import at.hannibal2.skyhanni.detektrules.RepoPatternElement.Companion.asRepoPatternElement
import at.hannibal2.skyhanni.detektrules.SkyHanniRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtPropertyDelegate

class RepoPatternRegexTestFailed(config: Config) : SkyHanniRule(config) {
    override val issue = Issue(
        "RepoPatternRegexTestFailed",
        Severity.Style,
        "All repo patterns must be accompanied by one or more passing regex test.",
        Debt.FIVE_MINS,
    )

    override fun visitPropertyDelegate(delegate: KtPropertyDelegate) {
        super.visitPropertyDelegate(delegate)

        val repoPatternElement = delegate.asRepoPatternElement() ?: return
        val variableName = repoPatternElement.variableName
        val rawPattern = repoPatternElement.rawPattern

        if (!rawPattern.needsRegexTest()) return

        if (repoPatternElement.regexTests.isEmpty()) return

        repoPatternElement.regexTests.forEach { test ->
            if (!repoPatternElement.pattern.matcher(test).find()) {
                delegate.reportIssue(
                    "Repo pattern `$variableName` failed regex test: `$test` pattern: `$rawPattern`. " +
                        "[View on Regex101](${repoPatternElement.regex101Url})",
                )
            }
        }

        repoPatternElement.failingRegexTests.forEach { test ->
            if (repoPatternElement.pattern.matcher(test).find()) {
                delegate.reportIssue("Repo pattern `$variableName` passed regex test: `$test` pattern: `$rawPattern` " +
                    "even though it was set to fail. [View on Regex101](${repoPatternElement.regex101Url})")
            }
        }
    }

    private fun String.needsRegexTest(): Boolean {
        return regexConstructs.containsMatchIn(this)
    }

    companion object {
        val regexConstructs = Regex("""(?<!\\)[.*+(){}\[|?]""")
    }
}
