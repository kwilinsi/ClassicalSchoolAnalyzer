package constructs.school;

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
 * This is the set of attributes for {@link School Schools}. Each school has a map relating each attribute to its value
 * for that school.
 * <p>
 * This system is effectively an alternative to creating extensive data members with corresponding getters and setters
 * for the School class. Instead, all the attributes are consolidated to a single {@link java.util.LinkedHashMap Map}.
 * This allows them to be processed more easily, as well as allowing generic functions such as
 * {@link #matches(School, School) matches()} to operate on all attributes.
 * <p>
 * <b>Note:</b> These attributes have a one-to-one correspondence with the columns of the Schools table in the
 * database, as specified by the setup script. The only exception is that there is no enumerated attribute for the
 * school's id. All other attributes are spelled, capitalized, and ordered exactly as they appear in the database.
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
     * <p>
     * Note: Some attributes have the {@link URL} type. That doesn't mean they actually use the URL class to store the
     * data. Instead, it means that the value is a {@link String}, but it represents a URL; therefore two values of this
     * type should be {@link #matches(Object, Object) compared} differently than regular strings.
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
     * This simply returns <code>true</code> if this attribute is {@link #is_excluded} or {@link #excluded_reason}.
     * Those attributes aren't included in a number of comparisons between schools, and they should only be modified in
     * the database following explicit instruction from the user.
     *
     * @return <code>True</code> if and only if this attribute is related to a school being excluded.
     */
    public boolean isExclusionRelated() {
        return this == is_excluded || this == excluded_reason;
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
    public void addToStatement(@NotNull PreparedStatement statement,
                               @Nullable Object value,
                               int position) throws SQLException {
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
     * Determine whether two schools share the same value for this attribute. This is done via a call to
     * {@link #matches(Object, Object)} with the values of this attribute for each school.
     *
     * @param schoolA The first school.
     * @param schoolB The second school.
     *
     * @return The resulting {@link MatchLevel}.
     * @see #matches(Object, Object)
     */
    @NotNull
    public MatchLevel matches(@NotNull School schoolA, @NotNull School schoolB) {
        return matches(schoolA.get(this), schoolB.get(this));
    }

    /**
     * Determine whether two values match for this attribute. This is returned as a {@link MatchLevel} that indicates
     * the degree to which the values match.
     * <p>
     * <h3>Procedure:</h3>
     * The procedure for assessing a possible match is as follows:
     * <ol>
     *     <li>The objects are compared via {@link Objects#equals(Object, Object) Objects.equals()}. If this yields
     *     <code>true</code>, {@link MatchLevel#EXACT EXACT} is returned.
     *     <li>If both values are {@link #isEffectivelyNull(Object) effectively null},
     *     {@link MatchLevel#INDICATOR INDICATOR} is returned.
     *     <li>If either value is {@link #isEffectivelyNull(Object) effectively null}, {@link MatchLevel#NONE NONE}
     *     is returned, because with only one null value they clearly don't match.
     *     <li>{@link #isExactMatch(Object, Object) isExactMatch()} is used to give the values one last change to be an
     *     exact match. This allows certain attribute types to be compared slightly differently. If this yields
     *     <code>true</code>, {@link MatchLevel#EXACT EXACT} is returned.
     *     <li>{@link #isIndicatorMatch(Object, Object) isIndicatorMatch()} is used to check for an
     *     {@link MatchLevel#INDICATOR INDICATOR} level match. If it returns <code>true</code>, the indicator level
     *     is returned.
     *     <li>{@link #isRelatedMatch(Object, Object) isPossibleMatch()} is used to check for a
     *     {@link MatchLevel#RELATED POSSIBLE} match. If it returns <code>true</code>, the possible level is returned.
     *     <li>The values clearly don't match at all. {@link MatchLevel#NONE NONE} is returned.
     * </ol>
     *
     * @param valA The first value.
     * @param valB The second value.
     *
     * @return <code>True</code> if and only if the values match.
     * @see #matches(School, School)
     */
    @NotNull
    public MatchLevel matches(@Nullable Object valA, @Nullable Object valB) {
        // Deal with possibly null values
        if (Objects.equals(valA, valB)) return MatchLevel.EXACT;
        boolean nullA = isEffectivelyNull(valA), nullB = isEffectivelyNull(valB);
        if (nullA && nullB) return MatchLevel.INDICATOR;
        if (nullA || nullB) return MatchLevel.NONE;

        // Check for an EXACT match
        if (isExactMatch(valA, valB)) return MatchLevel.EXACT;

        // Check for an INDICATOR match
        if (isIndicatorMatch(valA, valB)) return MatchLevel.INDICATOR;

        // Check for a POSSIBLE match
        if (isRelatedMatch(valA, valB)) return MatchLevel.RELATED;

        // There's no match
        return MatchLevel.NONE;
    }

    /**
     * Determine whether two values are an {@link MatchLevel#EXACT EXACT} match for this attribute.
     * <p>
     * <h3>Disambiguation:</h3>
     * Unlike {@link #matches(Object, Object)}, this does not check for possibly <code>null</code> values. Instead, it
     * operates with the prediction that neither value is <code>null</code> or
     * {@link #isEffectivelyNull(Object) effectively null}. Additionally, it presumes that
     * {@link Objects#equals(Object, Object) Objects.equals()} was already used to test for an exact match.
     * <p>
     * For these reasons, this method is not particularly useful for actually assessing an exact match between two
     * values, except as a helper method for {@link #matches(Object, Object) matches()}. For that reason, it has
     * <code>private</code> scope in this class.
     * <p>
     * <h3>Procedure:</h3>
     * This method handles the following {@link #type types} specially:
     * <ul>
     *     <li>{@link LocalDate LocalDates} are compared via
     *     {@link LocalDate#isEqual(ChronoLocalDate) LocalDate.isEqual()}.
     *     <li>{@link Double Doubles} are compared up to a precision of 0.00001.
     *     <li>All other attribute types return <code>false</code>.
     * </ul>
     *
     * @param valA The first value.
     * @param valB The second value.
     *
     * @return <code>True</code> if and only if the values are an {@link MatchLevel#EXACT EXACT} match.
     */
    private boolean isExactMatch(@NotNull Object valA, @NotNull Object valB) {
        if (type == LocalDate.class)
            return ((LocalDate) valA).isEqual((LocalDate) valB);

        if (type == Double.class || type == Double.TYPE)
            return Math.abs((Double) valA - (Double) valB) <= 0.00001;

        return false;
    }

    /**
     * Determine whether two values are an {@link MatchLevel#INDICATOR INDICATOR} match for this attribute.
     * <p>
     * This is done differently based on the {@link #type} of this {@link Attribute}. In all cases, it's assumed that
     * neither value is <code>null</code> or {@link #isEffectivelyNull(Object) effectively null}.
     * <ul>
     *     <li> For {@link URL} types, the values are treated as {@link String Strings}, converted to {@link URL URLs},
     *     and compared via {@link URLUtils#equals(URL, URL) URLUtils.equals()}.
     *     <li>{@link #grades_offered} and {@link #address}: <i>To be implemented</i>.
     * </ul>
     *
     * @param valA The first value.
     * @param valB The second value.
     *
     * @return <code>True</code> if and only if the values are an {@link MatchLevel#INDICATOR INDICATOR} match.
     * @see #matches(Object, Object)
     * @see #isExactMatch(Object, Object)
     * @see #isRelatedMatch(Object, Object)
     */
    private boolean isIndicatorMatch(@NotNull Object valA, @NotNull Object valB) {
        if (type == URL.class)
            try {
                return URLUtils.equals(new URL((String) valA), new URL((String) valB));
            } catch (MalformedURLException | NullPointerException e) {
                logger.warn(String.format("Failed to parse URLs %s and %s.", valA, valB), e);
                return false;
            }

        // TODO implement better matching for grades_offered, and maybe even addresses
        return false;
    }

    /**
     * Determine whether two values are a {@link MatchLevel#RELATED POSSIBLE} match for this attribute.
     * <p>
     * This is done differently based on the {@link #type} of this {@link Attribute}. In all cases, it's assumed that
     * neither value is <code>null</code> or {@link #isEffectivelyNull(Object) effectively null}.
     * <ul>
     *     <li> For {@link #website_url} attribute only, the values are compared via
     *     {@link URLUtils#hostEquals(String, String) URLUtils.hostEquals()}. Thus, only the hosts of the URLs must
     *     be the same for this to be a possible match.
     * <ul>
     *
     * @param valA The first value.
     * @param valB The second value.
     *
     * @return <code>True</code> if and only if the values are a {@link MatchLevel#RELATED POSSIBLE} match.
     * @see #matches(Object, Object)
     * @see #isExactMatch(Object, Object)
     * @see #isIndicatorMatch(Object, Object)
     */
    private boolean isRelatedMatch(@NotNull Object valA, @NotNull Object valB) {
        if (this == website_url)
            return URLUtils.hostEquals(String.valueOf(valA), String.valueOf(valB));

        return false;
    }
}
