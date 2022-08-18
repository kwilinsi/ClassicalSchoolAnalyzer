package constructs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Config;
import utils.Database;
import utils.Utils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class School extends BaseConstruct {
    /**
     * This is the id of the {@link District} to which this school belongs. Most schools are in a district by
     * themselves. However, some schools are paired with others in a district (such as in the case of separate
     * elementary, middle, and high schools).
     * <p>
     * By default, this is set to <code>-1</code> to indicate no assigned district.
     */
    protected int district_id = -1;

    @NotNull
    protected final Map<Attribute, Object> attributes;

    /**
     * The unique <code>id</code> for this school that was provided by MySQL through the AUTO_INCREMENT id column. This
     * is typically only set for {@link School Schools} created from a {@link ResultSet}.
     */
    protected int id;

    public School() {
        // Add all the Attributes to the attributes map.
        attributes = new LinkedHashMap<>(Attribute.values().length);
        for (Attribute attribute : Attribute.values())
            attributes.put(attribute, attribute.defaultValue);
    }

    /**
     * Create a {@link School} from a {@link ResultSet}, the result of a query of the Schools table. It's expected that
     * "<code>SELECT *</code>" was used, and so the resultSet contains every {@link Attribute}/column.
     *
     * @param resultSet The result set of the query.
     *
     * @throws SQLException if there is any error parsing the resultSet.
     */
    public School(@NotNull ResultSet resultSet) throws SQLException {
        this();

        this.id = resultSet.getInt("id");
        this.district_id = resultSet.getInt("district_id");

        for (Attribute a : Attribute.values())
            if (a.type == LocalDate.class) {
                Date date = resultSet.getDate(a.name());
                put(a, date == null ? null : date.toLocalDate());
            } else if (a.type == Double.class) {
                Object o = resultSet.getObject(a.name());
                if (o == null) put(a, null);
                else put(a, ((Float) o).doubleValue());
            } else
                put(a, resultSet.getObject(a.name()));

    }

    /**
     * Get this school's id, as provided by the MySQL database, if it has been set.
     *
     * @return The {@link #id}.
     */
    public int getId() {
        return id;
    }

    /**
     * Get this school's district id, if it has been set.
     *
     * @return The {@link #district_id} (or -1 if it hasn't been set).
     */
    public int getDistrictId() {
        return district_id;
    }

    /**
     * Retrieve the {@link District} to which this {@link School} belongs by querying the Districts table in the
     * database.
     *
     * @return A new {@link District} object created from a SQL query. This will never be <code>null</code>.
     * @throws SQLException         If there is any error querying the database.
     * @throws NullPointerException If the {@link #district_id} is not set.
     */
    @NotNull
    public District getDistrict() throws SQLException {
        if (district_id == -1)
            throw new NullPointerException("Cannot retrieve parent District because the district_id is not set.");

        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM Districts WHERE id = ?");
            statement.setInt(1, district_id);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next())
                return new District(resultSet);
        }

        throw new SQLException("No District found with id " + district_id + ".");
    }

    /**
     * Get the values for an array of {@link Attribute Attributes}, expressed as a string with each attribute on its own
     * line.
     * <p>
     * For example, calling this function with the list <code>[{@link Attribute#name}, {@link Attribute#website_url},
     * {@link Attribute#address}]</code> might yield the following string:
     * <p>
     * <code>name: Virtue Academy<br>website_url: virtue-academy.com<br>address: 1234 Plato Blvd., St., U.S.</code>
     * <p>
     * The <code>includeId</code> parameter can also be used to add the {@link #id} to the string as the first
     * attribute.
     *
     * @param attributes       An array of zero or more attributes to include in the result. Note that <code>Null</code>
     *                         attributes are ignored, and duplicate attributes will be listed twice.
     * @param markedAttributes Any of the attributes in the main <code>attributes</code> array that are also present in
     *                         this one will be marked with an asterisk (*) before the name of the attribute. Attributes
     *                         present in this array but not in the main one are ignored.
     * @param includeId        Whether to include the {@link #id} in the result.
     *
     * @return A string with the values for the given attributes. If no attributes are given, the string will be empty.
     */
    public String getAttributeStr(Attribute[] attributes, Attribute[] markedAttributes, boolean includeId) {
        if (attributes == null || attributes.length == 0) return "";
        List<Attribute> marked = markedAttributes == null ? new ArrayList<>() : Arrays.asList(markedAttributes);

        // Generate each line of the result.
        List<String> lines = new ArrayList<>();

        if (includeId) lines.add("id: " + id);

        for (Attribute a : attributes)
            if (a != null)
                lines.add(String.format("%s%s: %s",
                        marked.contains(a) ? "* " : "",
                        a.name(),
                        get(a)
                ));

        // Determine the length of the longest line
        int longestLine = lines.stream()
                .mapToInt(String::length)
                .max().orElse(0);

        // Join the lines together with newlines. Pad each line to the length of the longest one.
        return lines.stream()
                .map(line -> String.format("%" + longestLine + "s", line))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Save a new value for some {@link Attribute Attribute} to this {@link School School's} list of
     * {@link #attributes}.
     *
     * @param attribute The attribute to save.
     * @param value     The value of the attribute. (This is {@link Attribute#clean(Object, School) cleaned} first).
     */
    public void put(@NotNull Attribute attribute, @Nullable Object value) {
        attributes.put(attribute, attribute.clean(value, this));
    }

    /**
     * Get the current value of some {@link Attribute} associated with this school. This queries the {@link #attributes}
     * map.
     *
     * @param attribute The attribute to retrieve.
     *
     * @return The current value of that attribute.
     */
    @Nullable
    public Object get(@NotNull Attribute attribute) {
        return attributes.get(attribute);
    }

    /**
     * {@link #get(Attribute) Get} the value of some {@link Attribute} as a {@link String}.
     *
     * @param attribute The attribute to retrieve.
     *
     * @return The current value of that attribute, or <code>null</code> if the attribute is not a string type.
     */
    @Nullable
    public String getStr(@NotNull Attribute attribute) {
        return (get(attribute) instanceof String s) ? s : null;
    }

    /**
     * This is a wrapper for {@link #get(Attribute)} that returns a boolean instead of a generic {@link Object}.
     *
     * @param attribute The attribute to retrieve (must be type {@link Boolean#TYPE}).
     *
     * @return The current value of that attribute, or <code>null</code> if the attribute is not a boolean type.
     */
    public boolean getBool(@NotNull Attribute attribute) {
        return (get(attribute) instanceof Boolean b) ? b : false;
    }

    /**
     * Determine whether the current value of some {@link Attribute} for this school is
     * {@link Attribute#isEffectivelyNull(Object) effectively null}. That may be because it is literally set to
     * <code>null</code>, or because it matches some null-like default value for that attribute.
     *
     * @param attribute The attribute to consider.
     *
     * @return True if and only if the current value of that attribute is effectively null.
     */
    public boolean isEffectivelyNull(@NotNull Attribute attribute) {
        return attribute.isEffectivelyNull(get(attribute));
    }

    /**
     * {@link #get(Attribute) Get} the {@link Attribute#name name} of this school. If the name attribute is
     * <code>null</code>, {@link Config#MISSING_NAME_SUBSTITUTION} is returned instead.
     *
     * @return The name.
     */
    @NotNull
    public String name() {
        Object o = get(Attribute.name);
        return o == null ? Config.MISSING_NAME_SUBSTITUTION.get() : (String) o;
    }

    /**
     * Get the {@link #name() name} of this school with a <code>.html</code> file extension. The name is cleaned using
     * {@link Utils#cleanFile(String, String) Utils.cleanFile()}. The name is followed by the
     * {@link LocalTime#now() current} {@link LocalTime#toNanoOfDay() nanoseconds} today, to help ensure unique file
     * names. This means that calling this method twice in a row will result in two different file names.
     *
     * @return The unique, cleaned file name.
     */
    @NotNull
    public String generateHtmlFileName() {
        return Utils.cleanFile(
                String.format("%s - %d", name(), LocalTime.now().toNanoOfDay()),
                "html"
        );
    }
}
