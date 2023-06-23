package processing.schoolLists.matching;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Utils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

/**
 * This class is designed to interface between this Java program and the python script used to parse and
 * compare addresses.
 * <p>
 * The Python address parser is compiled to an executable and built on the Python library
 * <a href="https://github.com/GreenBuildingRegistry/usaddress-scourgify">usaddress-scourgify</a>.
 * The executable is located at {@link Config#PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH}.
 */
public class AddressParser {
    private static final Logger logger = LoggerFactory.getLogger(AddressParser.class);

    /**
     * This is the parameterized {@link TypeToken} for a {@link HashMap} mapping strings. It's used when decoding
     * output from the python library via {@link Gson#fromJson(Reader, Type) Gson}.
     *
     * @see #LIST_MAP_TYPE
     */
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, String>>() {
    }.getType();

    /**
     * This is the parameterized {@link TypeToken} for a {@link List} of {@link HashMap HashMaps}.
     *
     * @see #MAP_TYPE
     */
    private static final Type LIST_MAP_TYPE = new TypeToken<List<HashMap<String, String>>>() {
    }.getType();

    /**
     * Pass some arguments to the Python address parser at {@link Config#PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH},
     * creating and returning a new {@link Process}.
     *
     * @param args The arguments to pass to the process.
     * @return The new process.
     * @throws IOException If an error occurs creating the process.
     */
    @NotNull
    private static Process newProcess(@NotNull String... args) throws IOException {
        String[] allArgs = new String[args.length + 1];
        allArgs[0] = Config.PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH.get();
        System.arraycopy(args, 0, allArgs, 1, args.length);
        ProcessBuilder builder = new ProcessBuilder(allArgs);
        logger.trace("Calling python address parser: {}'", Utils.joinCommand(builder.command()));
        return builder.start();
    }

    /**
     * {@link #newProcess(String...) Create} a new bulk process with a specified command arguments that runs on an
     * entire file. Run the process, and return the resulting output file path.
     * <p>
     * If the process encounters an error, log it to the console, and return <code>null</code>.
     *
     * @param args The command arguments to run.
     * @return The path of the output file, if it exists. If an error occurred, this is <code>null</code>.
     */
    @Nullable
    private static String runBulkProcess(@NotNull String... args) {
        try {
            Process process = newProcess(args);
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            Map<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            if (map == null) {
                logger.warn(
                        "Unreachable state: map with python parser output is null.\n" +
                                "Input: " + process.info().commandLine().orElse(String.join(" ", args)),
                        Utils.packageStackTrace(Thread.currentThread().getStackTrace()));
                return null;
            }

            reader.close();
            if (map.containsKey("error"))
                //noinspection UnnecessaryUnicodeEscape
                logger.error("{} \u2014 {}: {}", map.get("error"), map.get("message"), map.get("stacktrace"));
            return map.get("output_file");
        } catch (IOException e) {
            logger.error("Failed to run the python address parser and read its output. " +
                    "Did you compile the executable?", e);
            return null;
        }
    }

