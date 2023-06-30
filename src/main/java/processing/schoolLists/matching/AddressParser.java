package processing.schoolLists.matching;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import constructs.correction.CorrectionManager;
import constructs.correction.CorrectionType;
import constructs.correction.normalizedAddress.NormalizedAddress;
import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Utils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
     * This is the standard {@link Gson} instance for all communication (both {@link #saveToFileUnique(Object, String)
     * serialization} and deserialization) with the address parser script.
     * <p>
     * Notably, it will {@link GsonBuilder#serializeNulls() serialize nulls}, so that they're picked up by the parser.
     */
    private static final Gson GSON = new GsonBuilder().serializeNulls().setLenient().disableHtmlEscaping().create();

    /**
     * This records whether the list of {@link NormalizedAddress} Corrections from the {@link CorrectionManager}
     * currently match the list of them saved in a {@link Config#PYTHON_ADDRESS_PARSER_NORMALIZATION_LIST_FILE_NAME
     * file} for use by the Python address parser. When the problem starts, this is <code>false</code> by default,
     * indicating that the file should be updated.
     */
    private static boolean IS_NORMALIZED_ADDRESSES_CACHE_VALID = false;

    /**
     * This is what the Python address parser returns when you {@link Task#normalize} normalize a
     * {@link Attribute#isEffectivelyNull(Object) null} address. It's stored here as a shortcut to skip calling the
     * parser in such a case.
     */
    @NotNull
    @Unmodifiable
    private static final Map<@NotNull String, @Nullable String> NULL_NORMALIZATION = new HashMap<>();

    /**
     * This is what the Python address parser returns when you {@link Task#compare} two
     * {@link Attribute#isEffectivelyNull(Object) null} addresses. It's stored here as a shortcut to skip calling the
     * parser in such a case. The <code>"match"</code> level is <code>"EXACT"</code>.
     */
    @NotNull
    @Unmodifiable
    private static final Map<@NotNull String, @Nullable String> NULL_COMPARISON_EXACT = new HashMap<>();

    /**
     * Same as {@link #NULL_COMPARISON_EXACT}, except the match is <code>"INDICATOR"</code>.
     */
    @NotNull
    @Unmodifiable
    private static final Map<@NotNull String, @Nullable String> NULL_COMPARISON_INDICATOR = new HashMap<>();

    // Initialize the NULL_NORMALIZATION, NULL_COMPARISON_EXACT, and NULL_COMPARISON_INDICATOR maps
    static {
        NULL_NORMALIZATION.put("address_line_1", null);
        NULL_NORMALIZATION.put("address_line_2", null);
        NULL_NORMALIZATION.put("city", null);
        NULL_NORMALIZATION.put("state", null);
        NULL_NORMALIZATION.put("postal_code", null);
        NULL_NORMALIZATION.put("normalized", null);

        NULL_COMPARISON_EXACT.putAll(NULL_NORMALIZATION);
        NULL_COMPARISON_EXACT.put("match", "EXACT");
        NULL_COMPARISON_EXACT.put("info", null);

        NULL_COMPARISON_INDICATOR.putAll(NULL_COMPARISON_EXACT);
        NULL_COMPARISON_INDICATOR.put("match", "INDICATOR");
    }

    // Add a listener that will invalidate the cache whenever a new NormalizedAddress is created or the entire
    // Corrections list is loaded
    static {
        CorrectionManager.registerListener(((type, correction) -> {
            if (type == CorrectionType.NORMALIZED_ADDRESS)
                invalidateNormalizedAddressCache();
        }));
    }

    /**
     * These are the valid tasks recognized by the Python address parser. They must be given to the parser via the
     * <code>-t</code> (or <code>--task</code>) flag.
     * <p>
     * These are declared such that calling {@link #name()} produces the identifier for the task as expected by the
     * Python script.
     */
    private enum Task {
        normalize,
        normalize_file,
        compare,
        compare_file,
        normalize_city,
        normalize_city_file,
        normalize_state,
        normalize_state_file
    }

    /**
     * Call the Python address parser at {@link Config#PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH}, creating and returning
     * a new {@link Process}.
     * <p>
     * This automatically {@link #prepareNormalizedAddresses() supplies} the {@link NormalizedAddress} cache to the
     * parser via the <code>-l</code> (lookup) flag. If preparing that file fails in some way, that flag is omitted.
     * <p>
     * This calls the parser with the following command:
     * <p>
     * <code>./path/to/parser/address.exe -t TASK -l LOOKUP_PATH ARGS</code>
     * <p>
     * where <code>TASK</code> is the given <code>task</code> string and <code>ARGS</code> is the set of additional
     * arguments.
     *
     * @param task The task to run.
     * @param args The additional arguments to pass to the process for the specified task.
     * @return The new process.
     * @throws IOException If an error occurs creating the process.
     */
    @NotNull
    private static Process newProcess(@NotNull Task task, @NotNull String... args) throws IOException {
        String normalizationCache = prepareNormalizedAddresses();

        ProcessBuilder builder = new ProcessBuilder(Config.PYTHON_ADDRESS_PARSER_EXECUTABLE_PATH.get());
        List<String> command = builder.command();
        command.add("-t"); // Task flag
        command.add(task.name());
        if (normalizationCache != null) {
            command.add("-l"); // Lookup flag
            command.add(normalizationCache);
        }
        command.addAll(Arrays.asList(args));

        logger.trace("Calling python address parser: '{}'", command);
        return builder.start();
    }

    /**
     * {@link #newProcess(Task, String...) Create} a new bulk process with a specified command arguments that runs
     * on an entire file. Run the process, and return the resulting output file path.
     * <p>
     * If the process encounters an error, log it to the console, and return <code>null</code>.
     *
     * @param task The task to run.
     * @param args The additional arguments to pass to the process for the specified task.
     * @return The path of the output file, if it exists. If an error occurred, this is <code>null</code>.
     */
    @Nullable
    private static String runBulkProcess(@NotNull Task task, @NotNull String... args) {
        try {
            Process process = newProcess(task, args);
            Map<String, String> map = null;

            try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
                map = GSON.fromJson(reader, MAP_TYPE);
            } catch (JsonParseException e) {
                logger.error("Failed to parse JSON response from address parser", e);
            }

            if (map == null) {
                logger.warn("Unreachable state: map with python parser output is null.\n" +
                                "Input: " + process.info().commandLine().orElse(getLoggingInput(task, args)),
                        Utils.packageStackTrace(Thread.currentThread().getStackTrace())
                );
            } else if (map.containsKey("error")) {
                //noinspection UnnecessaryUnicodeEscape
                logger.error("{} \u2014 {}: {}\n - Input args: {}",
                        map.get("error"), map.get("message"), map.get("stacktrace"), getLoggingInput(task, args)
                );
            } else {
                return map.get("output_file");
            }
        } catch (IOException e) {
            logger.error("Failed to run the python address parser and read its output. " +
                    "Did you compile the executable?", e);
        }

        return null;
    }

    /**
     * Parse and normalize a single address.
     * <p>
     * For more information, and to use the more efficient bulk normalization process, see {@link #parseAddress(List)}.
     *
     * @param address The address to normalize.
     * @return A map containing the parsed and normalized address information. The normalized address is retrievable
     * via the <code>"normalized"</code> key.
     */
    @Nullable
    public static Map<String, String> parseAddress(@Nullable String address) {
        logger.trace("Normalizing single address '{}'", address);

        // If the address is null, skip calling the python parser
        if (Attribute.address.isEffectivelyNull(address))
            return NULL_NORMALIZATION;

        Process process;
        try {
            process = newProcess(Task.normalize, address);
        } catch (IOException e) {
            logger.error("Failed to run process", e);
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
            Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);

            if (map.containsKey("error")) {
                logger.warn("Error normalizing address '{}'", address);
                logError(map.get("error"));
                return null;
            } else {
                return map;
            }
        } catch (IOException | JsonParseException e) {
            logger.error("Failed to parse JSON response from address parser", e);
            return null;
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
    public static List<Map<String, String>> parseAddress(@NotNull List<String> addresses) {
        logger.trace("Running bulk normalization on {} address{}", addresses.size(), addresses.size() == 1 ? "" : "es");

        // If they're all null, skip this. If only some are null, go ahead and use the python parser, as that won't
        // drastically increase computational time (it's the startup time that's slow)
        if (addresses.stream().allMatch(Attribute.address::isEffectivelyNull))
            return new ArrayList<>(Collections.nCopies(addresses.size(), NULL_NORMALIZATION));

        String path = saveToFileUnique(addresses, "addresses");
        if (path == null)
            return List.of();

        String outputPath = runBulkProcess(Task.normalize_file, path);
        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            List<Map<String, String>> maps = null;
            try {
                maps = GSON.fromJson(reader, LIST_MAP_TYPE);
            } catch (JsonParseException e) {
                logger.error("Failed to parse JSON response from address parser", e);
            }

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
        Map<String, String> normalized = parseAddress(address);
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
        List<Map<String, String>> parsed = parseAddress(addresses);
        if (parsed.size() == 0) return List.of();

        List<String> normalized = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < parsed.size(); i++) {
            Map<String, String> map = parsed.get(i);
            if (map == null) {
                logger.warn("Unexpected null normalization map for address '{}'", addresses.get(i));
                normalized.add(null);
            } else {
                if (map.containsKey("error")) {
                    logger.warn("Error normalizing address '{}'", addresses.get(i));
                    logError(map.get("error"));
                }

                String norm = map.get("normalized");
                normalized.add(norm);
                if (norm != null)
                    successCount++;
            }
        }

        logger.debug("Successfully normalized {}/{} addresses from {} inputs",
                successCount, addresses.size(), normalized.size());

        return normalized;
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

        // If the value and address are both null, skip calling the Python parser to save time
        if (attribute.isEffectivelyNull(value) && Attribute.address.isEffectivelyNull(address))
            return null;

        Process process;
        try {
            process = newProcess(
                    attribute == Attribute.city ? Task.normalize_city : Task.normalize_state,
                    value == null ? "null" : value,
                    address == null ? "null" : address
            );
        } catch (IOException e) {
            logger.error("Failed to run process", e);
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
            Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);

            if (map.containsKey("error")) {
                logger.warn("Error normalizing {} '{}'", attribute.name(), value);
                logError(map.get("error"));
                return null;
            } else {
                return map.get("normalized");
            }
        } catch (IOException | JsonParseException e) {
            logger.error("Failed to parse JSON response from address parser", e);
            return null;
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

        // If every value and address is null, skip calling the Python parser to save time
        if (addresses.stream().allMatch(Attribute.address::isEffectivelyNull) &&
                values.stream().allMatch(attribute::isEffectivelyNull))
            return new ArrayList<>(Collections.nCopies(values.size(), null));

        List<Map<String, String>> inputData = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            Map<String, String> map = new HashMap<>();
            map.put("value", values.get(i));
            map.put("address", addresses.get(i));
            inputData.add(map);
        }

        String path = saveToFileUnique(inputData, attribute == Attribute.city ? "cities" : "states");

        if (path == null)
            return List.of();

        String outputPath = runBulkProcess(
                attribute == Attribute.city ? Task.normalize_city_file : Task.normalize_state_file, path
        );

        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            List<Map<String, String>> maps = GSON.fromJson(reader, LIST_MAP_TYPE);
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
     * For more information on the map, see {@link #compare(String, List, boolean)}.
     *
     * @param addr1 The first address to compare. If <code>null</code>, this is replaced with the
     *              string <code>"null"</code>.
     * @param addr2 The second address to compare. If <code>null</code>, this is replaced with the
     *              string <code>"null"</code>.
     * @return The result of the comparison, or <code>null</code> if an error is encountered.
     */
    @Nullable
    public static Map<String, String> compare(@Nullable String addr1, @Nullable String addr2) {
        logger.trace("Running single address comparison on '{}' and '{}'", addr1, addr2);

        // If both addresses are null, skip the comparison
        if (Attribute.address.isEffectivelyNull(addr1) && Attribute.address.isEffectivelyNull(addr2))
            return Objects.equals(addr1, addr2) ? NULL_COMPARISON_EXACT : NULL_COMPARISON_INDICATOR;

        try {
            Process process = newProcess(Task.compare, addr1 == null ? "null" : addr1, addr2 == null ? "null" : addr2);
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
                Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
                if (map.containsKey("error")) {
                    logger.warn("Error while comparing addresses '{}' and '{}'", addr1, addr2);
                    logError(map.get("error"));
                    return null;
                } else {
                    return map;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <h2>Process</h2>
     * Compare one given address to a list of addresses. This returns a list of {@link HashMap HashMaps}, one for
     * each comparison address. The maps may contain the following keys (keys marked <code>(*)</code> may not be
     * included; see below):
     * <ul>
     *     <li><code>"match"</code> - Either <code>"EXACT"</code>, <code>"INDICATOR"</code>, or <code>"NONE"</code>.
     *     <li><code>"info"</code> - Optional additional information primarily for debugging.
     *     <li><code>"normalized"</code> - The normalized preferred form of the address. This is <code>null</code>
     *     when the <code>match</code> is <code>"NONE"</code>.
     *     <li><code>(*) "address_line_1"</code> - The parsed preference address.
     *     <li><code>(*) "address_line_2"</code> - <i>id.</i>
     *     <li><code>(*) "city"</code> - <i>id.</i>
     *     <li><code>(*) "state"</code> - <i>id.</i>
     *     <li><code>(*) "postal code"</code> - <i>id.</i>
     * </ul>
     * If a particular address encounters an error while parsing, the <code>"info"</code> key will map to an error
     * message that is {@link #logger logged}.
     *
     * <h2>Shortcutting for Nulls</h2>
     * This can also optionally skip making a call to the Python parser script if the primary <code>address</code> is
     * {@link Attribute#isEffectivelyNull(Object) null}. In that case, if <code>allowShortcuttingNulls</code> is
     * <code>true</code>, it's assumed that <b>all</b> <code>comparison</code> addresses have <b>already been
     * {@link #normalizeAddress(String) normalized}</b>, and the comparison occurs as follows:
     * <ul>
     *     <li>For <code>null</code> comparisons, the match level is set to <code>"EXACT"</code> or
     *     <code>"INDICATOR"</code>, depending on whether the strings are {@link Objects#equals(Object, Object) equal}.
     *     <li>For non-<code>null</code> comparison, it's assumed that the address is valid, and thus preference goes
     *     to the comparison address with a match level of <code>"NONE"</code>
     * </ul>
     * <p>
     * Note that if this shortcut is performed, this method does not guarantee that all possible keys will be
     * included in each map. Some keys may not be included or, if included, may not be accurate. The results are only
     * guaranteed to contain <code>"match</code>, <code>"normalized"</code>, and <code>"info"</code> keys, and the
     * <code>"info"</code> key will always map to <code>null</code>.
     *
     * @param address                The address to compare to everything else. If this is <code>null</code>, it's
     *                               replaced with
     *                               an empty string.
     * @param comparisons            The list of addresses to compare to the main one. If this is empty, an
     *                               immutable, empty list is returned.
     * @param allowShortcuttingNulls Whether to skip the Python parser if that is possible. Marking this
     *                               <code>true</code> indicates that the <code>comparisons</code> are <b>all</b>
     *                               normalized already.
     * @return A list of {@link HashMap HashMaps} indicating the results of each comparison. Or, if the comparison
     * process fails altogether, this will be an empty, immutable list.
     */
    @NotNull
    @Unmodifiable
    public static List<Map<String, String>> compare(@Nullable String address,
                                                    @NotNull List<String> comparisons,
                                                    boolean allowShortcuttingNulls) {
        logger.trace("Running bulk comparison of '{}' against {} address{}",
                Utils.cleanLineBreaks(address), comparisons.size(), comparisons.size() == 1 ? "" : "es");

        // If the list of comparisons is empty, exit immediately
        if (comparisons.size() == 0)
            return List.of();

        // If the address is null and shortcutting is enabled, skip calling the Python parser to save time
        if (allowShortcuttingNulls && Attribute.address.isEffectivelyNull(address)) {
            List<Map<String, String>> output = new ArrayList<>();
            for (String comparison : comparisons) {
                if (Attribute.address.isEffectivelyNull(comparison))
                    output.add(Objects.equals(address, comparison) ? NULL_COMPARISON_EXACT : NULL_COMPARISON_INDICATOR);
                else {
                    Map<String, String> map = new HashMap<>();
                    map.put("match", "NONE");
                    map.put("normalized", comparison);
                    map.put("info", null);
                    output.add(map);
                }
            }

            return output;
        }

        // Continue with calling the Python parser

        String path = saveToFileUnique(comparisons, "comp_addresses");
        if (path == null)
            return List.of();

        String outputPath = runBulkProcess(Task.compare_file, address == null ? "null" : address, path);
        if (outputPath == null) {
            Utils.deleteFiles(path);
            return List.of();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(outputPath))) {
            logger.trace("Parsing '{}' with Gson", outputPath);
            List<Map<String, String>> result = GSON.fromJson(reader, LIST_MAP_TYPE);
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
     * Generated a properly formatted address string given the parsed elements.
     * <p>
     * Typically, this is done automatically by the Python parser script during the normalization process. This
     * method seeks to mimic the functionality of the Python formatter 1:1 when formatting is necessary within the
     * Java program.
     *
     * @param line1      The first line of the address.
     * @param line2      The second line of the address.
     * @param city       The city.
     * @param state      The state.
     * @param postalCode The postal code.
     * @return The formatted address, or an empty string if every input is <code>null</code>.
     */
    @NotNull
    public static String formatAddress(@Nullable String line1,
                                       @Nullable String line2,
                                       @Nullable String city,
                                       @Nullable String state,
                                       @Nullable String postalCode) {
        city = Utils.joinNonEmpty(", ", city, state);
        city = Utils.joinNonEmpty(" ", city, postalCode);
        return Utils.joinNonEmpty("\n", line1, line2, city);
    }

    /**
     * Get a {@link File} instance pointing to the complete path where a particular file should be
     * {@link #saveToFile(Object, String) saved}.
     *
     * @param baseName The base name of the file without the file extension: <code>.json</code> is added automatically.
     * @return The reference to the file. Note that the file and its parent directories may not exist yet.
     */
    @NotNull
    private static File determineFile(@NotNull String baseName) {
        return Path.of(Config.DATA_DIRECTORY.get(), "tmp", baseName + ".json").toFile();
    }

    /**
     * Encode some data as JSON via {@link Gson} and save it to a given {@link File}.
     *
     * @param data The data to save.
     * @param file The file pointing to the location to save it. Note that this file, as well as its parent
     *             directories, need not exist. If they don't exist, they are created automatically.
     * @return The absolute path to the saved file.
     */
    @Nullable
    private static String saveToFile(@Nullable Object data, @NotNull File file) {
        try {
            if (file.getParentFile().mkdirs())
                logger.info("Created " + file.getParentFile().getAbsolutePath() + " for address parsing tmp storage");

            FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8);
            GSON.toJson(data, writer);
            writer.close();

            return file.getAbsolutePath();
        } catch (IOException | SecurityException | JsonIOException e) {
            logger.error("Failed to save address parsing data to " + file.getAbsolutePath(), e);
        }

        return null;
    }

    /**
     * Encode some data as JSON via {@link Gson} and save it to a file.
     * <p>
     * The file is saved in {@link Config#DATA_DIRECTORY} inside a directory called <code>"tmp"</code>. If that
     * directory doesn't exist, it is created.
     * <p>
     * The saving process is done by {@link #saveToFile(Object, File)}.
     *
     * @param data     The data to save.
     * @param baseName The base name of the file without the file extension: <code>.json</code> is added automatically.
     * @return The absolute path to the saved file.
     */
    @Nullable
    private static String saveToFile(@Nullable Object data, @NotNull String baseName) {
        return saveToFile(data, determineFile(baseName));
    }

    /**
     * Encode some data as JSON via {@link Gson} and save it to a file.
     * <p>
     * This calls {@link #saveToFile(Object, String) saveToFile()} with a unique file name. This name is generated by
     * combining the provided <code>baseName</code> with an underscore and 5 random numbers.
     *
     * @param data     The data to save.
     * @param baseName The base name of the file without the file extension.
     * @return The absolute path to the saved file.
     */
    @Nullable
    private static String saveToFileUnique(@Nullable Object data, @NotNull String baseName) {
        return saveToFile(data, String.format("%s_%05d", baseName, (int) (100000 * Math.random())));
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

    /**
     * Get a string that represents a "good-enough" version of the task and argument parameters that were passed to
     * the Python parser. This is intended solely for logging.
     *
     * @param task The task.
     * @param args The arguments associated with the task.
     * @return The text to log.
     */
    @NotNull
    private static String getLoggingInput(@NotNull Task task, @NotNull List<String> args) {
        return String.format("-t %s %s", task.name(), Utils.joinCommand(args));
    }

    /**
     * This is a wrapper for {@link #getLoggingInput(Task, List)} that converts vararg string arguments to a
     * {@link List}.
     *
     * @param task The task.
     * @param args The arguments associated with the task.
     * @return The text to log.
     */
    private static String getLoggingInput(@NotNull Task task, @NotNull String... args) {
        return getLoggingInput(task, Arrays.asList(args));
    }

    /**
     * Mark the cache of {@link NormalizedAddress} Corrections {@link #IS_NORMALIZED_ADDRESSES_CACHE_VALID invalid}
     * (i.e. <code>false</code>). The next {@link #prepareNormalizedAddresses() prepartion} of that list will require
     * resetting it: pulling from the database and updating the local file used by the Python address parser.
     */
    private static void invalidateNormalizedAddressCache() {
        IS_NORMALIZED_ADDRESSES_CACHE_VALID = false;
    }

    /**
     * Prepare the list of {@link NormalizedAddress} Corrections used by the Python address parser. If the list is
     * currently {@link #IS_NORMALIZED_ADDRESSES_CACHE_VALID valid}, this has no effect. Otherwise, it does the
     * following:
     * <ol>
     *     <li>{@link CorrectionManager#getNormalizedAddresses() Get} the list of normalized addresses from the
     *     {@link CorrectionManager}.
     *     <li>{@link #saveToFile(Object, String) Save} the list of normalized addresses to the appropriate
     *     {@link Config#PYTHON_ADDRESS_PARSER_NORMALIZATION_LIST_FILE_NAME file} in the same directory as the other
     *     files passed to the parser script.
     *     <li>Mark the list valid.
     * </ol>
     *
     * @return The complete path to the saved file or, if some error occurs, <code>null</code>.
     */
    @Nullable
    private static synchronized String prepareNormalizedAddresses() {
        File file = determineFile(Config.PYTHON_ADDRESS_PARSER_NORMALIZATION_LIST_FILE_NAME.get());
        if (IS_NORMALIZED_ADDRESSES_CACHE_VALID) return file.getAbsolutePath();

        String path = saveToFile(CorrectionManager.getNormalizedAddresses(), file);
        if (path == null) {
            logger.warn("Failed to update the normalized address cache file");
            return null;
        } else {
            IS_NORMALIZED_ADDRESSES_CACHE_VALID = true;
            return path;
        }
    }
}
