package constructs.correction;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import database.Database;
import gui.windows.corrections.CorrectionAddWindow;
import gui.windows.corrections.DistrictMatchCorrectionWindow;
import gui.windows.corrections.SchoolAttributeCorrectionWindow;
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
     * These are all the possible types that a {@link Correction} can have. They are associated with particular
     * subclasses of the Correction superclass.
     * <p>
     * Note that the current SQL structure limits these enums at a maximum {@link #name() name} length of 30.
     */
    public enum Type {
        /**
         * Corrections associated with school attributes.
         * <p>
         * <b>Correction class:</b> {@link SchoolAttributeCorrection}
         */
        SCHOOL_ATTRIBUTE(SchoolAttributeCorrection.class),

        /**
         * Corrections associated with district matches.
         * <p>
         * <b>Correction class:</b> {@link DistrictMatchCorrection}
         */
        DISTRICT_MATCH(DistrictMatchCorrection.class);

        /**
         * This is the class that is instantiated when a Correction is created from this type via {@link #make(String)}.
         */
        private final Class<? extends Correction> correctionClass;

        /**
         * Initialize a Correction type.
         *
         * @param correctionClass The {@link #correctionClass} associated with this type.
         */
        Type(Class<? extends Correction> correctionClass) {
            this.correctionClass = correctionClass;
        }

        /**
         * Create a new {@link Correction} of the appropriate {@link #correctionClass class} using the given JSON data.
         *
         * @param data The JSON data with which to make the Correction.
         * @return The new Correction.
         * @throws JsonSyntaxException If there is an error parsing the JSON data.
         */
        @NotNull
        public Correction make(@NotNull String data) throws JsonSyntaxException {
            return new Gson().fromJson(data, correctionClass);
        }
    }

    /**
     * This is the master list of {@link Correction Corrections}. It's a cache of a SQL database Corrections table.
     * <p>
     * The corrections are sorted by their {@link Type Type} for easy retrieval. This map is initialized to have a
     * guaranteed key for every type, though that will initialize map to an empty {@link ArrayList}. Note that the
     * lists thread-safe.
     * <p>
     * To populate this list from the database, call {@link #load()}.
     */
    @NotNull
    @Unmodifiable
    private static final Map<Type, List<Correction>> CORRECTIONS = Arrays.stream(Type.values()).collect(
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
                String typeStr = result.getString(1);
                String dataStr = result.getString(2);
                Type type;

                try {
                    type = Type.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    logger.warn("Failed to save Correction with unknown type " + typeStr, e);
                    continue;
                }

                try {
                    CORRECTIONS.get(type).add(type.make(dataStr));
                } catch (Exception e) {
                    logger.warn("Failed to create Correction of type " + typeStr + " from data " + dataStr, e);
                }
            }
        }
    }

    /**
     * Get all the {@link Correction Corrections} associated with the given {@link Type Type}.
     *
     * @param type The type of correction.
     * @return All cached Corrections with that type. This may be empty.
     */
    @NotNull
    public static List<Correction> get(@NotNull Type type) {
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
     * Add a new {@link Correction} to the {@link #CORRECTIONS master list}, and save it to the database.
     *
     * @param type       The type of Correction.
     * @param correction The Correction itself.
     * @throws SQLException If there is an error saving the Correction to the database.
     */
    public static void add(@NotNull Type type, @NotNull Correction correction) throws SQLException {
        logger.debug("Adding Correction type {} class {}", type, correction.getClass());

        // Add to the cache
        CORRECTIONS.get(type).add(correction);

        // Add to the database
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO Corrections (type, data, notes) VALUES (?, ?, ?)"
            );

            statement.setString(1, type.name());
            statement.setString(2, new Gson().toJson(correction));
            statement.setString(3, correction.notes);

            statement.executeUpdate();
        }

        logger.info("Added new {} to database", correction.getClass().getSimpleName());
    }

    /**
     * Open a GUI prompt for the Correction to add, and {@link #add(Type, Correction) add} the newly created correction.
     * <p>
     * If a {@link SQLException} occurs while saving the Correction to the database, that error is caught and logged.
     * In that case, it will still have been added to the {@link #CORRECTIONS cache}.
     */
    public static void promptAddCorrection() {
        SelectionPrompt<Type> prompt = SelectionPrompt.of(
                "Add Correction",
                "Select the type of correction to add:",
                Option.of("School Attribute", Type.SCHOOL_ATTRIBUTE),
                Option.of("District Match", Type.DISTRICT_MATCH),
                Option.of("Back", null)
        );

        Type selection = Main.GUI.showPrompt(prompt);
        if (selection == null) {
            guiManager();
            return;
        }

        CorrectionAddWindow window = switch (selection) {
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
     * {@link #get(Type) Get} all {@link SchoolAttributeCorrection SchoolAttributeCorrections} (those with
     * {@link Type Type} {@link Type#SCHOOL_ATTRIBUTE SCHOOL_ATTRIBUTE}).
     *
     * @return The cached list of Corrections.
     */
    @NotNull
    public static List<SchoolAttributeCorrection> getSchoolAttribute() {
        List<SchoolAttributeCorrection> list = new ArrayList<>();
        for (Correction c : CORRECTIONS.get(Type.SCHOOL_ATTRIBUTE))
            if (c instanceof SchoolAttributeCorrection s)
                list.add(s);

        return list;
    }

    /**
     * {@link #get(Type) Get} all {@link DistrictMatchCorrection DistrictMatchCorrections} (those with
     * {@link Type Type} {@link Type#DISTRICT_MATCH DISTRICT_MATCH}).
     *
     * @return The cached list of Corrections.
     */
    public static List<DistrictMatchCorrection> getDistrictMatch() {
        List<DistrictMatchCorrection> list = new ArrayList<>();
        for (Correction c : CORRECTIONS.get(Type.DISTRICT_MATCH))
            if (c instanceof DistrictMatchCorrection d)
                list.add(d);

        return list;
    }
}
