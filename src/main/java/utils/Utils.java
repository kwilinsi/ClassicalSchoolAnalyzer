package utils;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * Download a website using JSoup. This effectively calls
     * <code>{@link Jsoup}{@link Jsoup#connect(String) .connect(url)}{@link Connection#get() .get();}</code>
     * <p>
     * However, this utility method also includes user-adjustable properties from the Config class to perform additional
     * tasks, such as setting the user agent timeout length.
     *
     * @param url The URL to download.
     *
     * @return The website as a Jsoup Document.
     * @throws IOException If there is an error downloading the website.
     */
    @NotNull
    public static Document download(@NotNull String url) throws IOException {
        Connection connection = Jsoup.connect(url);

        if (Config.USE_USERAGENT.getBool())
            try {
                connection.userAgent(Config.USERAGENT.get());
            } catch (NullPointerException e) {
                logger.warn("Failed to get the useragent property when requested. It was not set.", e);
            }

        try {
            connection.timeout(Config.CONNECTION_TIMEOUT.getInt());
        } catch (NullPointerException e) {
            logger.warn("Failed to get the timeout value. Reverting to default 30,000.", e);
        } catch (NumberFormatException e) {
            logger.warn(
                    "The timeout property must be a valid integer in milliseconds. Reverting to default 30,000.", e);
        } catch (IllegalArgumentException e) {
            logger.warn("The timeout must be non-negative. Reverting to default 30,000.", e);
        }

        try {
            return connection.get();
        } catch (HttpStatusException e) {
            // If strict HTTP handling is enabled, throw this error
            if (Config.STRICT_HTTP.getBool()) throw e;

            // Otherwise, retry the connection
            logger.debug("Encountered an HTTP error. Retrying connection", e);
            connection.ignoreHttpErrors(true);
            return connection.get();
        }
    }

    /**
     * Parse an HTML file using {@link Jsoup} to obtain a {@link Document}. The file is parsed with the UTF-8 charset.
     *
     * @param file The file to parse.
     * @param url  The URL corresponding to that file (used for resolving relative links).
     *
     * @return The parsed document.
     * @throws IOException If there is an error while parsing the file or the file doesn't exist.
     */
    @NotNull
    public static Document parseDocument(@NotNull File file, @NotNull String url) throws IOException {
        return Jsoup.parse(file, "UTF-8", url);
    }

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
                logger.warn("Failed to create installation/ directory.");
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
    public static void runSqlScript(String fileName, java.sql.Connection connection) throws IOException, SQLException {
        InputStream fileStream = Utils.class.getResourceAsStream("/sql/" + fileName);
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
