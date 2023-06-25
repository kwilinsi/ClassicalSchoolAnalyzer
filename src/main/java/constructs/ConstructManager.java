package constructs;

import constructs.correction.Correction;
import constructs.district.CachedDistrict;
import constructs.district.District;
import constructs.districtOrganization.CachedDistrictOrganization;
import constructs.districtOrganization.DistrictOrganization;
import constructs.organization.Organization;
import constructs.school.Attribute;
import constructs.school.CachedSchool;
import constructs.school.School;
import database.Database;
import database.Table;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Pair;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is a base manager with methods that apply to any {@link Construct Constructs} from the SQL database.
 */
public class ConstructManager {
    private static final Logger logger = LoggerFactory.getLogger(ConstructManager.class);

    /**
     * Load every instance of some {@link CachedConstruct} from the SQL database.
     *
     * @param connection The SQL connection.
     * @param cla        The class to load.
     * @param progress   An optional progress window. If this is given, it is updated accordingly.
     * @param <T>        The type of construct.
     * @return The list of constructs.
     * @throws SQLException                 If there is an error querying the database.
     * @throws ReflectiveOperationException If there is an error related to using the ResultSet constructor of the
     *                                      given cached construct.
     */
    @NotNull
    public static <T extends CachedConstruct> List<T> loadCache(@NotNull Connection connection,
                                                                Class<T> cla,
                                                                @Nullable SchoolListProgressWindow progress)
            throws SQLException, ReflectiveOperationException {
        String sqlTable = getConstructSQLTable(cla);
        logger.debug("Retrieving all {} from database...", sqlTable);
        if (progress != null) progress.resetSubProgressBar(3).setSubTask("Generating SQL query...");

        List<T> constructs = new ArrayList<>();

        // Prepare the statement
        PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + sqlTable);

        // Execute
        if (progress != null) progress.incrementSubProgress().setSubTask("Awaiting response...");
        ResultSet resultSet = statement.executeQuery();

        // Convert the resultSet to a list of schools
        if (progress != null) progress.incrementSubProgress().setSubTask("Processing result...");

        Constructor<T> constructor = cla.getConstructor(ResultSet.class);
        while (resultSet.next())
            constructs.add(constructor.newInstance(resultSet));

