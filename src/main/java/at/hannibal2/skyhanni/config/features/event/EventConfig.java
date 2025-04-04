package at.hannibal2.skyhanni.config.features.event;

import at.hannibal2.skyhanni.config.features.event.bingo.BingoConfig;
import at.hannibal2.skyhanni.config.features.event.carnival.CarnivalConfig;
import at.hannibal2.skyhanni.config.features.event.diana.DianaConfig;
import at.hannibal2.skyhanni.config.features.event.gifting.GiftingConfig;
import at.hannibal2.skyhanni.config.features.event.hoppity.HoppityEggsConfig;
import at.hannibal2.skyhanni.config.features.event.waypoints.LobbyWaypointsConfig;
import at.hannibal2.skyhanni.config.features.event.winter.WinterConfig;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

public class EventConfig {

    @Category(name = "Bingo", desc = "Monthly Bingo Event settings")
    @Expose
    public BingoConfig bingo = new BingoConfig();

    @Category(name = "Diana", desc = "Diana's Mythological Burrows")
    @Expose
    public DianaConfig diana = new DianaConfig();

    @Category(name = "Winter", desc = "Winter Season on Jerry's Island")
    @Expose
    public WinterConfig winter = new WinterConfig();

    @Category(name = "Gifting", desc = "Giving and receiving gifts")
    @Expose
    public GiftingConfig gifting = new GiftingConfig();

    @Expose
    @Category(name = "Hoppity Eggs", desc = "Features for the Hoppity event that happens every SkyBlock spring.")
    public HoppityEggsConfig hoppityEggs = new HoppityEggsConfig();

    @ConfigOption(name = "City Project", desc = "")
    @Accordion
    @Expose
    public CityProjectConfig cityProject = new CityProjectConfig();

    @ConfigOption(name = "Mayor Jerry's Jerrypocalypse", desc = "")
    @Accordion
    @Expose
    public MayorJerryConfig jerry = new MayorJerryConfig();

    @ConfigOption(name = "The Great Spook", desc = "")
    @Accordion
    @Expose
    public GreatSpookConfig spook = new GreatSpookConfig();

    @Expose
    @Category(name = "The Carnival", desc = "Features for games at §eThe Carnival §7when §bFoxy §7is Mayor.")
    public CarnivalConfig carnival = new CarnivalConfig();

    // comment in if the event is needed again
//    @ConfigOption(name = "300þ Anniversary Celebration", desc = "Features for the 300þ year of SkyBlock")
    @Accordion
    @Expose
    public CenturyConfig century = new CenturyConfig();

    @ConfigOption(name = "400þ Anniversary Celebration", desc = "Features for the 400þ year of SkyBlock.")
    @Accordion
    @Expose
    public AnniversaryCelebration400Config anniversaryCelebration400 = new AnniversaryCelebration400Config();

    @ConfigOption(name = "Year of the Seal", desc = "Features for Year of the Seals.")
    @Accordion
    @Expose
    public YearOfTheSealConfig yearOfTheSeal = new YearOfTheSealConfig();

    @Category(name = "Lobby Waypoints", desc = "Lobby Event Waypoint settings")
    @Expose
    public LobbyWaypointsConfig lobbyWaypoints = new LobbyWaypointsConfig();
}
