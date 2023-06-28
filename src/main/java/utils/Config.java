package utils;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import gui.windows.schoolMatch.SchoolMatchDisplay;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

public enum Config {
    // Jsoup configuration
    /**
     * Whether to use the {@link #USERAGENT} when making {@link Jsoup} requests.
     * <p>
     * <b>Default:</b> <code>true</code>
     */
    USE_USERAGENT(true, false),

    /**
     * The user agent to {@link #USE_USERAGENT use} when making {@link Jsoup} requests.
     * <p>
     * <b>Default:</b> <code>"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)
     * Chrome/74.0.3729.169 Safari/537.36"</code>
     */
    USERAGENT("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit 537.36 (KHTML, like Gecko) " +
            "Chrome/102.0.5005.136 Safari/537.36", false),

    /**
     * The timeout in milliseconds to wait for a {@link Jsoup} request to complete.
     * <p>
     * <b>Default:</b> <code>30000</code>
     */
    CONNECTION_TIMEOUT(30000, false),

    /**
     * Whether {@link Jsoup} should be allowed to use {@link Connection#ignoreHttpErrors(boolean)} when making requests.
     * If this is <code>true</code>, all http errors will be thrown; otherwise, they're merely logged.
     * <p>
     * <b>Default:</b> <code>false</code>
     */
    STRICT_HTTP(false, false),

    // Database configuration
    /**
     * The ip address of the MySQL database.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATABASE_IP(null, false),

    /**
     * The port for connections to the MySQL database.
     * <p>
     * <b>Default:</b> <code>3306</code>
     */
    DATABASE_PORT(3306, false),

    /**
     * The name of the database in the MySQL server.
     * <p>
     * <b>Default:</b> <code>"classical"</code>
     */
    DATABASE_NAME("classical", false),

    /**
     * The username to use when connecting to the MySQL database.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATABASE_USERNAME(null, false),

    /**
     * The password to use when connecting to the MySQL database.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATABASE_PASSWORD(null, true),

    // General configuration

    /**
     * The path to the directory where downloaded files pertaining to this project are stored.
     * <p>
     * <b>Default:</b> <code>null</code>
     */
    DATA_DIRECTORY(null, false),

    /**
     * The name to use for HTML files from each organization that contain their school list, when downloading them to
     * the {@link #DATA_DIRECTORY}.
     * <p>
     * <b>Default:</b> <code>"school_list.html"</code>
     */
    SCHOOL_LIST_FILE_NAME("school_list.html", false),

    /**
     * The maximum number of threads to use while downloading school pages from each organization.
     * <p>
     * <b>Default:</b> <code>15</code>
     */
    MAX_THREADS_ORGANIZATIONS(15, false),

    /**
     * The default name to assign to schools whose name can't be determined.
     * <p>
     * <b>Default:</b> <code>"MISSING NAME"</code>
     */
    MISSING_NAME_SUBSTITUTION("MISSING NAME", false),

    /**
     * The path to the Python executable that parses addresses with <code>usaddress-scourgify</code>.
     * <p>
     * This path is used by {@link processing.schoolLists.matching.AddressParser AddressParser} to interface
     * with the python executable.
     * <p>
     * <b>Default:</b> <code>"external/address/dist/address-parser.exe"</code> (with platform-dependent slashes)
     */
    PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH(
            Paths.get("external", "address", "dist", "address.exe").toAbsolutePath(), false
    ),

    /**
     * The name of the file that stores a list pre-normalized addresses. This allows the user to provide normalized
     * values for addresses that would otherwise be un-parseable.
     * <p>
     * Note that this should not include the <code>.json</code> file extension.
     * <p>
     * <b>Default:</b> <code>"normalized_address_cache"</code>
     */
    PYTHON_ADDRESS_PARSER_NORMALIZATION_LIST_FILE_NAME("normalized_address_cache", false),

    /**
     * This is the maximum number of {@link constructs.school.Attribute Attributes} that will be shown for a school when
     * putting it in a {@link SchoolMatchDisplay SchoolMatchDisplay} GUI window.
     * <p>
     * Note that this is a soft maximum. If it's necessary to show more attributes, because there are some that
     * necessitate user input, those will still be shown.
     * <p>
     * This should never be larger than the number of {@link constructs.school.Attribute Attribute} enums. To disable
     * the limit altogether, set this to <code>-1</code>. To use the fewest possible attributes in all cases, set
     * this to <code>0</code>.
     * <p>
     * <b>Default:</b> <code>15</code>
     */
    MAX_SCHOOL_COMPARISON_GUI_ATTRIBUTES(15, false),

    /**
     * The maximum number of characters to print in a GUI window displaying the value of some
     * {@link constructs.school.Attribute Attribute}.
     * <p>
     * To disable the limit altogether, set this to <code>-1</code>. Do <b>not</b> set this to any value between 0
     * and 3, inclusive. This will cause {@link org.apache.commons.lang3.StringUtils#abbreviate(String, int)
     * StringUtils.abbreviate()} to throw an {@link IllegalArgumentException}.
     * <p>
     * <b>Default:</b> <code>50</code>
     */
    MAX_ATTRIBUTE_DISPLAY_LENGTH(50, false),