        if (progress != null) progress.incrementSubProgress();
        return constructs;
    }

    /**
     * Convenience method for {@link #loadCache(Connection, Class, SchoolListProgressWindow) loadCache()} that
     * catches and logs all exceptions, returning <code>null</code> if something fails.
     *
     * @param cla      The class to load.
     * @param progress An optional progress window. If this is given, it is updated accordingly. If an error occurs,
     *                 this will {@link SchoolListProgressWindow#errorOut(String, Throwable) error out}.
     * @param <T>      The type of construct.
     * @return The list of constructs.
     */
    @Nullable
    public static <T extends CachedConstruct> List<T> loadCacheNullable(@NotNull Connection connection,
                                                                        Class<T> cla,
                                                                        @Nullable SchoolListProgressWindow progress) {
        try {
            return loadCache(connection, cla, progress);
        } catch (SQLException | ReflectiveOperationException e) {
            if (progress != null)
                progress.errorOut(
                        "Failed to retrieve " + getConstructSQLTable(cla) + " from database. Aborting download.", e
                );
            return null;
        }
    }

    /**
     * Save a collection of {@link Construct Constructs} to the database.
     * <p>
     * If the constructs are {@link CachedConstruct CachedConstructs}, they are filtered such that only those that
     * {@link CachedConstruct#didChange() changed} are saved, and those that use {@link CachedConstruct#usesUpdate()
     * update} statements are handled properly.
     *
     * @param connection The SQL connection.
     * @param constructs The constructs to save. Any <code>null</code> constructs are skipped. All constructs must be
     *                   the same type.
     * @param progress   An optional progress window. If this is given, it is updated accordingly.
     * @param <T>        The type of construct.
     * @throws SQLException If there is an error creating or executing the statements.
     */
    public static <T extends Construct> void saveToDatabase(@NotNull Connection connection,
                                                            @NotNull Collection<T> constructs,
                                                            @Nullable SchoolListProgressWindow progress)
            throws SQLException {
        // Identify only the ones that need to be saved
        List<? extends Construct> toSave = constructs.stream().filter(Objects::nonNull).toList();

        // If the constructs are a CachedConstruct, UPDATE statements may also be required
        boolean isCached = toSave.get(0) instanceof CachedConstruct;
        if (isCached)
            toSave = toSave.stream().map(c -> (CachedConstruct) c).filter(CachedConstruct::didChange).toList();

        if (toSave.size() == 0) return;

        if (progress != null) {
            String name = getConstructName(toSave.get(0), true);
            progress.setGeneralTask("Saving " + name + " to database...").resetSubProgressBar(toSave.size(), name);
        }

        // Generate SQL statements: an INSERT statement, and an UPDATE statement only used for cached constructs
        Pair<String, Boolean> insertSQL = getInsertSQL(toSave.get(0));
        PreparedStatement insertStmt = connection.prepareStatement(
                insertSQL.a, insertSQL.b ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS
        );
        List<PreparedStatement> updateStatements = new ArrayList<>();
        List<Construct> inserted = new ArrayList<>();

        for (Construct construct : toSave) {
            if (isCached && ((CachedConstruct) construct).usesUpdate()) {
                updateStatements.add(((CachedConstruct) construct).toUpdateStatement(connection));
            } else {
                construct.addToInsertStatement(insertStmt);
                inserted.add(construct);
            }

            if (isCached)
                ((CachedConstruct) construct).saveChanges();

            if (progress != null) progress.incrementSubProgress().setSubTask("Generated SQL for " + construct);
        }

        // Execute SQL statements
        if (progress != null) progress.completeSubProgress().setSubTask("Executing SQL inserts...");

        logResult(insertStmt.executeBatch(), "insert");

        // Update the construct keys, if applicable
        if (insertSQL.b) {
            if (progress != null)
                progress.resetSubProgressBar(inserted.size(), "Keys").setSubTask("Updating SQL ids...");

            ResultSet keys = insertStmt.getGeneratedKeys();
            int i = 0;
            while (keys.next()) {
                inserted.get(i).setId(keys.getInt(1));
                i++;
                if (progress != null) progress.incrementSubProgress();
            }

            if (progress != null) progress.completeSubProgress();
        }

        // Execute the cached stuff
        if (isCached) {
            if (progress != null) progress.setSubTask("Executing SQL updates...");

            for (PreparedStatement statement : updateStatements)
                logResult(statement.executeUpdate());
        }
    }

    /**
     * Convenience method for {@link #saveToDatabase(Connection, Collection, SchoolListProgressWindow) saveToDatabase()}
     * with a newly initialized {@link Connection} and no progress window.
     *
     * @param constructs The constructs to save. Any <code>null</code> constructs are skipped. All constructs must be
     *                   the same type.
     * @param <T>        The type of construct.
     * @throws SQLException If there is an error creating or executing the statements.
     */
    public static <T extends Construct> void saveToDatabase(@NotNull Collection<T> constructs) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            saveToDatabase(connection, constructs, null);
        }
    }

    /**
     * Convenience method for {@link #saveToDatabase(Collection) saveToDatabase()} with a single {@link Construct}.
     *
     * @param construct The construct
     * @throws SQLException If there is an error creating or executing the statement.
     */
    public static void saveToDatabase(@NotNull Construct construct) throws SQLException {
        saveToDatabase(List.of(construct));
    }

    /**
     * Convenience method for {@link #saveToDatabase(Collection) saveToDatabase()} that catches and logs any
     * {@link SQLException SQLExceptions}.
     *
     * @param constructs The constructs to save. Any <code>null</code> constructs are skipped. All constructs must be
     *                   the same type.
     * @param message    The message to log if the saving process fails for any reason. If this is <code>null</code>
     *                   and an error occurs, a generic substitute is used.
     * @param <T>        The type of construct.
     * @return <code>True</code> if and only if the save is successful.
     */
    public static <T extends Construct> boolean saveToDatabase(@NotNull Collection<T> constructs,
                                                               @Nullable String message) {
        try {
            saveToDatabase(constructs);
            return true;
        } catch (SQLException e) {
            if (message == null)
                message = String.format("Error adding %s to SQL database.",
                        getConstructName(constructs.stream().filter(Objects::nonNull).findFirst().orElse(null),
                                true)
                );

            logger.error(message, e);
            return false;
        }
    }

    /**
     * Get the name of the SQL {@link Table} associated with a particular construct.
     *
     * @param cls The construct class.
     * @param <T> The construct class type.
     * @return The SQL table.
     */
    @Nullable
    private static <T extends CachedConstruct> String getConstructSQLTable(@NotNull Class<T> cls) {
        if (cls == CachedDistrict.class)
            return Table.Districts.getTableName();
        else if (cls == CachedSchool.class)
            return Table.Schools.getTableName();
        else if (cls == CachedDistrictOrganization.class)
            return Table.DistrictOrganizations.getTableName();
        else
            return null;
    }

    /**
     * Get the name associated with the given base {@link Construct}. This only considers direct subclasses of the
     * Construct class. Further subclasses use the nearest available superclass.
     *
     * @param obj    Some instance of the particular construct.
     * @param plural Whether the plural form of the name should be used.
     * @return The name of the construct, or <code>"&lt;Unknown Construct&gt;"</code> if the name is not recognized.
     */
    @NotNull
    public static String getConstructName(@Nullable Object obj, boolean plural) {
        Class<?> cls = obj == null ? Object.class : obj.getClass();
        if (School.class.isAssignableFrom(cls))
            return plural ? "Schools" : "School";
        else if (District.class.isAssignableFrom(cls))
            return plural ? "Districts" : "District";
        else if (Organization.class.isAssignableFrom(cls))
            return plural ? "Organizations" : "Organization";
        else if (DistrictOrganization.class.isAssignableFrom(cls))
            return plural ? "DistrictOrganizations" : "DistrictOrganization";
        else if (Correction.class.isAssignableFrom(cls))
            return plural ? "Constructs" : "Construct";
        else
            return "<Unknown Construct>";
    }

    /**
     * Get the complete SQL statement for a particular construct. This uses <code>"?"</code> for arguments, as it's
     * designed for a {@link PreparedStatement}.
     * <p>
     * Along with the SQL statement, this returns a boolean indicating whether the
     * {@link Statement#RETURN_GENERATED_KEYS RETURN_GENERATED_KEYS} flag should be used with the {@link Statement}.
     * This is <code>true</code> for constructs supporting {@link Construct#setId(int) setId()}.
     *
     * @param obj An instance of the construct.
     * @return The SQL statement (or an empty string if the construct is not recognized) and whether to get the
     * generated keys.
     */
    @NotNull
    public static Pair<String, Boolean> getInsertSQL(@NotNull Construct obj) {
        Class<? extends Construct> cls = obj.getClass();
        if (School.class.isAssignableFrom(cls))
            return Pair.of(String.format(
                            "INSERT INTO Schools (district_id, %s) VALUES (?, %s)",
                            Arrays.stream(Attribute.values()).map(Attribute::name)
                                    .collect(Collectors.joining(", ")),
                            String.join(", ", Collections.nCopies(Attribute.values().length, "?"))),
                    true
            );
        else if (District.class.isAssignableFrom(cls))
            return Pair.of("INSERT INTO Districts (name, website_url) VALUES (?, ?)", true);
        else if (Organization.class.isAssignableFrom(cls))
            return Pair.of("INSERT INTO Organizations (id, name, name_abbr, homepage_url, school_list_url) " +
                    "VALUES (?, ?, ?, ?, ?)", false);
        else if (DistrictOrganization.class.isAssignableFrom(cls))
            return Pair.of("INSERT INTO DistrictOrganizations (organization_id, district_id) VALUES (?, ?)", true);
        else if (Construct.class.isAssignableFrom(cls))
            return Pair.of("INSERT INTO Corrections (type, data, deserialization_data, notes) VALUES (?, ?, ?, ?)",
                    true);
        else
            return Pair.of("", false);
    }

    /**
     * Check the {@link Statement#executeBatch() results} of a batch SQL statement, logging each result.
     * <p>
     * If the result is a success (greater than or equal to 0, indicating a row count, or
     * {@link Statement#SUCCESS_NO_INFO SUCCESS_NO_INFO}), a <code>trace</code> message is logged. If the result os
     * {@link Statement#EXECUTE_FAILED EXECUTE_FAILED} or unknown, a <code>warn</code> message is logged.
     *
     * @param statementType The type of SQL statements that were executed. This might be <code>"insert"</code> or
     *                      <code>"update"</code>
     * @param batchResult   The result.
     */
    private static void logResult(int[] batchResult, String statementType) {
        for (int i = 0; i < batchResult.length; i++) {
            int result = batchResult[i];
            if (result >= 0)
                logger.trace("- SQL {} statement at batch index {} updated {} {}",
                        statementType, i, result, result == 1 ? "row" : "rows");
            else if (result == Statement.SUCCESS_NO_INFO)
                logger.trace("- SQL {} statement at batch index {} returned SUCCESS_NO_INFO", statementType, i);
            else if (result == Statement.EXECUTE_FAILED)
                logger.warn("- SQL {} statement at batch index {} returned EXECUTE_FAILED", statementType, i);
            else
                logger.warn("- SQL {} statement at batch index {} returned unreachable result {}",
                        statementType, i, result);
        }
    }

    /**
     * Log a message based on the {@link Statement#executeUpdate(String) result} of an update statement.
     *
     * @param rowCount The result of executing the statement.
     */
    private static void logResult(int rowCount) {
        if (rowCount == 0)
            logger.trace("- SQL statement executed successfully");
        else if (rowCount > 0)
            logger.trace("- SQL statement updated {} {}", rowCount, rowCount == 1 ? "row" : "rows");
        else
            logger.warn("- SQL statement returned unexpected row count {}", rowCount);
    }
}
