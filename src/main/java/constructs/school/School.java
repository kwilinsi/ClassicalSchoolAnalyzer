package constructs.school;

import constructs.BaseConstruct;
import constructs.district.District;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Config;
import database.Database;
import utils.Utils;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class School extends BaseConstruct {
    /**
     * This is the id of the {@link District} to which this school belongs. Most schools are in a district by
     * themselves. However, some schools are paired with others in a district (such as in the case of separate
     * elementary, middle, and high schools).
     * <p>
     * By default, this is set to <code>-1</code> to indicate no assigned district.
     */
    protected int district_id = -1;

    /**
     * This is the set of {@link Attribute Attributes} and their values for this school.
     */
    @NotNull
    protected final Map<Attribute, Object> attributes;

    /**
     * The unique <code>id</code> for this school that was provided by MySQL through the AUTO_INCREMENT id column. This
     * is typically only set for {@link School Schools} created from a {@link ResultSet}.
     * <p>
     * By default, this is set to <code>-1</code> to indicate no assigned id.
     */
    protected int id = -1;

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
     * Set this school's district id.
     *
     * @param district_id The {@link #district_id}.
     */
    public void setDistrictId(int district_id) {
        this.district_id = district_id;
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

    /**
     * Get a quick string representation of this school. This will take one of the following forms:
     * <ul>
     *     <li>If the {@link Attribute#name name} and {@link #id} are set, the string <code>"[name] ([id])"</code> is
     *     returned.
     *     <li>If the id is not set, the result of calling {@link #name()} is returned.
     * </ul>
     *
     * @return A string representation of this school.
     */
    @Override
    public String toString() {
        if (id != -1)
            return String.format("%s (%d)", name(), id);
        else
            return name();
    }
}