    /**
     * Normalize a single address.
     * <p>
     * For more information, and to use the more efficient bulk normalization process, see {@link #normalize(List)}.
     *
     * @param address The address to normalize.
     * @return A map containing the parsed and normalized address information. The normalized address is retrievable
     * via the <code>"normalized"</code> key.
     */
    @Nullable
    public static Map<String, String> normalize(@Nullable String address) {
        logger.debug("Normalizing single address '{}'", address);

        try {
            Process process = newProcess("normalize", address == null ? "null" : address);
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            Map<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            reader.close();

            if (map.containsKey("error")) {
                logger.warn("Error normalizing address '{}'", address);
                logError(map.get("error"));
                return null;
            } else {
                return map;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bulk normalize many addresses at the same time. This is significantly more efficient, as it minimizes the
     * number of separate calls to the Python script, which has a slow start-up time.
     * <p>
     * Addresses that fail the normalization process will be {@link #logger logged} to the console and returned as
     * <code>null</code>.
     *
     * @param addresses The list of addresses to normalize.
     * @return A list of all the normalization information returned by the address parsing script. If a fatal
     * occurs, this is an empty, immutable list.
     */
    @NotNull
    public static List<Map<String, String>> normalize(@NotNull List<String> addresses) {
        logger.debug("Running bulk normalization on {} address{}", addresses.size(), addresses.size() == 1 ? "" : "es");
        String path = saveToFile(addresses, "addresses");

        if (path == null)
            return List.of();

        String outputPath = runBulkProcess("normalize_file", path);

        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            List<Map<String, String>> maps = new Gson().fromJson(reader, LIST_MAP_TYPE);
            if (maps == null)
                logger.error("Failed to read JSON from {}", outputPath);
            else if (maps.size() != addresses.size())
                logger.error("Got {} normalized addresses, but input had {} addresses", maps.size(), addresses.size());
            else
                return maps;

            return List.of();
        } catch (IOException e) {
            logger.error("Failed to read " + outputPath + " JSON data", e);
            return List.of();
        } finally {
            Utils.deleteFiles(path, outputPath);
        }
    }

    /**
     * Normalize a single address.
     * <p>
     * For more information, and to use the more efficient bulk normalization process, see
     * {@link #normalizeAddress(List)}.
     *
     * @param address The address to normalize.
     * @return The normalized address.
     */
    @Nullable
    public static String normalizeAddress(@Nullable String address) {
        // Not strictly necessary, but significantly faster for the relatively likely null input
        if (address == null) return null;

        Map<String, String> normalized = normalize(address);
        if (normalized == null)
            return null;
        else
            return normalized.get("normalized");
    }

    /**
     * Bulk normalize many addresses at the same time. This is significantly more efficient, as it minimizes the
     * number of separate calls to the python script, which has a slow start-up time.
     * <p>
     * Addresses that fail the normalization process will be {@link #logger logged} to the console and returned as
     * <code>null</code>.
     *
     * @param addresses The list of addresses to normalize.
     * @return A normalized list of addresses in the same order as the input. If a fatal occurs, this is an empty,
     * immutable list.
     */
    @NotNull
    public static List<String> normalizeAddress(@NotNull List<String> addresses) {
        List<Map<String, String>> normalized = normalize(addresses);
        if (normalized.size() == 0) return List.of();

        List<String> output = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < normalized.size(); i++) {
            Map<String, String> map = normalized.get(i);
            if (map == null) {
                logger.warn("Unexpected null normalization map for address '{}'", addresses.get(i));
                output.add(null);
            } else {
                String norm = map.get("normalized");
                if (map.containsKey("error")) {
                    logger.warn("Error normalizing address '{}'", addresses.get(i));
                    logError(map.get("error"));
                }

                output.add(norm);
                if (norm != null)
                    successCount++;
            }
        }

        logger.debug("Successfully normalized {}/{} addresses from {} inputs",
                successCount, addresses.size(), output.size());

        return output;
    }

    /**
     * Normalize a single city or state value.
     * <p>
     * For more information, and to use the more efficient bulk normalization process, see
     * {@link #normalizeCityState(Attribute, List, List)}.
     *
     * @param attribute This must be either {@link Attribute#city city} or {@link Attribute#state state}.
     * @param value     The city or state value or normalize.
     * @param address   An optional address to assist in the normalization process.
     * @return The normalized city or state value.
     */
    @Nullable
    public static String normalizeCityState(@NotNull Attribute attribute,
                                            @Nullable String value,
                                            @Nullable String address) {
        logger.debug("Normalizing single {} '{}' with address {}", attribute.name(), value, address);

        try {
            Process process = newProcess(
                    "normalize_" + attribute.name() + "_file",
                    value == null ? "null" : value,
                    address == null ? "null" : address
            );
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            Map<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            reader.close();

            if (map.containsKey("error")) {
                logger.warn("Error normalizing {} '{}'", attribute.name(), value);
                logError(map.get("error"));
                return null;
            } else {
                return map.get("normalized");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Normalize the {@link Attribute#city city} and {@link Attribute#state state} attributes, both of which are
     * related to the {@link Attribute#address address}.
     *
     * @param attribute The particular attribute to normalize (must be the city or state).
     * @param addresses The list of addresses for a set of schools.
     * @param values    The current values of the <code>attribute</code> for each school.
     * @return A list of normalized values, or an empty, immutable list, if some fatal error occurs.
     * @throws IllegalArgumentException If the length of the addresses and values lists are not the same.
     */
    @NotNull
    public static List<String> normalizeCityState(@NotNull Attribute attribute,
                                                  @NotNull List<String> addresses,
                                                  @NotNull List<String> values) throws IllegalArgumentException {
        if (values.size() != addresses.size())
            throw new IllegalArgumentException(String.format(
                    "Array size mismatch. Got %d for address and %d for %s",
                    addresses.size(), values.size(), attribute.name()
            ));

        List<Map<String, String>> inputData = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            Map<String, String> map = new HashMap<>();
            map.put("value", values.get(i));
            map.put("address", addresses.get(i));
            inputData.add(map);
        }

        String path = saveToFile(inputData, attribute == Attribute.city ? "cities" : "states");

        if (path == null)
            return List.of();

        String outputPath = runBulkProcess("normalize_" + attribute.name(), path);

        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            List<Map<String, String>> maps = new Gson().fromJson(reader, LIST_MAP_TYPE);
            if (maps == null)
                logger.error("Failed to read JSON from {}", outputPath);
            else if (maps.size() != addresses.size())
                logger.error("Got {} normalized addresses, but input had {} addresses", maps.size(), addresses.size());
            else
                return maps.stream()
                        .map(m -> m == null ? null : m.get("normalized"))
                        .toList();

            return List.of();
        } catch (IOException e) {
            logger.error("Failed to read " + outputPath + " JSON data", e);
            return List.of();
        } finally {
            Utils.deleteFiles(path, outputPath);
        }
    }

    /**
     * Compare two addresses. This returns a single {@link HashMap} with the result. In the event that the parser
     * script throws an error, it is {@link #logger logged}, and <code>null</code> is returned instead.
     * <p>
     * For more information on the map, see {@link #compare(String, List)}.
     *
     * @param addr1 The first address to compare. If <code>null</code>, this is replaced with the
     *              string <code>"null"</code>.
     * @param addr2 The second address to compare. If <code>null</code>, this is replaced with the
     *              string <code>"null"</code>.
     * @return The result of the comparison, or <code>null</code> if an error is encountered.
     */
    @Nullable
    public static Map<String, String> compare(@Nullable String addr1, @Nullable String addr2) {
        logger.debug("Running single address comparison on '{}' and '{}'", addr1, addr2);

        try {
            Process process = newProcess(
                    "compare", addr1 == null ? "null" : addr1, addr2 == null ? "null" : addr2
            );
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            Map<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            reader.close();

            if (map.containsKey("error")) {
                logger.warn("Error while comparing addresses '{}' and '{}'", addr1, addr2);
                logError(map.get("error"));
                return null;
            } else {
                return map;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compare one given address to a list of addresses. This returns a list of {@link HashMap HashMaps}, one for
     * each comparison address. The maps will contain the following keys:
     * <ul>
     *     <li><code>"match"</code> - Either <code>"EXACT"</code>, <code>"INDICATOR"</code>, or <code>"NONE"</code>
     *     <li><code>"preference"</code> - A string with the preferred normalized form of the address
     *     <li><code>"info"</code> - Optional additional information primarily for debugging
     * </ul>
     * If a particular address encounters an error while parsing, its hashmap will be <code>null</code>, thus not
     * containing any keys. The error message is {@link #logger logged}.
     *
     * @param address     The address to compare to everything else. If this is <code>null</code>, it's replaced with
     *                    an empty string.
     * @param comparisons The list of addresses to compare to the main one.
     * @return A list of {@link HashMap HashMaps} indicating the results of each comparison. Or, if the comparison
     * process fails altogether, this will be an empty, immutable list.
     */
    @NotNull
    public static List<Map<String, String>> compare(@Nullable String address, @NotNull List<String> comparisons) {
        logger.debug("Running bulk comparison of '{}' against {} address{}",
                Utils.cleanLineBreaks(address), comparisons.size(), comparisons.size() == 1 ? "" : "es");

        String path = saveToFile(comparisons, "comp_addresses");

        if (path == null)
            return List.of();

        String outputPath = runBulkProcess("compare_file", address == null ? "null" : address, path);

        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            logger.trace("Parsing '{}' with Gson", outputPath);
            List<Map<String, String>> result = new Gson().fromJson(reader, LIST_MAP_TYPE);
            List<Map<String, String>> output = new ArrayList<>();

            if (result == null)
                return output;
            else if (result.size() != comparisons.size()) {
                logger.error("Got {} comparisons, but input had {} addresses", result.size(), comparisons.size());
                return output;
            }

            for (int i = 0; i < result.size(); i++) {
                Map<String, String> map = result.get(i);
                if (map.containsKey("error")) {
                    logger.warn("Error comparing address '{}' to '{}'", address, comparisons.get(i));
                    logError(map.get("error"));
                    output.add(null);
                } else {
                    output.add(map);
                }
            }

            return output;

        } catch (IOException e) {
            logger.error("Failed to read " + outputPath + " JSON data", e);
            return List.of();
        } finally {
            Utils.deleteFiles(path, outputPath);
        }
    }

    /**
     * Encode some data as JSON via {@link Gson} and save it to a file.
     * <p>
     * The file is saved in {@link Config#DATA_DIRECTORY} inside a directory called <code>"tmp"</code>. If that
     * directory doesn't exist, it is created.
     * <p>
     * The file name is created by combining the provided <code>baseName</code> with and underscore 5 random numbers,
     * along with the <code>.json</code> file extension.
     *
     * @param data     The data to save.
     * @param baseName The base name of the file without the file extension.
     * @return The absolute path to the saved file.
     */
    @Nullable
    private static String saveToFile(@Nullable Object data, @NotNull String baseName) {
        File file = Path.of(
                Config.DATA_DIRECTORY.get(),
                "tmp",
                String.format("%s_%05d.json", baseName, (int) (100000 * Math.random()))
        ).toFile();

        try {
            if (file.getParentFile().mkdirs())
                logger.info("Created " + file.getParentFile().getAbsolutePath() + " for address parsing tmp storage");

            FileWriter writer = new FileWriter(file);
            new Gson().toJson(data, writer);
            writer.close();

            return file.getAbsolutePath();
        } catch (IOException | SecurityException | JsonIOException e) {
            logger.error("Failed to save address parsing data to " + file.getAbsolutePath(), e);
        }

        return null;
    }

    /**
     * Log the given error message. This is intended for errors obtained from a JSON string associated with the
     * <code>"error"</code> key.
     * <p>
     * This logs the error at the debug level. It's expected that a warning was already logged.
     * <p>
     * This automatically removes the string <code>"ERROR:"</code> from the start of any lines within the message.
     * That prevents IntelliJ from flagging it as some sort of breaking exception, when in fact errors like this are
     * expected for malformed addresses.
     *
     * @param error The error message to log.
     */
    private static void logError(@NotNull String error) {
        String[] lines = error.split("\n");
        for (int i = 0; i < lines.length; i++) {
            // As long as the line starts with "error", remove the first word
            while (lines[i].toLowerCase(Locale.ROOT).startsWith("error")) {
                String[] words = lines[i].split(" ");
                lines[i] = String.join(" ", Arrays.copyOfRange(words, 1, words.length));
            }
        }

        logger.debug("- Error message: " + String.join("\n", lines));
    }
}
