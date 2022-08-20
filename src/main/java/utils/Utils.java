package utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * This is a list of all date formats that are recognized by {@link #parseDate(String)}.
     */
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("MM-dd-yy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yy/MM/dd"),
            DateTimeFormatter.ofPattern("yy-MM-dd")
    };

    /**
     * Get a file by its name in the installation directory. If the file does not exist, it is created.
     *
     * @param fileName The name of the file to retrieve.
     *
     * @return The file. If creating it failed, this may point to an empty path.
     */
    @NotNull
    public static File getInstallationFile(String fileName) {
        File installationDir = new File("installation");

        // If the installation directory doesn't exist, create it
        if (!installationDir.isDirectory()) {
            if (!installationDir.mkdir()) {
                // If creating the directory fails, log a warning and exit
                logger.warn("Failed to create installation directory.");
                return new File("");
            }
        }

        File newFile = new File(installationDir, fileName);

        // If the new file already exists, return it as-is
        if (newFile.exists())
            return newFile;

        // Otherwise, attempt to create it
        try {
            if (!newFile.createNewFile())
                logger.warn("Failed to create " + newFile.getAbsolutePath() + ".");
        } catch (IOException e) {
            logger.warn("Failed to create " + newFile.getAbsolutePath() + ".", e);
        }
        return newFile;
    }

    /**
     * Run one of the SQL scripts in the resources directory.
     *
     * @param fileName   The name of the script file to run.
     * @param connection The connection to the database.
     *
     * @throws IOException  If there is an error locating or reading the script file.
     * @throws SQLException If there is an error running the script.
     */
    public static void runSqlScript(String fileName, java.sql.Connection connection)
            throws IOException, SQLException {
        try (InputStream fileStream = Utils.class.getResourceAsStream("/sql/" + fileName)) {
            if (fileStream == null)
                throw new IOException("Failed to find SQL script " + fileName + ".");
            byte[] encoded = fileStream.readAllBytes();
            String[] sql = new String(encoded, StandardCharsets.UTF_8).split(";");
            Statement statement = connection.createStatement();
            for (String s : sql)
                statement.addBatch(s);
            statement.executeBatch();
        }
    }

    /**
     * Attempt to parse a string as a date. This will try a {@link #DATE_FORMATS barrage} of different date formats,
     * returning <code>null</code> if all of them fail.
     *
     * @param text The text to parse, or <code>null</code> to skip parsing and return <code>null</code>.
     *
     * @return The parsed date; or <code>null</code> if <code>text</code> is <code>null</code> or all parsing formats
     *         failed.
     */
    @Nullable
    public static LocalDate parseDate(@Nullable String text) {
        if (text == null) return null;

        // Try each of the formats in turn
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // Ignore errors and try another format
            }
        }

        // If all formats failed, log this un-parsable string and return null
        logger.info("Failed to parse String as date: " + text);
        return null;
    }

    /**
     * Clean a file name from illegal characters and optionally add an extension.
     *
     * @param file      The name of the file (not the full path or directory).
     * @param extension The extension to add, or <code>null</code> to skip adding an extension. Don't include a period
     *                  in the extension.
     *
     * @return The cleaned file name.
     */
    @NotNull
    public static String cleanFile(@NotNull String file, @Nullable String extension) {
        String clean = file.replaceAll("[:/*?\"<>|\\\\]", "");
        if (extension != null)
            return clean + "." + extension;
        return clean;
    }

    /**
     * I couldn't figure out how to do this in one line with {@link String#format(String, Object...)}, so I've written a
     * custom method to do it.
     * <p>
     * This takes some input string and forces it to be exactly <code>length</code> characters long. If the input is too
     * short, it is padded with spaces on the right (left-aligned).
     * <p>
     * If the string is too long, excess characters are taken off the end. If the <code>ellipsis</code> parameter is
     * <code>true</code>, an extra character is removed and replaced with an ellipsis, to indicate to the user that the
     * string was truncated.
     *
     * @param input    The input string to format.
     * @param length   The length of the desired output.
     * @param ellipsis Whether to add an ellipsis to the end of the string if it is too long.
     *
     * @return The formatted string.
     */
    @NotNull
    public static String padTrimString(@NotNull String input, int length, boolean ellipsis) {
        if (input.length() > length)
            return input.substring(0, length - (ellipsis ? 1 : 0)) + (ellipsis ? "\u2026" : "");
        else
            return String.format("%-" + length + "s", input);
    }

    /**
     * Generate the portion of a SQL {@link java.sql.PreparedStatement PreparedStatement} that contains the list of
     * arguments. That is, when you're creating a statement like "<code>INSERT INTO Table (col1, col2) VALUES (?,
     * ?);</code>", this function will generate the list of column names and the question marks for the values.
     * <p>
     * There are two modes for this function, determined by <code>isInsert</code>:
     * <ul>
     *     <li>If <code>isInsert</code> is <code>true</code>, the generated string will be for an INSERT statement.
     *     The provided column names will be enclosed in parentheses, and they are followed by "<code> VALUES
     *     </code>" and an equal number of question marks, also in parentheses. For example, in the following SQL
     *     statement, the underlined portion might be generated by this function:
     *     <br>
     *     <code>INSERT INTO MyTable <u>(foo, bar, lorem, ipsum) VALUES (?, ?, ?, ?)</u>;</code>
     *
     *     <li>If <code>isInsert</code> is <code>false</code>, the generated string will be for an UPDATE statement.
     *     Here the column names are paired with their corresponding question mark. For example, in the following SQL
     *     statement, the underlined portion might be generated by this function:
     *     <br>
     *     <code>UPDATE MyTable <u>SET foo = ?, bar = ?, hello = ?, world = ?</u> WHERE id = ?;</code>
     * </ul>
     *
     * @param columns  The list of zero or more column names. If this list is {@link Collection#isEmpty() empty}, an
     *                 empty string is returned.
     * @param isInsert <code>True</code> if the string should be generated for use in an INSERT statement;
     *                 <code>false</code> for use in an UPDATE statement.
     *
     * @return The generated string.
     */
    @NotNull
    public static String generateSQLStmtArgs(List<String> columns, boolean isInsert) {
        if (columns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        if (isInsert) {
            sb.append("(");
            for (String column : columns) sb.append(column).append(", ");
            sb.delete(sb.length() - 2, sb.length());
            sb.append(") VALUES (");
            sb.append("?, ".repeat(columns.size()));
            sb.delete(sb.length() - 2, sb.length());
            sb.append(")");
        } else {
            sb.append("SET ");
            for (String column : columns) sb.append(column).append(" = ?, ");
            sb.delete(sb.length() - 2, sb.length());
        }

        return sb.toString();
    }
}