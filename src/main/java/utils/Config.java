package utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;

public enum Config {

    USE_USERAGENT(true),
    USERAGENT("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit 537.36 (KHTML, like Gecko) " +
              "Chrome/102.0.5005.136 Safari/537.36");

    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final String FILE_NAME = "/config.properties";
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
    public static String get(@NotNull Config config) {
        InputStream configStream = Config.class.getResourceAsStream(FILE_NAME);
        Properties properties = new Properties();

        if (configStream == null) {
            logger.error("Missing " + FILE_NAME + " resource file. Everything will now break.");
            return null;
        } else {
            try {
                properties.load(configStream);
            } catch (IOException e) {
                logger.error("Failed to load " + FILE_NAME, e);
                return null;
            }
        }

        // Check the properties file for the config setting
        String prop = properties.getProperty(config.key());

        if (prop != null)
            return prop;

        // If it's not found there, check for a default value
        if (config.defaultValue != null)
            return String.valueOf(config.defaultValue);

        // If no value is found anywhere, ask the user to provide one
        System.out.printf(
                "Missing a required configuration setting. Please provide the following:%n%s = ",
                config.key()
        );

        Scanner in = new Scanner(System.in);
        String val = in.nextLine();
        properties.put(config.key(), val);

        try {
            File file = new File(Objects.requireNonNull(Config.class.getResource(FILE_NAME)).toURI());
            properties.store(new FileOutputStream(file), null);
        } catch (URISyntaxException | FileNotFoundException | NullPointerException e) {
            logger.error("Failed to locate " + FILE_NAME + " file to save changes.", e);
        } catch (IOException e) {
            logger.error("Failed to save properties", e);
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
