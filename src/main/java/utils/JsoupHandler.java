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
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("unused")
public class JsoupHandler {
    private static final Logger logger = LoggerFactory.getLogger(JsoupHandler.class);

    /**
     * {@link #download(String, DownloadConfig) Download} a {@link Document} from a URL with JSoup (or load from a
     * cache, if enabled). Save this document to the specified file, and then return it.
     *
     * @param url       The URL to download.
     * @param config    The configuration to use for how the download behaves.
     * @param cacheFile The path to the destination file for saving the document.
     *
     * @return The downloaded document.
     * @throws IOException If there is an error downloading or saving the website.
     */
    public static Document downloadAndSave(@NotNull String url,
                                           DownloadConfig config,
                                           @NotNull Path cacheFile) throws IOException {
        // Download the document
        Pair<Document, Boolean> download = downloadAndResults(url, config);

        // Save the document to the cache file, only if a cache wasn't used
        if (download.b)
            save(cacheFile, download.a);

        return download.a;
    }

    /**
     * Download a website using JSoup (or load from a cache, if {@link DownloadConfig#useCache enabled}). This
     * effectively calls
     * <code>{@link Jsoup}{@link Jsoup#connect(String) .connect(url)}{@link Connection#get() .get();}</code>
     * <p>
     * However, this utility method also includes user-adjustable properties from the {@link Config} and {@link
     * DownloadConfig DownloadConfig} classes to perform additional tasks, such as setting the user agent and timeout
     * duration.
     * <p>
     * Additionally, if <code>useCache</code> is enabled, the Cache table will be checked for a cached version of the
     * page before downloading it.
     *
     * @param url    The URL to download.
     * @param config Configuration to control the download behavior.
     *
     * @return The website as a Jsoup Document, paired with a boolean indicating whether the website was actually
     *         downloaded. It will be <code>true</code> if the website was downloaded from a server with Jsoup, and
     *         <code>false</code> if it was loaded from the cache.
     * @throws IOException If there is an error downloading the website.
     */
    @NotNull
    public static Pair<Document, Boolean> downloadAndResults(@NotNull String url,
                                                             @NotNull DownloadConfig config) throws IOException {
        // If caching is enabled, look for a cache
        if (config.useCache) {
            logger.debug("Searching for Cache of {}.", url);
            String path = null;
            try (java.sql.Connection connection = Database.getConnection()) {
                // Check Cache table for record with matching URL
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT file_path FROM Cache WHERE url = ?");
                statement.setString(1, url);
                ResultSet resultSet = statement.executeQuery();

                // If there's a match, attempt to parse the file
                if (resultSet.next()) {
                    path = resultSet.getString(1);
                    return new Pair<>(parse(new File(path), url), false);
                }
            } catch (SQLException e) {
                logger.warn("Failed to search Cache database for: " + url + ". Downloading with Jsoup instead.", e);
            } catch (IOException e) {
                logger.warn("Failed to parse a cached document: " + url + " at " + path +
                            ". Re-downloading with Jsoup instead.", e);
            }
        }

        // If caching is disabled or didn't work, download with JSoup
        logger.info("Downloading URL with Jsoup: {}.", url);

        Connection connection = Jsoup.connect(url);
        connection.ignoreContentType(config.ignoreContentType);

        // Enable user-agent, if specified in Config
        if (Config.USE_USERAGENT.getBool())
            try {
                connection.userAgent(Config.USERAGENT.get());
            } catch (NullPointerException e) {
                logger.warn("Failed to get the useragent property when requested. It was not set.", e);
            }

        // Set the timeout delay from Config
        try {
            connection.timeout(Config.CONNECTION_TIMEOUT.getInt());
        } catch (NullPointerException e) {
            logger.warn("Failed to get the timeout value. Reverting to default 30,000.", e);
        } catch (NumberFormatException e) {
            logger.warn(
                    "The timeout property must be a valid integer in milliseconds. Reverting to default 30,000.",
                    e);
        } catch (IllegalArgumentException e) {
            logger.warn("The timeout must be non-negative. Reverting to default 30,000.", e);
        }

        // Download the page
        try {
            return new Pair<>(connection.get(), true);
        } catch (HttpStatusException e) {
            // If there's an HTTP error and strict HTTP handling is enabled, throw it
            if (Config.STRICT_HTTP.getBool()) throw e;

            // Otherwise, log the error and retry the connection
            logger.debug("Encountered an HTTP error at URL " + url + ". Retrying connection", e);
            connection.ignoreHttpErrors(true);
            return new Pair<>(connection.get(), true);
        }
    }

