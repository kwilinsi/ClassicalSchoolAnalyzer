package constructs.correction;

import com.google.gson.Gson;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import constructs.ConstructManager;
import constructs.correction.districtMatch.DistrictMatchCorrection;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.correction.schoolCorrection.SchoolCorrection;
import database.Database;
import gui.windows.corrections.CorrectionAddWindow;
import gui.windows.corrections.districtMatch.DistrictMatchCorrectionWindow;
import gui.windows.corrections.schoolAttribute.SchoolAttributeCorrectionWindow;
import gui.windows.corrections.schoolCorrection.SchoolCorrectionWindow;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.RunnableOption;
import gui.windows.prompt.selection.SelectionPrompt;
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
     * This is the master list of {@link Correction Corrections}. It's a cache of a SQL database Corrections table.
     * <p>
     * The corrections are sorted by their {@link CorrectionType CorrectionType} for easy retrieval. This map is initialized to have a
     * guaranteed key for every type, though that will initialize map to an empty {@link ArrayList}. Note that the
     * lists thread-safe.
     * <p>
     * To populate this list from the database, call {@link #load()}.
     */
    @NotNull
    @Unmodifiable
    private static final Map<CorrectionType, List<Correction>> CORRECTIONS = Arrays.stream(CorrectionType.values()).collect(
            Collectors.toMap(Function.identity(), ignored -> Collections.synchronizedList(new ArrayList<>()))
    );

    /**
     * Fill the {@link #CORRECTIONS} list with all the Corrections in the SQL database.
     * <p>
     * If the list already contains Corrections, this method does nothing.
     *
     * @throws SQLException If there is an error querying the database.
     */
    public static void load() throws SQLException {
        // If any Correction lists contain Corrections, do nothing
        if (CORRECTIONS.values().stream().filter(Objects::nonNull).anyMatch(l -> !l.isEmpty()))
            return;

        // Load all Corrections from the database
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
                    CORRECTIONS.get(type).add(type.make(dataStr, deserializationData, notesStr));
                } catch (Exception e) {
                    logger.warn("Failed to create Correction of type '" + typeStr + "' from data " + dataStr, e);
                }
            }
        }

        logger.info("Loaded Corrections.");
    }

    /**
     * Get all the {@link Correction Corrections} associated with the given {@link CorrectionType CorrectionType}.
     *
     * @param type The type of correction.
     * @return All cached Corrections with that type. This may be empty.
     */
    @NotNull
    public static List<Correction> get(@NotNull CorrectionType type) {
        return CORRECTIONS.get(type);
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
                    RunnableOption.of("Back", null)
            ));

            if (runnable == null)
                return;
            else
                runnable.run();
        }
    }

    /**
     * Add a new {@link Correction} to the {@link #CORRECTIONS master list}, and
     * {@link ConstructManager#saveToDatabase(constructs.Construct) save} it to the database.
     *
     * @param type       The type of Correction.
     * @param correction The Correction itself.
     * @throws SQLException If there is an error saving the Correction to the database.
     */
    public static void add(@NotNull CorrectionType type, @NotNull Correction correction) throws SQLException {
        logger.debug("Adding Correction type {} class {}", type, correction.getClass());
        CORRECTIONS.get(type).add(correction);
        ConstructManager.saveToDatabase(correction);
    }

    /**
     * Open a GUI prompt for the Correction to add, and {@link #add(CorrectionType, Correction) add} the newly created correction.
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
        };

        logger.debug("Creating new {} Correction with {}", selection.name(), window.getClass());

        MessageDialogButton button = window.show(Main.GUI.getWindowGUI());
        if (button == MessageDialogButton.Cancel)
            return;

        try {
            add(selection, window.makeCorrection());
        } catch (SQLException e) {
            logger.error("Failed to save new " + selection.name() + " Correction to database", e);
        }
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link SchoolCorrection SchoolCorrections} (those with {@link CorrectionType CorrectionType}
     * {@link CorrectionType#SCHOOL_CORRECTION SCHOOL_CORRECTION}).
     *
     * @return The cached list of Corrections.
     */
    @NotNull
    public static List<SchoolCorrection> getSchoolCorrections() {
        List<SchoolCorrection> list = new ArrayList<>();
        for (Correction c : CORRECTIONS.get(CorrectionType.SCHOOL_CORRECTION))
            if (c instanceof SchoolCorrection s)
                list.add(s);

        return list;
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link SchoolAttributeCorrection SchoolAttributeCorrections} (those with
     * {@link CorrectionType CorrectionType} {@link CorrectionType#SCHOOL_ATTRIBUTE SCHOOL_ATTRIBUTE}).
     *
     * @return The cached list of Corrections.
     */
    @NotNull
    public static List<SchoolAttributeCorrection> getSchoolAttributes() {
        List<SchoolAttributeCorrection> list = new ArrayList<>();
        for (Correction c : CORRECTIONS.get(CorrectionType.SCHOOL_ATTRIBUTE))
            if (c instanceof SchoolAttributeCorrection s)
                list.add(s);

        return list;
    }

    /**
     * {@link #get(CorrectionType) Get} all {@link DistrictMatchCorrection DistrictMatchCorrections} (those with
     * {@link CorrectionType CorrectionType} {@link CorrectionType#DISTRICT_MATCH DISTRICT_MATCH}).
     *
     * @return The cached list of Corrections.
     */
    public static List<DistrictMatchCorrection> getDistrictMatches() {
        List<DistrictMatchCorrection> list = new ArrayList<>();
        for (Correction c : CORRECTIONS.get(CorrectionType.DISTRICT_MATCH))
            if (c instanceof DistrictMatchCorrection d)
                list.add(d);

        return list;
    }
}
