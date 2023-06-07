package utils;

import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

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
     * @return The parsed date; or <code>null</code> if <code>text</code> is <code>null</code> or all parsing formats
     * failed.
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
     * @return The formatted string.
     */
    @NotNull
    public static String padTrimString(@NotNull String input, int length, boolean ellipsis) {
        if (input.length() > length)
            return input.substring(0, length - (ellipsis ? 1 : 0)) + (ellipsis ? "…" : "");
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

    /**
     * Attempt to find any of the search strings in the base string using {@link String#startsWith(String)}. The
     * first matching search string is returned. If there are no matches, <code>null</code> is returned.
     *
     * @param base     The string to search.
     * @param searches The search strings to look for in the start of the base string.
     * @return The first identified search string, or <code>null</code> if there are no matches.
     */
    @Nullable
    public static String startsWithAny(String base, Collection<String> searches) {
        for (String search : searches)
            if (base.startsWith(search))
                return search;
        return null;
    }

    /**
     * Deletes the files specified by the given file path strings.
     * Logs the result of each deletion using the {@link #logger}.
     *
     * @param filePaths The paths of the files to be deleted.
     */
    public static void deleteFiles(@Nullable String... filePaths) {
        if (filePaths == null)
            return;

        for (String filePath : filePaths) {
            if (filePath == null) continue;

            try {
                File file = new File(filePath);
                if (file.exists()) {
                    if (file.delete())
                        logger.debug("Deleted file '{}'", filePath);
                    else
                        logger.debug("Failed to delete '{}'", filePath);
                } else {
                    logger.debug("Cannot delete '{}' as it doesn't exist", filePath);
                }
            } catch (SecurityException e) {
                logger.debug("Failed to delete '{}' due to SecurityException: {}", filePath, e.getMessage());
            }
        }
    }

    /**
     * Convert the given string to title case, where the first letter of every word is capitalized.
     *
     * @param str The string to convert.
     * @return The converted string, or <code>null</code> if the input was <code>null</code>.
     */
    @Nullable
    public static String titleCase(@Nullable String str) {
        if (str == null) return null;

        String[] words = str.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() == 0) continue;
            words[i] = words[i].substring(0, 1).toUpperCase(Locale.ROOT) +
                    words[i].substring(1).toLowerCase(Locale.ROOT);
        }

        return String.join(" ", words);
    }

    /**
     * Package a stacktrace (a set of {@link StackTraceElement StackTraceElements}) in a {@link Throwable}. This is
     * useful for logging the stacktrace without needing a <code>try-catch</code> block.
     *
     * @param stackTrace The stacktrace elements.
     * @return The throwable.
     */
    public static Throwable packageStackTrace(StackTraceElement[] stackTrace) {
        Throwable throwable = new Throwable();
        throwable.setStackTrace(stackTrace);
        return throwable;
    }

    /**
     * Extract the {@link Throwable#getStackTrace() stacktrace} from a {@link Throwable} and convert it to a string.
     *
     * @param throwable The throwable from which to get the stacktrace.
     * @return The stacktrace as a string.
     */
    public static String getStackTraceAsString(@NotNull Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Join a set of arguments as if they were terminal commands, putting them in a single string. This is not secure
     * like the actual {@link ProcessBuilder}, as it will not escape characters or anything. It is only for
     * approximate reference in log statements. Instead, it does the following:
     * <ul>
     *     <li>{@link #cleanLineBreaks(String) Replace} any line breaks with the literal string <code>"\n"</code>.
     *     <li>Skip <code>null</code> and empty arguments.
     *     <li>Surround every argument that contains spaces with quotations.
     *     <li>Combine all arguments with a single space between them.
     * </ul>
     *
     * @param args The list of terminal arguments, which may be <code>null</code>.
     * @return The combined command, or an empty string if the input is <code>null</code>.
     */
    @NotNull
    public static String joinCommand(@Nullable List<String> args) {
        StringJoiner s = new StringJoiner(" ");

        if (args != null)
            for (String arg : args) {
                if (arg == null || arg.isEmpty()) continue;

                arg = cleanLineBreaks(arg);

                if (arg.matches("\\s"))
                    arg = "\"" + arg + "\"";

                s.add(arg);
            }

        return s.toString();
    }

    /**
     * Clean a string being printed to the console by replacing any line breaks with the literal string
     * <code>"\n"</code>.
     *
     * @param input The text to clean.
     * @return The cleaned text.
     */
    @Nullable
    public static String cleanLineBreaks(@Nullable String input) {
        if (input == null)
            return null;
        else
            //noinspection RedundantEscapeInRegexReplacement
            return input.replaceAll("\\n", "\\n");
    }

    /**
     * Given a {@link List} of {@link Attribute Attributes}, combine their {@link Attribute#name names} into a single
     * user-readable string separated by commas with the word "and" between the last two. This includes the Oxford
     * comma.
     * <p>
     * The order of the input list is preserved, along with duplicates. However, <code>null</code> elements are skipped.
     *
     * @param attributes The list of attributes to combine.
     * @return The user-readable string (an empty string if the list is empty).
     */
    @NotNull
    public static String listAttributes(@NotNull List<Attribute> attributes) {
        List<String> names = attributes.stream().filter(Objects::nonNull).map(Attribute::name).toList();

        if (names.size() == 0)
            return "";
        else if (names.size() == 1)
            return names.get(0);
        else if (names.size() == 2)
            return names.get(0) + " and " + names.get(1);
        else
            return String.join(", ", names.subList(0, names.size() - 1)) +
                    ", and " + names.get(names.size() - 1);
    }
}
