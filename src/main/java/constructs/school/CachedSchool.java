package constructs.school;

import constructs.CachedConstruct;
import constructs.district.CachedDistrict;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Utils;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * A cached school is a subclass of {@link School} that is designed to represent either schools already in the SQL
 * database or schools which still need to be added. After its initial creation, all changes to the attributes of
 * this school are tracked in a second {@link #newAttributes list} of values.
 * <p>
 * From there, it's easy to check whether this school {@link #didChange() changed} since its initial creation, and a
 * SQL statement can be generated to add or update this school in the database.
 * <p>
 * Note that the {@link #id} is not tracked, as it is necessary for identifying the database school when
 * {@link #toUpdateStatement(Connection) updating} it.
 */
public class CachedSchool extends School implements CachedConstruct {
    /**
     * Records whether this cached school is new, meaning it isn't stored in the database yet. Cached schools
     * {@link #CachedSchool(ResultSet) created} from a SQL {@link ResultSet} are marked <i>not</i> new
     * (<code>false</code>), and those {@link #CachedSchool(CreatedSchool) created} from a created school are marked
     * new (<code>true</code>).
     *
     * @see #isNew()
     */
    private final boolean isNew;

    /**
     * The list of values for the new attributes. These are the attribute values for this school that are not yet
     * recorded in the database.
     */
    private final Map<Attribute, Object> newAttributes = new LinkedHashMap<>();

    /**
     * Sometimes, a cached school doesn't know its {@link #district_id}, because it is a {@link #isNew() new} school
     * not yet in the database, and its district is also new. In that case, this records the actual
     * {@link CachedDistrict} object to which this school belongs. After that district has been
     * {@link CachedDistrict#addToInsertStatement(PreparedStatement) added} to the database, its
     * {@link CachedDistrict#getId() id} can be copied and set as this school's district id.
     */
    @Nullable
    private CachedDistrict district;

    /**
     * Created a cached school by copying a {@link CreatedSchool}. Here, since a created school represents a school
     * not yet in the database, all the attributes are treated as {@link #newAttributes new} values.
     * <p>
     * This is treated as a new school; {@link #isNew} is set to <code>true</code>.
     * <p>
     * Note that this conversion loses access to features and data members unique to created schools, such as the
     * {@link CreatedSchool#getOrganization() organization}.
     *
     * @param school The school to copy. Note that this creates a shallow copy (meaning it doesn't make deep copies of
     *               the objects referenced by this school's attributes).
     */
    public CachedSchool(@NotNull CreatedSchool school) {
        super();
        this.isNew = true;
        this.newAttributes.putAll(school.attributes);
    }

    /**
     * Create a cached school from a {@link ResultSet}, the result of a query of the Schools table. It's expected that
     * "<code>SELECT *</code>" was used, meaning the <code>resultSet</code> contains every {@link Attribute}/column.
     * <p>
     * This is considered not a {@link #isNew new} school.
     *
     * @param resultSet The result set of the query.
     * @throws SQLException If there is any error parsing the <code>resultSet</code>.
     */
    @SuppressWarnings("unused")
    public CachedSchool(@NotNull ResultSet resultSet) throws SQLException {
        super();

        this.isNew = false;
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

    @Nullable
    public CachedDistrict getDistrict() {
        return district;
    }

    /**
     * Save a new value for some attribute to this school. If that value is the same as the old value stored in the
     * initial {@link #attributes} map, any new values are cleared. However, if this is a new value for the
     * attribute, it is added to the {@link #newAttributes} map.
     * <p>
     * Note that the value is {@link Attribute#clean(Object, School) cleaned} first before making any comparisons or
     * saving anything.
     *
     * @param attribute The attribute to save.
     * @param value     The value of the attribute.
     */
    @Override
    public void put(@NotNull Attribute attribute, @Nullable Object value) {
        value = attribute.clean(value, this);

        if (Objects.equals(value, attributes.get(attribute)))
            newAttributes.remove(attribute);
        else
            newAttributes.put(attribute, value);
    }

    /**
     * Get the value for an attribute, preferring the {@link #newAttributes new} value if one is
     * {@link Map#containsKey(Object) present}. If there is no new value for this attribute, the original value is
     * returned.
     *
     * @param attribute The attribute to retrieve.
     * @return The current value of that attribute.
     */
    @Override
    public @Nullable Object get(@NotNull Attribute attribute) {
        if (newAttributes.containsKey(attribute))
            return newAttributes.get(attribute);
        else
            return attributes.get(attribute);
    }

    /**
     * Set the district to which this school belongs.
     * <p>
     * Use this <b>only</b> when you know what district object this school belongs to but not that district's
     * {@link CachedDistrict#getId() id}. This is <b>not</b> a general-purpose substitute for
     * {@link #setDistrictId(int)}.
     * <p>
     * When generating the SQL for an {@link #addToInsertStatement(PreparedStatement) insert} statement to add this
     * school to the database, the district's id is retrieved. Make sure the district has been added to the database
     * by then.
     *
     * @param district The {@link #district}.
     */
    public void setDistrict(@Nullable CachedDistrict district) {
        this.district = district;
    }

    @Override
    public boolean didChange() {
        return !newAttributes.isEmpty();
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /**
     * Check to ensure this {@link #usesInsert() uses} an <code>INSERT</code> statement before calling the
     * {@link School#addToInsertStatement(PreparedStatement) super} implementation.
     *
     * @param statement The statement to modify.
     * @throws SQLException             If there is an error modifying the statement.
     * @throws IllegalArgumentException If this instance should be using an {@link #usesUpdate() update} statement
     *                                  instead.
     */
    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement)
            throws IllegalArgumentException, IllegalStateException, SQLException {
        if (!usesInsert())
            throw new IllegalArgumentException("Cannot add school " + this + " to INSERT statement");

        if (district_id == -1) {
            if (district == null)
                throw new IllegalStateException("District id unknown for " + this + ", and district is null");
            else if (district.getId() == -1)
                throw new IllegalStateException("District id for " + district + " is not set");
            else
                district_id = district.getId();
        }

        super.addToInsertStatement(statement);
    }

    @Override
    public @NotNull PreparedStatement toUpdateStatement(@NotNull Connection connection)
            throws IllegalArgumentException, SQLException {
        if (!usesUpdate())
            throw new IllegalArgumentException("Cannot create UPDATE statement for district " + this + ".");

        PreparedStatement statement = connection.prepareStatement(String.format(
                "UPDATE Schools %s WHERE id = ?;",
                Utils.generateSQLStmtArgs(Attribute.toNames(newAttributes.keySet()), false)
        ));

        List<Attribute> attributes = new ArrayList<>(newAttributes.keySet());
        for (int i = 0; i < attributes.size(); i++)
            attributes.get(i).addToStatement(statement, get(attributes.get(i)), i + 1);

        statement.setInt(attributes.size() + 1, id);
        return statement;
    }

    @Override
    public void saveChanges() {
        // No need to clean the values, as they've already been cleaned if they're in newAttributes
        attributes.putAll(newAttributes);
        newAttributes.clear();

        // Copy the district id if that hasn't been done yet, and clear the district
        if (district_id == -1 && district != null)
            district_id = district.getId();
        district = null;
    }
}
