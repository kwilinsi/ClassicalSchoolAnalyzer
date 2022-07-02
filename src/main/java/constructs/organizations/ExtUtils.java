package constructs.organizations;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import constructs.schools.School;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Config;

public class ExtUtils {
    /**
     * This is a list of strings that are mapped to <code>null</code> by {@link #aliasNull(String)}.
     */
    public static final String[] NULL_STRINGS = {"", "n/a", "null", "not available", "not applicable", "none"};

    /**
     * This is a list of strings that are mapped to <code>null</code> by {@link #aliasNullLink(String)}.
     */
    private static final String[] NULL_LINKS = {"http://", "https://"};

    /**
     * Take some {@link String} input and assess whether it should be treated as <code>null</code>.
     * <p>
     * First, the string is {@link String#trim() trimmed}. Then, if it matches any of the {@link #NULL_STRINGS}
     * (case-insensitive), <code>null</code> is returned. Otherwise, the trimmed input string is returned.
     *
     * @param input The input.
     *
     * @return The trimmed input, or possibly <code>null</code>.
     */
    @Nullable
    public static String aliasNull(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        for (String n : NULL_STRINGS)
            if (s.equalsIgnoreCase(n))
                return null;
        return s;
    }

    /**
     * This is an extension of {@link #aliasNull(String)} that also handles URLs. The input is first passed to {@link
     * #aliasNull(String) aliasNull()}. If the result is not null, it is checked against the strings in {@link
     * #NULL_LINKS} (case-insensitive). If it matches any of them, <code>null</code> is returned.
     *
     * @param input The input.
     *
     * @return The original string, or possibly <code>null</code>.
     */
    @Nullable
    public static String aliasNullLink(@Nullable String input) {
        String s = aliasNull(input);
        if (s == null) return null;
        for (String n : NULL_LINKS)
            if (s.equalsIgnoreCase(n))
                return null;
        return s;
    }

    /**
     * Take some text that is expected to be the name of a {@link School}. This will run it through {@link
     * #aliasNull(String)}, which may flag it <code>null</code>. In this case, it will be replaced with {@link
     * Config#MISSING_NAME_SUBSTITUTION}, to not violate the <code>NOT NULL</code> constraint in the Schools table.
     *
     * @param name The input name.
     *
     * @return The input unmodified, or the missing name substitution if the name would otherwise resolev to
     *         <code>null</code>.
     */
    @NotNull
    public static String validateName(@Nullable String name) {
        if (aliasNull(name) == null)
            return Config.MISSING_NAME_SUBSTITUTION.get();
        return name;
    }

    /**
     * Extract an element from an {@link JsonArray} as a string. This string is passed through {@link
     * ExtUtils#aliasNull(String)}, and the result is returned.
     *
     * @param arr   The JSON array to extract from.
     * @param index The index of the element to extract.
     *
     * @return The extracted element, or <code>null</code> if the element is <code>null</code> or empty.
     */
    @Nullable
    public static String extractJson(@NotNull JsonArray arr, int index) {
        return ExtUtils.aliasNull(arr.get(index).getAsString());
    }

    /**
     * See {@link #extractJson(JsonArray, int)}. This behaves similarly, but using string keys rather than indices for
     * selecting the desired element.
     *
     * @param obj The JSON object to extract from.
     * @param key The key of the element to extract.
     *
     * @return The extracted element, or <code>null</code> if the element is <code>null</code> or empty.
     */
    @Nullable
    public static String extractJson(@NotNull JsonObject obj, @NotNull String key) {
        return ExtUtils.aliasNull(obj.get(key).getAsString());
    }
}
