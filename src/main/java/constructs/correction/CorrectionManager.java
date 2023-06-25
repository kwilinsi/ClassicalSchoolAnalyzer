package constructs.correction;

import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.ConstructManager;
import constructs.correction.districtMatch.DistrictMatchCorrection;
import constructs.correction.normalizedAddress.NormalizedAddress;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.correction.schoolCorrection.SchoolCorrection;
import database.Database;
import gui.windows.corrections.CorrectionAddWindow;
import gui.windows.corrections.districtMatch.DistrictMatchCorrectionWindow;
import gui.windows.corrections.normalizedAddress.NormalizedAddressCorrectionWindow;
import gui.windows.corrections.schoolAttribute.SchoolAttributeCorrectionWindow;
import gui.windows.corrections.schoolCorrection.SchoolCorrectionWindow;
import gui.windows.prompt.Option;
import gui.windows.prompt.RunnableOption;
import gui.windows.prompt.SelectionPrompt;
import main.Actions;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class manages the {@link Correction Corrections} from the SQL database.
 */
public class CorrectionManager {
    private static final Logger logger = LoggerFactory.getLogger(CorrectionManager.class);

    /**
     * Listener interface that can be used to catch Correction {@link #add(CorrectionType, Correction) adding}
     * and {@link #load() loading} events.
     */
    public interface Listener {
        /**
         * This method is {@link #callListeners(CorrectionType, Correction) called} whenever one of the following
         * conditions is met:
         * <ul>
         *     <li>A single new Correction is {@link #add(CorrectionType, Correction) added}.
         *     <li>The entire {@link #CORRECTIONS list} of Corrections is {@link #load() loaded} from the database. If
         *     this happens, the listeners are called once for every added Correction.
         * </ul>
         * In either case, it is called after the adding process occurs.
         *
         * @param type       The type of Correction.
         * @param correction The newly added Correction.
         */
        void onCorrectionAdded(@NotNull CorrectionType type, @NotNull Correction correction);
    }

    /**
     * This is the master list of {@link Correction Corrections}. It's a cache of a SQL database Corrections table.
     * <p>
     * The corrections are sorted by their {@link CorrectionType CorrectionType} for easy retrieval. This map is
     * initialized to have a
     * guaranteed key for every type, though that will initialize map to an empty {@link ArrayList}. Note that the
     * lists are thread-safe.
     * <p>
     * To populate this list from the database, call {@link #load()}.
     */
    @NotNull
    @Unmodifiable
    private static final Map<@NotNull CorrectionType, @NotNull List<@NotNull Correction>> CORRECTIONS =
            Arrays.stream(CorrectionType.values()).collect(
                    Collectors.toMap(Function.identity(), ignored -> Collections.synchronizedList(new ArrayList<>()))
            );

    /**
     * This indicates whether the {@link #CORRECTIONS} list is believed to currently match the SQL database. When the
     * program starts, this is <code>false</code>, indicating that corrections must be {@link #load() loaded} from
     * the database.
     * <p>
     * This can be invalidated at any time by calling {@link #invalidate()}.
     */
    private static boolean IS_CACHE_VALID = false;

    /**
     * This is the list of {@link Listener Listeners} that are {@link #callListeners(CorrectionType, Correction) called}
     * when a new Correction is added. See {@link Listener#onCorrectionAdded(CorrectionType, Correction)
     * Listener.onCorrectionAdded()}.
     * <p>
     * Add a new listener with {@link #registerListener(Listener) registerListener()}.
     */
    private static final List<Listener> LISTENERS = new ArrayList<>();

