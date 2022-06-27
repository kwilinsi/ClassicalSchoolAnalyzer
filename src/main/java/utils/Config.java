package utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;

public enum Config {

    USE_USERAGENT(true),

    USERAGENT("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit 537.36 (KHTML, like Gecko) " +
              "Chrome/102.0.5005.136 Safari/537.36"),

    CONNECTION_TIMEOUT(30000),

    STRICT_HTTP(false);

    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final String FILE_NAME = "config.properties";
    private final Object defaultValue;

    Config(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Get the value of some config setting. Initially, the config.properties resource file is checked. If the setting
     * is not there, its default value is used. If that default value is null, then the user is prompted to provide a
     * value for the setting, which is then stored in the config.properties file.
     *
     * @param config The config setting to retrieve.
     *
     * @return The current value of that setting.
     */
    @Nullable
    public static String getRaw(@NotNull Config config) {
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
        String prop = properties.getProperty(config.key());

        // If a matching property is found in the config file, return it
        if (prop != null) return prop;

        String val = String.valueOf(config.defaultValue);

        // If no value is found anywhere, ask the user to provide one
        if (val == null) {
            System.out.printf(
                    "Missing a required configuration setting. Please provide the following:%n%s = ",
                    config.key()
            );

            Scanner in = new Scanner(System.in);
            val = in.nextLine();
        }

        // Save the new value (whether from the Config enum itself or from the user) to the properties file
        properties.put(config.key(), val);
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
     * {@link #getRaw(Config) Get} a configuration setting, ensuring that it is not null. If it would otherwise be null,
     * a {@link NullPointerException} is thrown instead.
     *
     * @param config The configuration setting to retrieve.
     *
     * @return The value of that setting.
     * @throws NullPointerException If the setting is null.
     */
    @NotNull
    public static String get(@NotNull Config config) throws NullPointerException {
        String r = getRaw(config);
        if (r == null)
            throw new NullPointerException("Retrieved property " + config.key() + " was null.");
        return r;
    }

    /**
     * This is a convenience method for {@link #getRaw(Config)} that {@link Boolean#parseBoolean(String) parses} the
     * result to a boolean.
     *
     * @param config The configuration setting to retrieve.
     *
     * @return The value of that setting.
     */
    public static boolean getBool(@NotNull Config config) {
        return Boolean.parseBoolean(getRaw(config));
    }

    /**
     * This is a convenience method for {@link #get(Config)} that {@link Integer#parseInt(String) parses} the result to
     * an integer. It will throw an error if the property is null or not a valid integer.
     *
     * @param config The configuration setting to retrieve.
     *
     * @return The value of that setting.
     * @throws NullPointerException  If the value returned by {@link #getRaw(Config)} is <code>null</code>.
     * @throws NumberFormatException If the string property cannot be coerced into an integer.
     */
    public static int getInt(@NotNull Config config) throws NullPointerException, NumberFormatException {
        return Integer.parseInt(get(config));
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
