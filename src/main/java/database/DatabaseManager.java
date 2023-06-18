package database;

import constructs.ConstructManager;
import constructs.organization.OrganizationManager;
import gui.windows.prompt.selection.MultiSelectionPrompt;
import gui.windows.prompt.selection.Option;
import gui.windows.prompt.selection.RunnableOption;
import gui.windows.prompt.selection.SelectionPrompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Pair;
import utils.Utils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Function;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * Present the user with the database management options, and call the method associated with their selection.
     */
    public static void prompt() {
        Runnable runnable = Main.GUI.showPrompt(SelectionPrompt.of(
                "Manage Database",
                "What would you like to do?",
                RunnableOption.of("Clear Selected Tables", DatabaseManager::clearTables),
                RunnableOption.of("Reset All", DatabaseManager::resetDatabase,
                        "Are you sure you want to reset the entire database? " +
                                "This will permanently delete every table."),
                Option.of("Back", null)
        ));

        if (runnable != null)
            runnable.run();
    }

    /**
     * Prompt the user to select tables to clear, and then {@link #clearTables(Collection) clear} those tables.
     */
    private static void clearTables() {
        Function<List<Table>, Pair<Boolean, String>> validator = (items) -> {
            // Confirm that if a table is selected, its dependants are also selected
            for (Table table : items)
                for (Table related : table.getDependants())
                    if (!items.contains(related))
                        return Pair.of(false, String.format(
                                "Cannot clear %s without also clearing %s, " +
                                        "as it's related by key constraints.",
                                table, related
                        ));
            return Pair.of(true, "");
        };

        Function<List<Table>, String> confirmation = (items) -> {
            if (items.size() == Table.values().length)
                return "This will clear every table in the database.";
            else if (items.size() + 2 >= Table.values().length) {
                List<String> missing = Arrays.stream(Table.values())
                        .filter(t -> !items.contains(t)).map(Table::getTableName).toList();
                return "This will clear every table except " + Utils.joinList(missing) + ".";
            } else {
                return String.format("This will clear the %s %s.",
                        Utils.joinList(items.stream().map(Table::getTableName).toList()),
                        items.size() == 1 ? "table" : "tables"
                );
            }
        };

        List<Table> tables = Main.GUI.showPrompt(MultiSelectionPrompt.of(
                "Clear Table",
                "Select the tables to clear.",
                Arrays.stream(Table.values()).map(t -> Option.of(t.getTableName(), t)).toList(),
                validator,
                confirmation
        ));

        if (tables != null)
            clearTables(tables);
    }

    /**
     * Clear the specified {@link Table}. Log an info message for each cleared table.
     * <p>
     * This also resets the auto-increment counter to 1 for the table.
     *
     * @param tables The tables to clear. This must not be <code>null</code>, but it may include <code>null</code>
     *               elements in any order with duplicates. It is filtered and sorted before clearing.
     */
    private static void clearTables(@NotNull Collection<Table> tables) {
        try (Connection connection = Database.getConnection()) {
            tables.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparingInt(Table::ordinal).reversed())
                    .forEachOrdered(table -> {
                        try {
                            Statement statement = connection.createStatement();
                            statement.addBatch("DELETE FROM " + table.getTableName() + " WHERE true");
                            statement.addBatch("ALTER TABLE " + table.getTableName() + " AUTO_INCREMENT = 1");
                            int[] results = statement.executeBatch();
                            if (results[0] >= 0)
                                logger.info("Successfully cleared {} rows from {} table", results[0], table);
                            else if (results[0] == Statement.SUCCESS_NO_INFO)
                                logger.info("Successfully cleared table {}. Unknown row count", table);
                            else
                                logger.warn("Failed to clear table {}", table);

                            if (results[1] >= 0 || results[1] == Statement.SUCCESS_NO_INFO)
                                logger.debug(" - Reset auto increment to 1 for {} table", table);
                            else
                                logger.warn(" - Failed to reset auto increment to 1 for {} table", table);

                        } catch (SQLException e) {
                            logger.error("Failed to clear " + table + " table", e);
                        }
                    });
        } catch (SQLException e) {
            logger.error("Failed to establish database connection", e);
        }
    }

    /**
     * {@link #deleteTables() Delete} every table in the database and {@link #createTables() Recreate}
     * them from the <code>setup.sql</code> script.
     */
    private static void resetDatabase() {
        deleteTables();
        createTables();
        ConstructManager.saveToDatabase(OrganizationManager.ORGANIZATIONS, null);
    }

    /**
     * Delete every table in the database. Importantly, this is done in the reverse order of the {@link Table}
     * {@link Table#ordinal() declaration}.
     */
    public static void deleteTables() {
        try (Connection connection = Database.getConnection()) {
            logger.info("Deleting all SQL tables...");
            Table[] values = Table.values();
            for (int i = values.length - 1; i >= 0; i--) {
                Table table = values[i];
                PreparedStatement statement = connection.prepareStatement("DROP TABLE IF EXISTS ?");
                statement.setString(1, table.getTableName());
                statement.executeUpdate();
            }
            logger.info("Successfully deleted all tables");
        } catch (SQLException e) {
            logger.error("Failed to delete one or more tables", e);
        }
    }

    /**
     * Make sure all the tables in the database are present. Create any missing tables.
     */
    public static void createTables() {
        try (Connection connection = Database.getConnection()) {
            logger.info("Creating SQL tables from setup.sql");
            Utils.runSqlScript("setup.sql", connection);
        } catch (IOException e) {
            logger.error("Failed to load setup.sql script", e);
        } catch (SQLException e) {
            logger.error("Failed to create tables", e);
        }
    }
}
