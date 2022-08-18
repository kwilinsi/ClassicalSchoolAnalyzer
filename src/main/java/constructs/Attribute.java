package constructs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.URLUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.util.Objects;

/**
 * This is the set of attributes that a school may have.
 * <p>
 * <b>Note:</b> These attributes have a one-to-one correspondence with the columns of the Schools table in the
 * database, as specified by the setup script. The only exception is that there is no enumerated attribute for the
 * school's id. All other attributes are spelled and ordered exactly as they appear in the database.
 */
public enum Attribute {
    name(String.class, null, 100),
    phone(String.class, null, 20),
    address(String.class, null, 100),
    mailing_address(String.class, null, 100),
    city(String.class, null, 50),
    state(String.class, null, 40),
    country(String.class, null, 30),
    website_url(URL.class, null, 300),
    website_url_redirect(URL.class, null, 300),
    has_website(Boolean.TYPE, false),
    contact_name(String.class, null, 100),
    accs_accredited(Boolean.class, null),
    office_phone(String.class, null, 20),
    date_accredited(LocalDate.class, null),
    year_founded(Integer.class, null),
    grades_offered(String.class, null, 100),
    membership_date(LocalDate.class, null),
    enrollment(Integer.class, null),
    number_of_students_k_6(Integer.class, null),
    number_of_students_k_6_non_traditional(Integer.class, null),
    classroom_format(String.class, null, 100),
    number_of_students_7_12(Integer.class, null),
    number_of_students_7_12_non_traditional(Integer.class, null),
    number_of_teachers(Integer.class, null),
    student_teacher_ratio(String.class, null, 50),
    international_student_program(Boolean.class, null),
    tuition_range(String.class, null, 50),
    headmaster_name(String.class, null, 100),
    church_affiliated(Boolean.class, null),
    chairman_name(String.class, null, 100),
    accredited_other(String.class, null, 300),
    latitude(Double.class, null),
    longitude(Double.class, null),
    lat_long_accuracy(String.class, null, 25),
    projected_opening(String.class, null, 20),
    bio(String.class, null, 65535),
    accs_page_url(URL.class, null, 300),
    hillsdale_affiliation_level(String.class, null, 50),
    icle_page_url(URL.class, null, 300),
    icle_affiliation_level(String.class, null, 25),
    is_excluded(Boolean.TYPE, false),
    excluded_reason(String.class, null, 100);

    private static final Logger logger = LoggerFactory.getLogger(Attribute.class);

    /**
     * The data type of the attribute.
     */
    public final Class<?> type;

    /**
     * The default value assigned to this attribute if it is not set.
     */
    public final Object defaultValue;

    /**
     * This is the maximum length of the string value of this attribute. Set this to -1 if it doesn't apply.
     */
    public final int maxLength;

    <T> Attribute(Class<T> type, T defaultValue, int maxLength) {
        this.type = type;
        this.defaultValue = defaultValue;
        this.maxLength = maxLength;
    }

    <T> Attribute(Class<T> type, T defaultValue) {
        this(type, defaultValue, -1);
    }

    /**
     * Clean the given value according to the constraints imposed by this attribute within SQL. This is used to fix the
     * length of strings to their {@link #maxLength}. If a value is changed by this method, a warning is logged to the
     * console.
     *
     * @param input  The value to clean.
     * @param school The school to which the value belongs. This is used exclusively for logging purposes.
     *
     * @return The cleaned value.
     */
    @Nullable
    public Object clean(@Nullable Object input, @NotNull School school) {
        if (input == null) return null;

        // If this attribute is a string and the input is a string and the input is too long, truncate it
        if (type == String.class || type == URL.class)
            if (input instanceof String s && s.length() > maxLength) {
                logger.warn("Trimmed {} for school {} to max {} characters.", name(), school.name(), maxLength);
                return s.substring(0, maxLength);
            }

        return input;
    }

    /**
     * Add some value to a {@link PreparedStatement} based on the {@link #type} of this {@link Attribute Attribute}.
     *
     * @param statement The statement to add the value to.
     * @param value     The value to add.
     * @param position  The position to add the value to.
     *
     * @throws SQLException             If there is an error adding the value to the statement.
     * @throws IllegalArgumentException If this {@link Attribute Attribute's} type isn't recognized.
     */
    public void addToStatement(PreparedStatement statement, Object value, int position) throws SQLException {
        // Handle null values
        if (value == null) {
            int sqlType;
            if (type == String.class || type == URL.class)
                sqlType = Types.VARCHAR;
            else if (type == Integer.class)
                sqlType = Types.INTEGER;
            else if (type == Boolean.class)
                sqlType = Types.BOOLEAN;
            else if (type == LocalDate.class)
                sqlType = Types.DATE;
            else if (type == Double.class)
                sqlType = Types.DOUBLE;
            else
                throw new IllegalArgumentException("Unknown type: " + type);
            statement.setNull(position, sqlType);
            return;
        }

        // Handle non-null values
        if (type == String.class || type == URL.class)
            statement.setString(position, (String) value);
        else if (type == Integer.class || type == Integer.TYPE)
            statement.setInt(position, (int) value);
        else if (type == Boolean.class || type == Boolean.TYPE)
            statement.setBoolean(position, (boolean) value);
        else if (type == LocalDate.class)
            statement.setDate(position, Date.valueOf((LocalDate) value));
        else if (type == Double.class || type == Double.TYPE)
            statement.setDouble(position, (double) value);
        else
            throw new IllegalArgumentException("Unknown type: " + type);
    }

