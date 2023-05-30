package processing.schoolLists.matching;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Utils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        logger.debug("Calling python address parser: '{}'", String.join(" ", builder.command()));
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
            HashMap<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            if (map == null) {
                logger.warn(
                        "Unreachable state: map with python parser output is null.\n" +
                                "Input: " + process.info().commandLine().orElse(String.join(" ", args)),
                        Utils.packageStackTrace(Thread.currentThread().getStackTrace()));
                return null;
            }

            reader.close();
            return getOrLogError(map, "output_file");
        } catch (IOException e) {
            logger.error("Failed to run the python address parser and read its output. " +
                    "Did you compile the executable?", e);
            return null;
        }
    }

    /**
     * Attempt to retrieve the specified key from the given {@link HashMap}. However, this first checks whether the
     * map contains the key <code>"error"</code>, which indicates that it instead contains an error message. In this
     * case, the error is logged to the console and <code>null</code> is returned.
     * <p>
     * This makes use of the fact that {@link Map#get(Object) Map.get()} returns <code>null</code> for nonexistent
     * keys. If the map contains an error message <i>and</i> the specified key, it will log the error and return the
     * value too.
     *
     * @param map The map from the python parser containing the error message.
     * @param key The key to retrieve, assuming the map doesn't contain an error message.
     * @return The value {@link HashMap#get(Object) mapped} to the specified key. This will be <code>null</code> if
     * the given <code>map</code> is <code>null</code>.
     */
    @Nullable
    private static String getOrLogError(@Nullable HashMap<String, String> map,
                                        @NotNull String key) {
        if (map == null)
            return null;
        else if (map.containsKey("error"))
            logError(map);

        return map.get(key);
    }

    /**
     * Assuming the given {@link HashMap} contains an error message, {@link Logger#debug(String) log} that message.
     *
     * @param map The map with the error message.
     */
    private static void logError(@NotNull HashMap<String, String> map) {
        logger.debug("{} — {}: {}", map.get("error"), map.get("message"), map.get("stacktrace"));
    }

    /**
     * Attempt to normalize the given address using the python address script.
     * <p>
     * If the address cannot be normalized for some reason and the library throws an error, the error is
     * {@link #logError(HashMap) logged} to the console and <code>null</code> is returned.
     *
     * @param address The address to normalize.
     * @return The normalized address, or <code>null</code> if the input is null or not parseable.
     * @see #normalize(List)
     */
    @Nullable
    public static String normalize(@Nullable String address) {
        if (address == null || address.isBlank()) return null;

        try {
            // Call the python address parser, and read the result as JSON data
            Process process = newProcess("normalize", address);
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            HashMap<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            reader.close();
            return getOrLogError(map, "normalized");
        } catch (IOException e) {
            logger.error("Failed to run the python address parser and read its output. " +
                    "Did you compile the executable?", e);
            return null;
        }
    }

    /**
     * Bulk normalize many addresses at the same time. This is significantly more efficient, as it minimizes the
     * number of separate calls to the python script, which has a slow start-up time.
     * <p>
     * Addresses that fail the normalization process will be {@link #logError(HashMap) logged} to the console and
     * returned as <code>null</code>.
     *
     * @param addresses The list of addresses to normalize.
     * @return A normalized list of addresses in the same order as the input. If a fatal occurs, this is an empty,
     * immutable list.
     * @see #normalize(String)
     */
    @NotNull
    public static List<String> normalize(@NotNull List<String> addresses) {
        logger.debug("Running bulk normalization on {} addresses", addresses.size());
        String path = saveToFile(addresses, "addresses");

        if (path == null)
            return List.of();

        String outputPath = runBulkProcess("normalize_file", path);

        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            List<HashMap<String, String>> normalizedData = new Gson().fromJson(reader, LIST_MAP_TYPE);
            List<String> output = new ArrayList<>();

            if (normalizedData == null) return output;

            int successCount = 0;
            for (HashMap<String, String> map : normalizedData) {
                String norm = getOrLogError(map, "normalized");
                output.add(norm);
                if (norm != null)
                    successCount++;
            }

            logger.debug("Successfully normalized {}/{} addresses from {} inputs",
                    successCount, addresses.size(), output.size());

            return output;
        } catch (IOException e) {
            logger.error("Failed to read " + outputPath + " JSON data", e);
            return List.of();
        } finally {
            Utils.deleteFiles(path, outputPath);
        }
    }

    /**
     * Compare two addresses. This returns a single {@link HashMap} with the result. In the event that the parser
     * script throws an error, it is {@link #logError(HashMap) logged}, and <code>null</code> is returned instead.
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
    public static HashMap<String, String> compare(@Nullable String addr1, @Nullable String addr2) {
        try {
            Process process = newProcess(
                    "compare", addr1 == null ? "null" : addr1, addr2 == null ? "null" : addr2
            );
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            HashMap<String, String> map = new Gson().fromJson(reader, MAP_TYPE);

            reader.close();

            if (map.containsKey("error")) {
                logError(map);
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
     * containing any keys. The error message is {@link #logError(HashMap) logged}.
     *
     * @param address     The address to compare to everything else. If this is <code>null</code>, it's replaced with
     *                    an empty string.
     * @param comparisons The list of addresses to compare to the main one.
     * @return A list of {@link HashMap HashMaps} indicating the results of each comparison. Or, if the comparison
     * process fails altogether, this will be an empty, immutable list.
     */
    @NotNull
    public static List<HashMap<String, String>> compare(@Nullable String address, @NotNull List<String> comparisons) {
        logger.debug("Running bulk comparison against {} addresses", comparisons.size());

        String path = saveToFile(comparisons, "comp_addresses");

        if (path == null)
            return List.of();

        String outputPath = runBulkProcess("compare_file", address == null ? "null" : address, path);

        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            logger.debug("Parsing '{}' with Gson", outputPath);
            List<HashMap<String, String>> result = new Gson().fromJson(reader, LIST_MAP_TYPE);
            List<HashMap<String, String>> output = new ArrayList<>();

            if (result == null) return output;

            for (HashMap<String, String> map : result) {
                if (map.containsKey("error")) {
                    logError(map);
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
}
