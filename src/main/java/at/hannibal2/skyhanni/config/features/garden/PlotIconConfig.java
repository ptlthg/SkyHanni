package at.hannibal2.skyhanni.config.features.garden;

import at.hannibal2.skyhanni.config.FeatureToggle;
//#if TODO
import at.hannibal2.skyhanni.features.garden.inventory.plots.GardenPlotIcon;
import at.hannibal2.skyhanni.utils.HypixelCommands;
//#endif
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

// todo 1.21 impl needed
public class PlotIconConfig {
    @Expose
    @ConfigOption(name = "Enable", desc = "Enable icon replacement in the Configure Plots menu.")
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean enabled = true;

    //#if TODO
    @ConfigOption(name = "Hard Reset", desc = "Reset every slot to its original item.")
    @ConfigEditorButton(buttonText = "Reset")
    public Runnable hardReset = () -> {
        GardenPlotIcon.INSTANCE.setHardReset(true);
        HypixelCommands.INSTANCE.gardenDesk();
    };
    //#endif
}