    /**
     * Convenience method for {@link #downloadAndResults(String, DownloadConfig)} that drops the returned {@link
     * Boolean} indicating whether a cache was used.
     *
     * @param url    The URL to download.
     * @param config Configuration to control the download behavior.
     *
     * @return The website as a Jsoup Document.
     * @throws IOException If there is an error downloading the website.
     */
    @NotNull
    public static Document download(@NotNull String url,
                                    @NotNull DownloadConfig config) throws IOException {
        return downloadAndResults(url, config).a;
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
    public static Document parse(@NotNull File file, @NotNull String url) throws IOException {
        return Jsoup.parse(file, "UTF-8", url);
    }

    /**
     * Save the contents of an HTML {@link Document} to a file, and store the reference to this cache in the database.
     * <p>
     * This also saves a reference to the file in the database Cache, so it can be retrieved later.
     *
     * @param path     The path to the desired output file.
     * @param document The document to save.
     *
     * @throws IOException If an error occurs while saving the file.
     */
    public static void save(Path path, Document document) throws IOException {
        File file = path.toFile();

        // Create the file (and parent directories) if it doesn't exist
        try {
            if (!file.exists()) {
                logger.debug("Creating file " + file.getAbsolutePath());
                if (file.getParentFile().mkdirs())
                    logger.debug("Successfully created parent directory " + file.getParentFile().getAbsolutePath());
                if (!file.createNewFile())
                    throw new IOException("file.createNewFile returned false");
            }
        } catch (IOException | SecurityException e) {
            throw new IOException("Failed to create file " + file.getAbsolutePath() + ".", e);
        }

        // Write the document to the file
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println(document.outerHtml());
        } catch (IOException | SecurityException e) {
            throw new IOException("Failed to write to file " + file.getAbsolutePath() + ".", e);
        }

        logger.debug("Saved document to " + file.getAbsolutePath() + ". Adding reference to Cache table.");

        // Add a reference to the cache table
        try (java.sql.Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO Cache (url, file_path) VALUES (?, ?) ON DUPLICATE KEY UPDATE file_path = ?");
            statement.setString(1, document.baseUri());
            statement.setString(2, file.getAbsolutePath());
            statement.setString(3, file.getAbsolutePath());
            statement.execute();
        } catch (SQLException e) {
            logger.warn("Failed to add document reference to Cache table: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * This class is used for special configuration of {@link Jsoup} {@link Connection connections} while downloading.
     * <p>
     * Note that some configuration is controlled by the {@link Config} class, which reads from a separate file on
     * program startup.
     */
    public static class DownloadConfig {
        /**
         * Standardized config with the following settings:
         * <ul>
         *     <li>{@link #useCache}: <code>false</code></li>
         *     <li>{@link #ignoreContentType}: <code>false</code></li>
         * </ul>
         */
        public static final DownloadConfig DEFAULT = new DownloadConfig(false, false);

        /**
         * Standardized config with the following settings:
         * <ul>
         *     <li>{@link #useCache}: <code>true</code></li>
         *     <li>{@link #ignoreContentType}: <code>false</code></li>
         * </ul>
         */
        public static final DownloadConfig CACHE_ONLY = new DownloadConfig(true, false);

        /**
         * Standardized config with the following settings:
         * <ul>
         *     <li>{@link #useCache}: <code>true</code></li>
         *     <li>{@link #ignoreContentType}: <code>true</code></li>
         * </ul>
         */
        public static final DownloadConfig CACHE_AND_IGNORE_CONTENT_TYPE =
                new DownloadConfig(true, true);

        /**
         * Standardized config with the following settings:
         * <ul>
         *     <li>{@link #useCache}: <code>false</code></li>
         *     <li>{@link #ignoreContentType}: <code>true</code></li>
         * </ul>
         */
        public static final DownloadConfig IGNORE_CONTENT_TYPE_ONLY =
                new DownloadConfig(false, true);

        /**
         * If this is enabled, the download may not make a Jsoup request to the target server. Instead, it will first
         * check the Cache table in the {@link Database} for a cached version of the page. If a cached version isn't
         * found, <i>then</i> the page will be downloaded from the URL.
         * <p>
         * <b>Default:</b> <code>false</code>
         */
        private final boolean useCache;

        /**
         * See {@link Connection#ignoreContentType(boolean)}. This controls whether Jsoup should ignore the type of file
         * returned by the server and accept it regardless. If this is disabled, Jsoup will throw exceptions on
         * unrecognized content types.
         * <p>
         * <b>Default:</b> <code>false</code>
         */
        private final boolean ignoreContentType;

        /**
         * Initialize a {@link DownloadConfig}.
         *
         * @param useCache          See {@link #useCache}.
         * @param ignoreContentType See {@link #ignoreContentType}.
         */
        private DownloadConfig(boolean useCache, boolean ignoreContentType) {
            this.useCache = useCache;
            this.ignoreContentType = ignoreContentType;
        }

        /**
         * Create a new {@link DownloadConfig DownloadConfig} with custom settings.
         *
         * @param useCache          See {@link #useCache}.
         * @param ignoreContentType See {@link #ignoreContentType}.
         */
        public static DownloadConfig of(boolean useCache, boolean ignoreContentType) {
            return new DownloadConfig(useCache, ignoreContentType);
        }
    }
}
