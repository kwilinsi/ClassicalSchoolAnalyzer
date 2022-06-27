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

        if (Config.getBool(Config.USE_USERAGENT))
            try {
                connection.userAgent(Config.get(Config.USERAGENT));
            } catch (NullPointerException e) {
                logger.warn("Failed to get the useragent property when requested. It was not set.", e);
            }

        try {
            connection.timeout(Config.getInt(Config.CONNECTION_TIMEOUT));
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
            if (Config.getBool(Config.STRICT_HTTP)) throw e;

            // Otherwise, retry the connection
            logger.debug("Encountered an HTTP error. Retrying connection", e);
            connection.ignoreHttpErrors(true);
            return connection.get();
        }
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
}
