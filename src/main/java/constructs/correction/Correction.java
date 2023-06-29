package constructs.correction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import constructs.Construct;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * A Correction represents some manually created rule that affects the behavior of the program with respect to the
 * SQL database. It's a means of permanently accounting for some anomaly that is not handled by other automated means
 * (e.g. parsing or normalization scripts).
 * <p>
 * For example, if some school listed their country as <code>"caanda"</code> when it should clearly be
 * <code>"canada"</code>, it's probably not worth rewriting the code base to detect and intercept this misspelling
 * in the normalization process. Instead, a Correction can be added to the database that tells all schools to replace
 * <code>"caanda"</code> with <code>"canada"</code>.
 * <p>
 * Corrections can also be used to simplify the school match detection process by storing particular patterns of
 * schools that belong in the same district but are not automatically detected.
 * <p>
 * Everything done via Corrections could also be done by refactoring the code, but Corrections are intended for cases
 * that only affect one or two individual schools, districts, or other constructs. Additionally, since they're
 * sometimes used for typos, it's reasonable to assume that those typos may be resolved as Organization and School
 * websites correct their information.
 * <p>
 * Corrections are especially useful during the testing process, as this process often involves frequently clearing
 * and recreating the rest of the SQL database, such as when testing the school list aggregation process. Many
 * schools that trigger manual action can be dealt with using Corrections, thereby avoiding repeated tasks that waste
 * time.
 * <hr>
 * <p>
 * Corrections are stored in the SQL database. They consist of a {@link CorrectionType CorrectionType} parameter that
 * specifies what processes within the program they impact, which greatly improves filtering time. Their logic is
 * stored in JSON objects within the databse that contain information partaining to the type of Correction. This
 * structure allows for easily creating new types of Corrections as the program expands, without the need to
 * constantly refactor the Corrections SQL table.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class Correction implements Construct {
    /**
     * The unique id of this Correction in the SQL database. This is <code>-1</code> if the id is not yet known.
     */
    protected transient int id;

    /**
     * The {@link CorrectionType CorrectionType} of Correction.
     */
    @NotNull
    private transient CorrectionType type;

    /**
     * These are user-friendly notes associated with the Correction. These are for use in the SQL database to record
     * any helpful information about what the Correction does and/or why it was created.
     * <p>
     * The SQL database imposes a maximum 300-character limit on these notes.
     * <p>
     * This is <code>transient</code> as it is stored separately from the serialized Corretion data in the database.
     */
    @Nullable
    private transient String notes;

    /**
     * This is a set of 0 or more {@link TypeAdapter TypeAdapters} that may be used to configure the
     * {@link com.google.gson.Gson Gson} interpreter that will read the Correction data.
     */
    @NotNull
    @Unmodifiable
    private final transient Map<Class<?>, Class<?>> deserialization_data;

    /**
     * This is the {@link Gson} instance used for encoding Corrections when
     * {@link #addToInsertStatement(PreparedStatement) saving} them to the database.
     */
    private static final Gson GSON_ENCODER = new GsonBuilder().serializeNulls().create();

    /**
     * Initialize a new Correction.
     *
     * @param id                   The {@link #id}.
     * @param type                 The {@link #type}.
     * @param notes                The {@link #notes}.
     * @param deserialization_data The {@link #deserialization_data}.
     * @throws IllegalArgumentException If the notes are more than 300 characters long, the maximum length allowed by
     *                                  the SQL database.
     */
    protected Correction(int id,
                         @NotNull CorrectionType type,
                         @Nullable String notes,
                         @NotNull Map<Class<?>, Class<?>> deserialization_data) throws IllegalArgumentException {
        if (notes != null && notes.length() > 300)
            throw new IllegalArgumentException(
                    "Correction notes length " + notes.length() + " exceeds 300-character limit"
            );

        this.id = id;
        this.type = type;
        this.notes = notes;
        this.deserialization_data = deserialization_data;
    }

    /**
     * Initialize a new Correction with the given type, notes, and deserilization data.
     *
     * @param type                 The {@link #type}.
     * @param notes                The {@link #notes}.
     * @param deserialization_data The {@link #deserialization_data}.
     * @throws IllegalArgumentException If the notes are more than 300 characters long, the maximum length allowed by
     *                                  the SQL database.
     */
    protected Correction(@NotNull CorrectionType type,
                         @Nullable String notes,
                         @NotNull Map<Class<?>, Class<?>> deserialization_data) throws IllegalArgumentException {
        this(-1, type, notes, deserialization_data);
    }

    /**
     * Initialize a new Correction with the given type and notes. The {@link #deserialization_data} is set to an empty
     * map.
     *
     * @param type  The {@link #type}.
     * @param notes The {@link #notes}.
     * @throws IllegalArgumentException If the notes are more than 300 characters long, the maximum length allowed by
     *                                  the SQL database.
     */
    protected Correction(@NotNull CorrectionType type, @Nullable String notes) throws IllegalArgumentException {
        this(-1, type, notes, new HashMap<>());
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    public void setType(@NotNull CorrectionType type) {
        this.type = type;
    }

    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement) throws SQLException {
        statement.setString(1, type.name());
        statement.setString(2, GSON_ENCODER.toJson(this));
        statement.setString(3, CorrectionType.encodeDeserializationData(deserialization_data));
        statement.setString(4, notes);

        statement.addBatch();
    }

    @Override
    @NotNull
    public String toString() {
        return String.format("%s correction%s", type, id == -1 ? "" : " (" + id + ")");
    }
}