    /**
     * Fill the {@link #CORRECTIONS} list with all the Corrections in the SQL database.
     * <p>
     * If the list is already {@link #IS_CACHE_VALID valid}, this has no effect. Otherwise, after loading the
     * Corrections, it sets the validity to <code>true</code>.
     * <p>
     * To force reloading this cache the next time the Corrections list is queried, you can {@link #invalidate()
     * invalidate} the cache.
     *
     * @throws SQLException If there is an error querying the database.
     */
    public static synchronized void load() throws SQLException {
        if (IS_CACHE_VALID) return;

        // Load all Corrections from the database
        logger.info("Loading Corrections from the database...");
        try (Connection connection = Database.getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM Corrections;");
            while (result.next()) {
                String typeStr = result.getString(2);
                String dataStr = result.getString(3);
                String deserializationData = result.getString(4);
                String notesStr = result.getString(5);
                CorrectionType type;

                try {
                    type = CorrectionType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    logger.warn("Failed to create Correction with unknown type '" + typeStr + "'", e);
                    continue;
                }

                try {
                    Correction correction = type.make(dataStr, deserializationData, notesStr);
                    CORRECTIONS.get(type).add(correction);
                    callListeners(type, correction);
                } catch (Exception e) {
                    logger.warn("Failed to create Correction of type '" + typeStr + "' from data " + dataStr, e);
                }
            }
        }

        IS_CACHE_VALID = true;
        logger.info("Finished loading Corrections");
    }

    /**
     * Invalidate the {@link #CORRECTIONS} cache, setting its {@link #IS_CACHE_VALID validity} to <code>false</code>.
     * Future queries of the list will require {@link #load() loading} from the database.
     */
    public static void invalidate() {
        logger.info("Invalidated the Corrections cache");
        IS_CACHE_VALID = false;
    }

    /**
     * Add a new {@link Correction}. This is done as follows:
     * <ol>
     *     <li>Add it to the {@link #CORRECTIONS master list} associated with the correct <code>type</code>.
     *     <li>{@link ConstructManager#saveToDatabase(constructs.Construct) Save} it to the database.
     *     <li>Call each of the {@link #LISTENERS} in the order they were added.
     * </ol>
     *
     * @param type       The type of Correction.
     * @param correction The Correction itself.
     * @throws SQLException If there is an error saving the Correction to the database.
     */
    public static void add(@NotNull CorrectionType type, @NotNull Correction correction) throws SQLException {
        logger.info("Adding new {} Correction to database...", type);
        CORRECTIONS.get(type).add(correction);
        ConstructManager.saveToDatabase(correction);
        callListeners(type, correction);
    }

    /**
     * Add a new {@link Listener Listener} to the list of {@link #LISTENERS}.
     *
     * @param listener The new listener.
     */
    public static void registerListener(@NotNull Listener listener) {
        LISTENERS.add(listener);
    }

    /**
     * Call each of the {@link #LISTENERS}, catching and logging any exceptions.
     *
     * @param type       The Correction type.
     * @param correction The newly added Correction.
     */
    private static void callListeners(@NotNull CorrectionType type, @NotNull Correction correction) {
        for (Listener listener : LISTENERS)
            try {
                listener.onCorrectionAdded(type, correction);
            } catch (Exception e) {
                logger.warn("Failed while running Correction listener", e);
            }
    }

    /**
     * Open the GUI manager for Corrections. Keep re-prompting it until the user chooses to go back.
     */
    public static void guiManager() {
        while (true) {
            Runnable runnable = Main.GUI.showPrompt(SelectionPrompt.of(
                    "Correction Manager",
                    "Please select a management action:",
                    RunnableOption.of("Add Corrections", CorrectionManager::promptAddCorrection),
                    RunnableOption.of("Remove Corrections", Actions::notImplemented),
                    RunnableOption.of("Invalidate Cache", CorrectionManager::invalidate),
                    RunnableOption.of("Back", null)
            ));

            if (runnable == null)
                return;
            else
                runnable.run();
        }
    }

