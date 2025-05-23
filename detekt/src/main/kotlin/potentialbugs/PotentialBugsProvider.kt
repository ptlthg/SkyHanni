package at.hannibal2.skyhanni.detektrules.potentialbugs

import com.google.auto.service.AutoService
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

@AutoService(RuleSetProvider::class)
class PotentialBugsProvider : RuleSetProvider {
    override val ruleSetId: String
        get() = "potential-bugs"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf(
                ImmutableTypesWithExpectedInteriorMutabilityInConfig(config),
                StorageNeedsExpose(config),
                NonStorageDoesntNeedExpose(config),
            ),
        )
    }
}
