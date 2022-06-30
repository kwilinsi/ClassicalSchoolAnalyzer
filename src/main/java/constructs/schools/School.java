package constructs.schools;

import constructs.BaseConstruct;
import constructs.organizations.Organization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class School extends BaseConstruct {
    private static final Logger logger = LoggerFactory.getLogger(School.class);

    @NotNull
    private final Organization organization;

    @NotNull
    private final Map<Attribute, Object> attributes;

    /**
     * Create a new {@link School} by providing the {@link Organization} it comes from. Everything else is added later
     * via {@link #put(Attribute, Object)}.
     */
    public School(@NotNull Organization organization) {
        this.organization = organization;

        // Add all the Attributes to the attributes map.
        attributes = new LinkedHashMap<>(Attribute.values().length);
        for (Attribute attribute : Attribute.values())
            attributes.put(attribute, attribute.defaultValue);

        // Save the organization id
        put(Attribute.organization_id, organization.get_id());
    }

    /**
     * Save a new value for some {@link Attribute Attribute} to this {@link School School's} list of {@link
     * #attributes}.
     *
     * @param attribute The attribute to save.
     * @param value     The value of the attribute.
     */
    public void put(@NotNull Attribute attribute, @Nullable Object value) {
        attributes.put(attribute, value);
    }

    /**
     * Get the current value of some {@link Attribute} associated with this school. This queries the {@link #attributes}
     * map.
     *
     * @param attribute The attribute to retrieve.
     *
     * @return The current value of that attribute.
     */
    public Object get(@NotNull Attribute attribute) {
        return attributes.get(attribute);
    }

    public void saveToDatabase() throws SQLException {
        logger.debug("Saving school " + get(Attribute.name) + " to database.");

        // Construct the SQL statement to insert this school
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        StringBuilder conflicts = new StringBuilder();

        for (Attribute attribute : Attribute.values()) {
            columns.append(attribute.name()).append(", ");
            placeholders.append("?, ");
            conflicts.append(attribute.name()).append(" = ?, ");
        }

        // Remove trailing commas from each string
        columns.delete(columns.length() - 2, columns.length());
        placeholders.delete(placeholders.length() - 2, placeholders.length());
        conflicts.delete(conflicts.length() - 2, conflicts.length());

        String sql = String.format(
                "INSERT INTO Schools (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
                columns, placeholders, conflicts
        );

        // Open database connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add values to the statement according to their type
            Attribute[] values = Attribute.values();
            for (int i = 0; i < values.length; i++) {
                Attribute attribute = values[i];
                attribute.addToStatement(statement, attributes.get(attribute), i + 1);
                attribute.addToStatement(statement, attributes.get(attribute), i + 1 + values.length);
            }

            // Execute the finished statement
            statement.execute();
        }
    }

    @NotNull
    public Organization get_organization() {
        return organization;
    }

    /**
     * This is the list of attributes that a school may have.
     * <p>
     * <b>Note:</b> The order here is intentional. It is the same order of the columns of the Schools table in
     * the database.
     */
    public enum Attribute {
        name(String.class, null),
        organization_id(Integer.TYPE, -1),
        phone(String.class, null),
        address(String.class, null),
        state(String.class, null),
        country(String.class, null),
        website_url(String.class, null),
        website_url_redirect(String.class, null),
        has_website(Boolean.TYPE, false),
        contact_name(String.class, null),
        accs_accredited(Boolean.class, null),
        office_phone(String.class, null),
        date_accredited(LocalDate.class, null),
        year_founded(Integer.class, null),
        grades_offered(String.class, null),
        membership_date(LocalDate.class, null),
        number_of_students_k_6(Integer.class, null),
        number_of_students_k_6_non_traditional(Integer.class, null),
        classroom_format(String.class, null),
        number_of_students_7_12(Integer.class, null),
        number_of_students_7_12_non_traditional(Integer.class, null),
        number_of_teachers(Integer.class, null),
        student_teacher_ratio(String.class, null),
        international_student_program(Boolean.class, null),
        tuition_range(String.class, null),
        headmaster_name(String.class, null),
        church_affiliated(Boolean.class, null),
        chairman_name(String.class, null),
        accredited_other(String.class, null),
        is_excluded(Boolean.TYPE, false),
        excluded_reason(String.class, null);

        private final Class<?> type;
        private final Object defaultValue;

        <T> Attribute(Class<T> type, T defaultValue) {
            this.type = type;
            this.defaultValue = defaultValue;
        }

        /**
         * Add some value to a {@link PreparedStatement} based on the {@link #type} of this {@link Attribute
         * Attribute}.
         *
         * @param statement The statement to add the value to.
         * @param value     The value to add.
         * @param position  The position to add the value to.
         *
         * @throws SQLException             If there is an error adding the value to the statement.
         * @throws IllegalArgumentException If this {@link Attribute Attribute's} type isn't recognized.
         */
        public void addToStatement(PreparedStatement statement, Object value, int position) throws SQLException {
            if (value == null) {
                int sqlType;
                if (type == String.class)
                    sqlType = Types.VARCHAR;
                else if (type == Integer.class)
                    sqlType = Types.INTEGER;
                else if (type == Boolean.class)
                    sqlType = Types.BOOLEAN;
                else if (type == LocalDate.class)
                    sqlType = Types.DATE;
                else
                    throw new IllegalArgumentException("Unknown type: " + type);
                statement.setNull(position, sqlType);
                return;
            }

            if (type == String.class)
                statement.setString(position, (String) value);
            else if (type == Integer.class || type == Integer.TYPE)
                statement.setInt(position, (int) value);
            else if (type == Boolean.class || type == Boolean.TYPE)
                statement.setBoolean(position, (boolean) value);
            else if (type == LocalDate.class)
                statement.setDate(position, Date.valueOf((LocalDate) value));
            else
                throw new IllegalArgumentException("Unknown type: " + type);

        }
    }
}
