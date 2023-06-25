package constructs.correction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import constructs.correction.districtMatch.DistrictMatchCorrection;
import constructs.correction.normalizedAddress.NormalizedAddress;
import constructs.correction.schoolAttribute.SchoolAttributeCorrection;
import constructs.correction.schoolCorrection.Action;
import constructs.correction.schoolCorrection.ChangeAttributesAction;
import constructs.correction.schoolCorrection.OmitAction;
import constructs.correction.schoolCorrection.SchoolCorrection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.Map;

/**
 * These are all the possible types that a {@link Correction} can have. They are associated with particular
 * subclasses of the Correction superclass.
 * <p>
 * Note that the current SQL structure limits these enums at a maximum {@link #name() name} length of 30.
 */
public enum CorrectionType {
    /**
     * Corrections associated with district matches.
     * <p>
     * <b>Correction class:</b> {@link DistrictMatchCorrection}
     */
    DISTRICT_MATCH(DistrictMatchCorrection.class),

    /**
     * Corrections associated with the normalized address lookup table for the Python address parser.
     * <p>
     * <b>Correction class:</b> {@link NormalizedAddress}
     */
    NORMALIZED_ADDRESS(NormalizedAddress.class),

    /**
     * Corrections associated with school attributes.
     * <p>
     * <b>Correction class:</b> {@link SchoolAttributeCorrection}
     */
    SCHOOL_ATTRIBUTE(SchoolAttributeCorrection.class),

    /**
     * Corrections associated with modifying schools.
     * <p>
     * <b>Correction class:</b> {@link SchoolCorrection}
     */
    SCHOOL_CORRECTION(SchoolCorrection.class);

    /**
     * This is the class that is instantiated when a Correction is created from this type via
     * {@link #make(String, String, String)}.
     */
    private final Class<? extends Correction> correctionClass;

    /**
     * Initialize a Correction type.
     *
     * @param correctionClass The {@link #correctionClass} associated with this type.
     */
    CorrectionType(Class<? extends Correction> correctionClass) {
        this.correctionClass = correctionClass;
    }

    /**
     * Create a new {@link Correction} of the appropriate {@link #correctionClass class} using the given JSON data.
     *
     * @param data                The JSON data with which to make the Correction.
     * @param deserializationData The deserialization data.
     * @param notes               The notes.
     * @return The new Correction.
     * @throws JsonSyntaxException If there is an error parsing the JSON data.
     */
    @NotNull
    public Correction make(@NotNull String data,
                           @Nullable String deserializationData,
                           @Nullable String notes) throws JsonSyntaxException {
        Correction correction = new GsonBuilder()
                .registerTypeAdapterFactory(new CustomTypeAdapterFactory(readDeserializationData(deserializationData)))
                .create()
                .fromJson(data, correctionClass);

        correction.setNotes(notes);
        return correction;
    }

    /**
     * Attempt to convert the name of some class related to Corrections into an actual {@link Class} instance.
     *
     * @param name The class name.
     * @return The class.
     * @throws IllegalArgumentException If the input is invalid.
     * @see #getNameFromClass(Class)
     */
    @NotNull
    private static Class<?> getClassFromName(@NotNull String name) {
        return switch (name) {
            case "Action" -> Action.class;
            case "OmitAction" -> OmitAction.class;
            case "ChangeAttributesAction" -> ChangeAttributesAction.class;
            default -> throw new IllegalArgumentException("Unknown class name: " + name);
        };
    }

    /**
     * Attempt to convert some {@link Class} into a string format for use in a JSON object. This process can be
     * reversed with {@link #getClassFromName(String)}.
     *
     * @param cls The class.
     * @return The class name.
     */
    @NotNull
    private static String getNameFromClass(@NotNull Class<?> cls) {
        if (cls == Action.class)
            return "Action";
        else if (cls == OmitAction.class)
            return "OmitAction";
        else if (cls == ChangeAttributesAction.class)
            return "ChangeAttributesAction";
        else
            throw new IllegalArgumentException("Unknown class: " + cls);
    }

    /**
     * Read JSON-encoded deserialization data.
     *
     * @param json The JSON string to read, or <code>null</code>.
     * @return The map of deserialization data. This is empty if the input is <code>null</code>.
     */
    @NotNull
    @Unmodifiable
    public static Map<@NotNull Class<?>, @NotNull Class<?>> readDeserializationData(@Nullable String json) {
        if (json == null) return Map.of();

        Map<String, String> map = new Gson().fromJson(json, new TypeToken<Map<String, String>>() {
        }.getType());

        Map<Class<?>, Class<?>> data = new HashMap<>();
        for (String key : map.keySet())
            data.put(getClassFromName(key), getClassFromName(map.get(key)));

        return data;
    }

    /**
     * Encode deserialization data as JSON.
     *
     * @param data The deserialization data to encode.
     * @return The encoded JSON object, or <code>null</code> if the input map is empty.
     */
    @Nullable
    public static String encodeDeserializationData(@NotNull Map<Class<?>, Class<?>> data) {
        if (data.size() == 0) return null;

        Map<String, String> map = new HashMap<>();
        for (Class<?> cls : data.keySet())
            map.put(getNameFromClass(cls), getNameFromClass(data.get(cls)));

        return new Gson().toJson(map);
    }
}