    /**
     * This is the typical domain used by {@link constructs.organization.OrganizationManager#GHI GHI} schools for their
     * websites. The schools each have a subdomain on this page. This is relavent when setting District
     * {@link constructs.district.District#getWebsiteURL() website URLs}. See
     * {@link processing.schoolLists.matching.MatchIdentifier MatchIdentifier}.
     * <p>
     * <b>Default:</b> <code>"greatheartsamerica.org"</code>
     */
    @SuppressWarnings("SpellCheckingInspection")
    STANDARD_GHI_WEBSITE_DOMAIN("greatheartsamerica.org", false),

    /**
     * The maximum number of characters per line in a GUI popup message that respects
     * {@link gui.utils.GUIUtils#wrapLabelText(String)}.
     * <p>
     * <b>Default:</b> <code>75</code>
     */
    GUI_POPUP_TEXT_WRAP_LENGTH(75, false);

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    /**
     * The name of the file that stores these configuration settings.
     * <p>
     * Note that this file is cached via {@link #PROPERTIES}, and thus it cannot be safely modified manually during
     * program execution. Changes will either be ignored, overwritten, or both.
     */
    private static final String FILE_NAME = "config.properties";

    /**
     * This is a cache of the {@link Properties} file obtained from {@link #FILE_NAME config.properties} by
     * {@link #getRaw()} upon requesting a config setting.
     */
    private static Properties PROPERTIES;

    /**
     * The default value for each configuration setting.
     */
    @Nullable
    private final Object defaultValue;

    /**
     * If this is <code>true</code>, then if the user is prompted to enter a value for it via {@link #getRaw()}, the
     * input box will be configured for {@link TextInputDialogBuilder#setPasswordInput(boolean) password} input.
     */
    private final boolean isPassword;

    /**
     * Instantiate a new {@link Config} setting.
     *
     * @param defaultValue The {@link #defaultValue}.
     * @param isPassword   If it {@link #isPassword}.
     */
    Config(@Nullable Object defaultValue, boolean isPassword) {
        this.defaultValue = defaultValue;
        this.isPassword = isPassword;
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
     * Get the value of this config setting.
     * <p>
     * If the config {@link #PROPERTIES} are already loaded (i.e. not <code>null</code>), the value is retrieved from
     * there.
     * <p>
     * Otherwise, the {@link #FILE_NAME config.properties} resource file is
     * {@link Utils#getInstallationFile(String) loaded} and cached. If the setting is not there, its default value is
     * used. If that default value is null, then the user is prompted to provide a value for the setting, which is
     * then stored in the <code>config.properties</code> file.
     *
     * @return The current value of this setting.
     * @throws RuntimeException If an error occurs while opening the properties file.
     */
    @Nullable
    public String getRaw() {
        // If the properties file isn't cached yet, cache it now
        if (PROPERTIES == null || PROPERTIES.size() == 0) {
            File configFile = Utils.getInstallationFile(FILE_NAME);
            PROPERTIES = new Properties();

            try {
                PROPERTIES.load(new FileInputStream(configFile));
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to load " + configFile.getAbsolutePath() + " to Properties object.", e
                );
            }
        }

        // Check the properties file for the config setting
        String prop = PROPERTIES.getProperty(this.key());

        // If a matching property is found in the config file, return it
        if (prop != null) return prop;

        // Otherwise, use the default value. If that doesn't exist, ask the user to provide one
        File configFile = Utils.getInstallationFile(FILE_NAME);
        String val;

        if (this.defaultValue == null) {
            // Set the prompt size to match the width of the description string
            String description = "\nMissing a required configuration setting.\nPlease provide the " + this.key() + ":";
            int width = Arrays.stream(description.split("\n")).mapToInt(String::length).max().orElse(40);

            val = Main.GUI.textDialog(new TextInputDialogBuilder()
                    .setTitle("Configuration Required")
                    .setDescription(description)
                    .setPasswordInput(this.isPassword)
                    .setTextBoxSize(new TerminalSize(width, isPassword ? 1 : 3))
                    .build()
            );
        } else {
            val = String.valueOf(this.defaultValue);
        }

        // Save the new value (whether from the Config enum itself or from the user) to the properties file
        try {
            PROPERTIES.put(this.key(), val);
            logger.debug("Saving properties to " + configFile.getAbsolutePath() + ".");
            OutputStream out = new FileOutputStream(configFile);
            PROPERTIES.store(out, null);
        } catch (FileNotFoundException e) {
            logger.warn("Failed to create output stream for " + configFile.getAbsolutePath() +
                    ". Couldn't save properties.", e);
        } catch (IOException e) {
            logger.warn("Failed to store updated config properties to " + configFile.getAbsolutePath(), e);
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
