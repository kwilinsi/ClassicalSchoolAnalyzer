package constructs.school;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This extension of {@link School} is used for representing schools that have already been added to the database.
 */
public class CachedSchool extends School {

    /**
     * This is the set of {@link Attribute Attributes} and their normalized values for this school. Normalizing
     * attributes is a critical step in comparing them between schools, and storing them here prevents re-normalizing
     * every attribute during every comparison.
     */
    @NotNull
    protected final Map<Attribute, Object> normalizedAttributes = new LinkedHashMap<>() {{
        for (Attribute attribute : Attribute.values())
            put(attribute, null);
    }};

    public CachedSchool() {
        super();
    }

    /**
     * Create a {@link CachedSchool} from a {@link ResultSet}.
     *
     * @see School#School(ResultSet)
     */
    public CachedSchool(@NotNull ResultSet resultSet) throws SQLException {
        super(resultSet);
    }

    public CachedSchool(@NotNull CreatedSchool school) {
        this();

        this.id = school.id;
        this.district_id = school.district_id;
        for (Attribute a : Attribute.values())
            put(a, school.get(a));
    }

    /**
     * Save a new value for some {@link Attribute Attribute} to the {@link #normalizedAttributes} list.
     *
     * @param attribute The attribute to save.
     * @param value     The value of the normalized attribute.
     */
    public void putNormalized(@NotNull Attribute attribute, @Nullable Object value) {
        normalizedAttributes.put(attribute, value);
    }

    /**
     * Get the current value of some {@link #normalizedAttributes normalized attribute}. If it doesn't exist, use the
     * regular value with {@link #get(Attribute) get()}.
     *
     * @param attribute The attribute to retrieve.
     * @return The current normalized value of that attribute.
     */
    @Nullable
    public Object getNormalized(@NotNull Attribute attribute) {
        if (normalizedAttributes.containsKey(attribute))
            return normalizedAttributes.get(attribute);
        else
            return get(attribute);
    }

    /**
     * {@link #getNormalized(Attribute) Get} the value of some {@link #normalizedAttributes normalized attribute} as a
     * {@link String}. If it's not a string, this returns <code>null</code>.
     *
     * @param attribute The attribute to retrieve.
     * @return The current value of that attribute, or <code>null</code> if the attribute is not a string type.
     */
    @Nullable
    public String getNormalizedStr(@NotNull Attribute attribute) {
        return (getNormalized(attribute) instanceof String s) ? s : null;
    }
}
