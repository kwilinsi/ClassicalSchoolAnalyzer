package utils;

import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;

public enum Config {

    // Jsoup configuration
    /**
     * Whether to use the {@link #USERAGENT} when making {@link Jsoup} requests.
     * <p>
     * <b>Default:</b> <code>true</code>
     */
    USE_USERAGENT(true),

    /**
     * The user agent to {@link #USE_USERAGENT use} when making {@link Jsoup} requests.
     * <p>
     * <b>Default:</b> <code>"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)
     * Chrome/74.0.3729.169 Safari/537.36"</code>
     */
    USERAGENT("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit 537.36 (KHTML, like Gecko) " +
            "Chrome/102.0.5005.136 Safari/537.36"),

    /**
     * The timeout in milliseconds to wait for a {@link Jsoup} request to complete.
     * <p>
     * <b>Default:</b> <code>30000</code>
     */
    CONNECTION_TIMEOUT(30000),

    /**
     * Whether {@link Jsoup} should be allowed to use {@link Connection#ignoreHttpErrors(boolean)} when making requests.
     * If this is <code>true</code>, all http errors will be thrown; otherwise, they're merely logged.
     * <p>
     * <b>Default:</b> <code>false</code>
     */
    STRICT_HTTP(false),

    // Database configuration
    /**
     * The ip address of the MySQL database.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATABASE_IP(null),

    /**
     * The port for connections to the MySQL database.
     * <p>
     * <b>Default:</b> <code>3306</code>
     */
    DATABASE_PORT(3306),

    /**
     * The name of the database in the MySQL server.
     * <p>
     * <b>Default:</b> <code>"classical"</code>
     */
    DATABASE_NAME("classical"),

    /**
     * The username to use when connecting to the MySQL database.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATABASE_USERNAME(null),

    /**
     * The password to use when connecting to the MySQL database.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATABASE_PASSWORD(null),

    // General configuration

    /**
     * The path to the directory where downloaded files pertaining to this project are stored.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATA_DIRECTORY(null),

    /**
     * The name to use for HTML files from each organization that contain their school list, when downloading them to
     * the {@link #DATA_DIRECTORY}.
     * <p>
     * <b>Default:</b> <code>"school_list.html"</code>
     */
    SCHOOL_LIST_FILE_NAME("school_list.html"),

    /**
     * The maximum number of threads to use while downloading school pages from each organization.
     * <p>
     * <b>Default:</b> <code>15</code>
     */
    MAX_THREADS_ORGANIZATIONS(15),

    /**
     * The default name to assign to schools whose name can't be determined.
     * <p>
     * <b>Default:</b> <code>"MISSING NAME"</code>
     */
    MISSING_NAME_SUBSTITUTION("MISSING NAME"),

    /**
     * The path to the Python executable that parses addresses with <code>usaddress-scourgify</code>.
     * <p>
     * This path is used by {@link processing.schoolLists.matching.AddressParser AddressParser} to interface
     * with the python executable.
     * <p>
     * <b>Default:</b> <code>"external/address/dist/address-parser.exe"</code> (with platform-dependent slashes)
     */
    PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH(
            Paths.get("external", "address", "dist", "address.exe").toAbsolutePath()
    ),

    /**
     * This is the maximum number of {@link constructs.school.Attribute Attributes} that will be shown for a school when
     * putting it in a {@link gui.windows.prompt.schoolMatch.SchoolMatchDisplay SchoolMatchDisplay} GUI window.
     * <p>
     * Note that this is a soft maximum. If it's necessary to show more attributes, because there are some that
     * necessitate user input, those will still be shown.
     * <p>
     * This should never be larger than the number of {@link Attribute} enums. To disable the limit altogether, set
     * this to <code>-1</code>. To use the fewest possible attributes in all cases, set this to <code>0</code>.
     * <p>
     * <b>Default:</b> <code>15</code>
     */
    MAX_SCHOOL_COMPARISON_GUI_ATTRIBUTES(15);

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    /**
     * The name of the file that stores these configuration settings.
     */
    private static final String FILE_NAME = "config.properties";

    /**
     * The default value for each configuration setting.
     */
    @Nullable
    private final Object defaultValue;

    /**
     * Instantiate a new {@link Config} setting with the given default value.
     *
     * @param defaultValue The {@link #defaultValue}.
     */
    Config(@Nullable Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * {@link #getRaw() Get} a configuration setting, ensuring that it is not null. If it would otherwise be null, a
     * {@link NullPointerException} is thrown instead.
     *
     * @return The value of that setting.
     * @throws NullPointerException If the setting is null.
     */
    @NotNull
    public String get() throws NullPointerException {
        String r = this.getRaw();
        if (r == null)
            throw new NullPointerException("Retrieved property " + this.key() + " was null.");
        return r;
    }

    /**
     * This is a convenience method for {@link #getRaw()} that {@link Boolean#parseBoolean(String) parses} the result to
     * a boolean.
     *
     * @return The value of that setting.
     */
    public boolean getBool() {
        return Boolean.parseBoolean(this.getRaw());
    }

    /**
     * This is a convenience method for {@link #get()} that {@link Integer#parseInt(String) parses} the result to an
     * integer. It will throw an error if the property is null or not a valid integer.
     *
     * @return The value of that setting.
     * @throws NullPointerException  If the value returned by {@link #getRaw()} is <code>null</code>.
     * @throws NumberFormatException If the string property cannot be coerced into an integer.
     */
    public int getInt() throws NullPointerException, NumberFormatException {
        return Integer.parseInt(this.get());
    }

    /**
     * Get the value of this config setting. Initially, the config.properties resource file is checked. If the setting
     * is not there, its default value is used. If that default value is null, then the user is prompted to provide a
     * value for the setting, which is then stored in the config.properties file.
     *
     * @return The current value of this setting.
     */
    @Nullable
    public String getRaw() {
        File configFile = Utils.getInstallationFile(FILE_NAME);
        Properties properties = new Properties();

        try {
            properties.load(new FileInputStream(configFile));
        } catch (IOException e) {
            logger.error("Failed to load " +
                    configFile.getAbsolutePath() + " to Properties object. Everything will now break.", e);
            return null;
        }

        // Check the properties file for the config setting
        String prop = properties.getProperty(this.key());

        // If a matching property is found in the config file, return it
        if (prop != null) return prop;

        String val = String.valueOf(this.defaultValue);

        // TODO update this to use the new prompt interface with the GUI

        // If no value is found anywhere, ask the user to provide one
        if (this.defaultValue == null) {
            System.out.printf(
                    "Missing a required configuration setting. Please provide the following:%n%s = ",
                    this.key()
            );

            Scanner in = new Scanner(System.in);
            val = in.nextLine();
        }

        // Save the new value (whether from the Config enum itself or from the user) to the properties file
        properties.put(this.key(), val);
        logger.debug("Saving properties to " + configFile.getAbsolutePath() + ".");

        OutputStream out;
        try {
            out = new FileOutputStream(configFile);
        } catch (FileNotFoundException e) {
            logger.warn("Failed to create output stream for " +
                    configFile.getAbsolutePath() + ". Couldn't save properties.", e);
            return val;
        }

        try {
            properties.store(out, null);
        } catch (IOException e) {
            logger.warn("Failed to save properties to " + configFile.getAbsolutePath() + ".", e);
        }

        return val;
    }

    /**
     * Return the name of this config setting in all lowercase. This is used as a key in the properties file.
     *
     * @return The key.
     */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

}