    /**
     * Determine whether some value is effectively null for this {@link Attribute}. Typically, this just means testing
     * whether the passed <code>value</code> parameter is <code>null</code>, but for certain attributes the test is
     * different.
     *
     * @param value The value to test.
     *
     * @return <code>True</code> if and only if the value is effectively null.
     */
    public boolean isEffectivelyNull(@Nullable Object value) {
        if (value == null) return true;

        if (this.equals(Attribute.name) && Config.MISSING_NAME_SUBSTITUTION.get().equalsIgnoreCase((String) value))
            return true;

        if (type == String.class || type == URL.class)
            return ((String) value).isBlank() || ((String) value).trim().equals("null");

        return false;
    }

    /**
     * Determine whether two {@link School Schools} have the same value for this attribute.
     * <p>
     * The values for attributes are compared via {@link Objects#equals(Object, Object)}. However, some attributes use
     * different comparison rules based on their data type:
     * <ul>
     *     <li>{@link URL URLs} are compared via {@link URLUtils#equals(URL, URL) URLUtils.equals()}.
     *     <li>{@link LocalDate LocalDates} are compared via
     *     {@link LocalDate#isEqual(ChronoLocalDate) LocalDate.isEqual()}.
     *     <li>{@link Double Doubles} are compared up to a precision of 0.00001.
     * </ul>
     *
     * @param schoolA The first school.
     * @param schoolB The second school.
     *
     * @return <code>True</code> if and only if the two schools have the same value for this attribute;
     *         <code>false</code> otherwise.
     */
    public boolean matches(@NotNull School schoolA, @NotNull School schoolB) {
        Object valA = schoolA.get(this);
        Object valB = schoolB.get(this);

        // If they're both null, they match
        if (isEffectivelyNull(valA) && isEffectivelyNull(valB)) return true;
        // Otherwise, if one is null, they don't match
        if (valA == null || valB == null) return false;

        //
        // Run separate comparisons for certain data types
        //

        if (type == URL.class)
            try {
                return URLUtils.equals(new URL((String) valA), new URL((String) valB));
            } catch (MalformedURLException | NullPointerException e) {
                logger.warn(String.format("Failed to parse URLs %s and %s.", valA, valB), e);
                return false;
            }

        if (type == LocalDate.class)
            return ((LocalDate) valA).isEqual((LocalDate) valB);

        if (type == Double.class || type == Double.TYPE)
            return Math.abs((Double) valA - (Double) valB) <= 0.00001;

        // Generic match for all other attributes
        return Objects.equals(valA, valB);
    }

    /**
     * This is a particular implementation of {@link #matches(School, School) matches()} that is designed specifically
     * for matching the {@link Organization#getMatchIndicatorAttributes() matchIndicatorAttributes} associated with each
     * {@link Organization}.
     * <p>
     * Match detection in this method exhibits the following behavior:
     * <ul>
     *     <li>If the value of this attribute is {@link #isEffectivelyNull(Object) effectively null} for either
     *     school, the match returns <code>false</code>.
     *     <li>Exclusively for the {@link #website_url} attribute, {@link URLUtils#domainEquals(String, String)
     *     URLUtils.domainEquals()} is used to determine a match.
     *     <li>For all other attributes, the standard {@link #matches(School, School) matches()} method is used.
     * </ul>
     *
     * @param schoolA The first school.
     * @param schoolB The second school.
     *
     * @return <code>True</code> if and only if the value of this attribute for both schools is determined to match
     *         according to the above procedure.
     */
    public boolean schoolIndicatorMatches(@NotNull School schoolA, @NotNull School schoolB) {
        if (isEffectivelyNull(schoolA.get(this))) return false;

        // TODO implement better matching for grades_offered, and maybe even addresses

        if (this == website_url)
            return URLUtils.domainEquals(schoolA.getStr(this), schoolB.getStr(this));

        return matches(schoolA, schoolB);
    }
}