    /**
     * Open a GUI prompt for the Correction to add, and {@link #add(CorrectionType, Correction) add} the newly
     * created correction.
     * <p>
     * If a {@link SQLException} occurs while saving the Correction to the database, that error is caught and logged.
     * In that case, it will still have been added to the {@link #CORRECTIONS cache}.
     */
    public static void promptAddCorrection() {
        SelectionPrompt<CorrectionType> prompt = SelectionPrompt.of(
                "Add Correction",
                "Select the type of correction to add:",
                Option.of("School Correction", CorrectionType.SCHOOL_CORRECTION),
                Option.of("School Attribute", CorrectionType.SCHOOL_ATTRIBUTE),
                Option.of("Normalized Address", CorrectionType.NORMALIZED_ADDRESS),
                Option.of("District Match", CorrectionType.DISTRICT_MATCH),
                Option.of("Back", null)
        );

        CorrectionType selection = Main.GUI.showPrompt(prompt);
        if (selection == null) {
            guiManager();
            return;
        }

        CorrectionAddWindow window = switch (selection) {
            case SCHOOL_CORRECTION -> new SchoolCorrectionWindow();
            case SCHOOL_ATTRIBUTE -> new SchoolAttributeCorrectionWindow();
            case DISTRICT_MATCH -> new DistrictMatchCorrectionWindow();
            case NORMALIZED_ADDRESS -> new NormalizedAddressCorrectionWindow();
        };

        logger.debug("Creating new {} Correction with {}", selection.name(), window.getClass());

        if (window.show() == MessageDialogButton.Cancel)
            return;

        try {
            add(selection, window.makeCorrection());
        } catch (SQLException e) {
            logger.error("Failed to save new " + selection.name() + " Correction to database", e);
        }
    }

    /**
     * Get all the {@link Correction Corrections} associated with the given {@link CorrectionType CorrectionType}.
     * <p>
     * If this cache is {@link #IS_CACHE_VALID invalid}, this will first {@link #load() load} it from the database.
     *
     * @param type The type of correction.
     * @return All cached Corrections with that type. This may be empty.
     */
    @NotNull
    public static List<Correction> get(@NotNull CorrectionType type) {
        if (!IS_CACHE_VALID) {
            try {
                load();
            } catch (SQLException e) {
                logger.error("Failed to load Corrections from the database. The returned list of " + type +
                        " may be invalid", e);
            }
        }

        return CORRECTIONS.get(type);
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link SchoolCorrection SchoolCorrections} (those with
     * {@link CorrectionType CorrectionType}
     * {@link CorrectionType#SCHOOL_CORRECTION SCHOOL_CORRECTION}).
     *
     * @return The cached list of Corrections.
     */
    @NotNull
    public static List<SchoolCorrection> getSchoolCorrections() {
        return get(CorrectionType.SCHOOL_CORRECTION).stream()
                .map(c -> (SchoolCorrection) c)
                .toList();
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link SchoolAttributeCorrection SchoolAttributeCorrections} (those with
     * {@link CorrectionType CorrectionType} {@link CorrectionType#SCHOOL_ATTRIBUTE SCHOOL_ATTRIBUTE}).
     *
     * @return The cached list of Corrections.
     */
    @NotNull
    public static List<SchoolAttributeCorrection> getSchoolAttributes() {
        return get(CorrectionType.SCHOOL_ATTRIBUTE).stream()
                .map(c -> (SchoolAttributeCorrection) c)
                .toList();
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link DistrictMatchCorrection DistrictMatchCorrections} (those with
     * {@link CorrectionType CorrectionType} {@link CorrectionType#DISTRICT_MATCH DISTRICT_MATCH}).
     *
     * @return The cached list of Corrections.
     */
    public static List<DistrictMatchCorrection> getDistrictMatches() {
        return get(CorrectionType.DISTRICT_MATCH).stream()
                .map(c -> (DistrictMatchCorrection) c)
                .toList();
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link NormalizedAddress NormalizedAddress} Corrections (those with
     * {@link CorrectionType CorrectionType} {@link CorrectionType#NORMALIZED_ADDRESS NORMALIZED_ADDRESS}).
     *
     * @return The cached list of Corrections.
     */
    @Unmodifiable
    public static List<NormalizedAddress> getNormalizedAddresses() {
        return get(CorrectionType.NORMALIZED_ADDRESS).stream()
                .map(c -> (NormalizedAddress) c)
                .toList();
    }
}
