package constructs.schools;

import constructs.BaseConstruct;
import constructs.organizations.Organization;
import constructs.organizations.OrganizationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Config;
import utils.Database;
import utils.Prompt;
import utils.Prompt.Selection;
import utils.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.*;

public class School extends BaseConstruct {
    private static final Logger logger = LoggerFactory.getLogger(School.class);
    /**
     * This is a list of attributes that are used to check whether this {@link School} {@link #determineMatch() matches}
     * an existing school in the SQL database.
     */
    @NotNull
    protected final List<Attribute> matchAttributes = new ArrayList<>();
    @NotNull
    private final Organization organization;
    @NotNull
    private final Map<Attribute, Object> attributes;
    /**
     * The unique <code>id</code> for this school that was provided by MySQL through the AUTO_INCREMENT id column. This
     * is typically only set for {@link School Schools} created from a {@link ResultSet}.
     */
    private int id;

    /**
     * Create a new {@link School} by providing the {@link Organization} it comes from. This also sets the {@link
     * Attribute#organization_id organization_id} attribute. Everything else is added later via {@link #put(Attribute,
     * Object)}.
     */
    public School(@NotNull Organization organization) {
        this.organization = organization;

        // Add all the Attributes to the attributes map.
        attributes = new LinkedHashMap<>(Attribute.values().length);
        for (Attribute attribute : Attribute.values())
            attributes.put(attribute, attribute.defaultValue);

        // Save the organization id
        put(Attribute.organization_id, organization.get_id());

        // The following attributes (when they match for two schools) are good indicators of those schools being the
        // same.
        matchAttributes.add(Attribute.name);
        matchAttributes.add(Attribute.website_url);
        matchAttributes.add(Attribute.address);
        matchAttributes.add(Attribute.phone);
    }

    /**
     * Create a {@link School} from a {@link ResultSet}, the result of a query of the Schools table. It's expected that
     * "SELECT *" was used, and so the resultSet contains every {@link Attribute}/column.
     *
     * @param resultSet The result set of the query.
     *
     * @throws SQLException         if there is any error parsing the resultSet.
     * @throws NullPointerException if the {@link Attribute#organization_id organization_id} in the resultSet does not
     *                              correspond to one of the {@link OrganizationManager#ORGANIZATIONS organizations}.
     */
    public School(@NotNull ResultSet resultSet) throws SQLException {
        this(Objects.requireNonNull(OrganizationManager.getById(resultSet.getInt(Attribute.organization_id.name()))));

        this.id = resultSet.getInt("id");

        for (Attribute a : Attribute.values())
            if (!a.equals(Attribute.organization_id))
                put(a, resultSet.getObject(a.name()));
    }

    /**
     * Save a new value for some {@link Attribute Attribute} to this {@link School School's} list of {@link
     * #attributes}.
     *
     * @param attribute The attribute to save.
     * @param value     The value of the attribute.
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
     * This is a wrapper for {@link #get(Attribute)} that returns a boolean instead of a generic {@link Object}.
     *
     * @param attribute The attribute to retrieve (must be type {@link Boolean#TYPE}).
     *
     * @return The current value of that attribute.
     */
    public boolean getBool(@NotNull Attribute attribute) {
        Object o = get(attribute);
        return o instanceof Boolean && (Boolean) o;
    }

    /**
     * Determine whether the current value of some {@link Attribute} for this school is {@link
     * Attribute#isEffectivelyNull(Object) effectively null}. That may be because it is literally set to
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
     * {@link Utils#cleanFile(String, String) Utils.cleanFile()}. The name is followed by the {@link LocalTime#now()
     * current} {@link LocalTime#toNanoOfDay() nanoseconds} today, to help ensure unique file names. This means that
     * calling this method twice in a row will result in two different file names.
     *
     * @return The unique, cleaned file name.
     */
    @NotNull
    public String getHtmlFile() {
        return Utils.cleanFile(
                String.format("%s - %d", name(), LocalTime.now().toNanoOfDay()),
                "html"
        );
    }

    /**
     * Determine whether this {@link School} should be marked {@link Attribute#is_excluded excluded} in the SQL table,
     * and save the result in the {@link #attributes} map accordingly.
     * <p>
     * A school can be automatically excluded for two reasons:
     * <ol>
     *     <li>The {@link Attribute#name name} is {@link #isEffectivelyNull(Attribute) effectively null}.
     *     <li>{@link Attribute#has_website has_website} is <code>false</code>.
     * </ol>
     * <p>
     * If either of these conditions are met (or both), the school is automatically excluded and the
     * {@link Attribute#excluded_reason excluded_reason} attribute is set appropriately.
     * <p>
     * If neither condition is met, this does <b>not</b> change the {@link Attribute#is_excluded is_excluded} or
     * {@link Attribute#excluded_reason excluded_reason} attributes. They are left as-is.
     */
    public void checkExclude() {
        boolean noName = isEffectivelyNull(Attribute.name);
        boolean noWebsite = !getBool(Attribute.has_website);

        if (noName && noWebsite) {
            put(Attribute.is_excluded, true);
            put(Attribute.excluded_reason, "Name and website_url are missing.");
        } else if (noName) {
            put(Attribute.is_excluded, true);
            put(Attribute.excluded_reason, "Name is missing.");
        } else if (noWebsite) {
            put(Attribute.is_excluded, true);
            put(Attribute.excluded_reason, "Website URL is missing.");
        }
    }

    /**
     * Determine whether this {@link School} has a website. This is done by checking whether the {@link
     * Attribute#website_url website_url} is {@link #isEffectivelyNull(Attribute) effectively null}. If it is, {@link
     * Attribute#has_website has_website} is set to <code>false</code>; otherwise it's set to <code>true</code>.
     */
    public void checkHasWebsite() {
        put(Attribute.has_website, !isEffectivelyNull(Attribute.website_url));
    }

    /**
     * Determine whether the SQL database already contains this {@link School}.
     * <h2>Approach</h2>
     * This method performs the following steps:
     * <ol>
     *     <li>Identify a {@link #matchAttributes list} of {@link Attribute Attributes} to consider in identifying a
     *     matching school from the database.
     *     <li>Query the database to look for schools matching any of those attributes.
     *     <li>Determine whether there is a match, possibly by {@link Prompt prompting} the user.
     *     <li>Return some {@link SchoolMatchResult result} to indicate the verdict.
     * </ol>
     * <h2>Results</h2>
     * There are five possible results that this method may return:
     * <ol>
     *     <li><b>{@link SchoolMatchResultType#INSERT INSERT}:</b> The database does not contain any matches
     *     whatsoever. Add this school using a standard SQL <code>INSERT</code> statement.
     *     <li><b>{@link SchoolMatchResultType#OMIT OMIT}:</b> There is a match in the database, and this
     *     {@link School} should be ignored. Do not add this school to the database, and leave the existing
     *     school as-is.
     *     <li><b>{@link SchoolMatchResultType#APPEND APPEND}:</b> There is a match in the database. Update any fields
     *     from the existing school that are <code>null</code> but have a value in this school. This suggests that new
     *     information was found on an existing school.
     *     <li><b>{@link SchoolMatchResultType#UPDATE UPDATE}:</b> There is a match in the database. Update all
     *     fields in the existing school <i>except</i> for {@link Attribute#is_excluded is_excluded},
     *     {@link Attribute#excluded_reason excluded_reason}, and any fields that are <code>null</code> for this
     *     school. This result means that the existing information in the database is outdated, but existing
     *     information should be preserved if it would otherwise become <code>null</code> or manual input would
     *     be overwritten.
     *     <li><b>{@link SchoolMatchResultType#REPLACE REPLACE}:</b> There is a match in the database. Every column
     *     (except for <code>id</code>, obviously) should be replaced with the value from this school, regardless of
     *     whether either are <code>null</code>.
     * </ol>
     * <p>
     * Every {@link SchoolMatchResultType} that necessitates accessing an existing school in the database will
     * include the matching {@link SchoolMatchResult#school school} in the result.
     *
     * @return The result of the match test.
     */
    public SchoolMatchResult determineMatch() {
        logger.debug("Determining match for {}.", name());

        // Get a list of all the matchAttributes that aren't effectively null for this school. We'll use these ones
        // to make comparisons.
        List<Attribute> nonNullMatchAttributes = new ArrayList<>();
        for (Attribute a : matchAttributes)
            if (!isEffectivelyNull(a))
                nonNullMatchAttributes.add(a);

        // Assemble a sql statement for returning any schools that match any of the key attributes to consider
        StringBuilder sql = new StringBuilder("SELECT * FROM Schools WHERE ");

        // Add each attribute
        for (Attribute a : nonNullMatchAttributes)
            sql.append(a.name()).append(" = ? OR ");
        sql.delete(sql.length() - 4, sql.length());

        Map<School, List<Attribute>> schools = new HashMap<>();

        // Establish a connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement(sql.toString());

            // Add the current values of the attributes to the statement
            for (int i = 0; i < nonNullMatchAttributes.size(); i++) {
                Attribute a = nonNullMatchAttributes.get(i);
                a.addToStatement(stmt, get(a), i + 1);
            }

            // Execute the statement
            ResultSet resultSet = stmt.executeQuery();

            // Convert the resultSet to a list of schools
            while (resultSet.next())
                schools.put(new School(resultSet), new ArrayList<>());

        } catch (SQLException e) {
            logger.warn("Encountered SQL error while determining matches for school " + name() + ".", e);
            return SchoolMatchResultType.OMIT.of();
        }

        // If there are no possibly matching schools, exit
        if (schools.size() == 0)
            return SchoolMatchResultType.INSERT.of();

        // For each possible match...
        for (School s : schools.keySet())
            // ...determine which of the nonNullMatchAttributes are equal to this school
            for (Attribute a : nonNullMatchAttributes)
                if (Objects.equals(get(a), s.get(a)))
                    schools.get(s).add(a);

        // Now sort the list of schools by the number of matching attributes (highest to lowest). We'll only prompt the
        // user about the first one.
        Map<School, List<Attribute>> sortedSchools = new LinkedHashMap<>();
        schools.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getValue().size()))
                .forEach(e -> sortedSchools.put(e.getKey(), e.getValue()));


        // Process each school
        for (School school : sortedSchools.keySet()) {
            List<Attribute> matching = sortedSchools.get(school);
            logger.debug("Found {} matching attributes for existing school {} against new school {}.",
                    matching.size(), school.name(), this.name());

            // If there aren't actually any matches (they were all null), skip this school
            if (matching.size() == 0)
                continue;

            // If every single attribute matched, this is obviously the same school. Just return APPEND to update in
            // case there's anything new.
            if (matching.size() == nonNullMatchAttributes.size())
                return SchoolMatchResultType.APPEND.of(school);

            // If only the name attribute matched, skip this school
            if (matching.size() == 1 && matching.contains(Attribute.name))
                continue;

            // If only the url attribute matched, do whatever the config says
            if (matching.size() == 1 && matching.contains(Attribute.website_url)) {
                String mode = Config.SCHOOL_MATCH_URL_MODE.get();
                if (!mode.equals("IGNORE"))
                    return SchoolMatchResultType.fromName(mode).of(school);
            }

            // Now ask the user what to do. Start by assembling the prompt message
            StringBuilder prompt = new StringBuilder("Possible match identified.\nSQL Record attributes:\n");

            prompt.append(String.format("%" + Attribute.MAX_NAME_LENGTH + "s: %d%n", "id", school.id));
            prompt.append(String.format("%" + Attribute.MAX_NAME_LENGTH + "s: %d%n", "organization",
                    school.organization.get_id()));
            for (Attribute a : matchAttributes)
                prompt.append(String.format("%" + Attribute.MAX_NAME_LENGTH + "s: %s%n",
                        a.name(), school.get(a))
                );

            prompt.append("Incoming School attributes:\n");
            prompt.append(String.format("%" + Attribute.MAX_NAME_LENGTH + "s: %d%n", "organization",
                    this.organization.get_id()));
            for (Attribute a : matchAttributes)
                prompt.append(String.format("%" + Attribute.MAX_NAME_LENGTH + "s: %s%n",
                        a.name(), get(a))
                );

            prompt.append("\nWhat do you want to do?");

            // Prompt the user
            int choice = Prompt.run(prompt.toString(), SchoolMatchResultType.SELECTIONS);

            // If the user chose to insert this as a new school, first ask them about any other matches
            if (choice == SchoolMatchResultType.INSERT.index)
                continue;

            // For any other choice, exit now with that result
            if (choice == SchoolMatchResultType.OMIT.index)
                return SchoolMatchResultType.OMIT.of();
            else if (choice == SchoolMatchResultType.APPEND.index)
                return SchoolMatchResultType.APPEND.of(school);
            else if (choice == SchoolMatchResultType.UPDATE.index)
                return SchoolMatchResultType.UPDATE.of(school);
            else if (choice == SchoolMatchResultType.REPLACE.index)
                return SchoolMatchResultType.REPLACE.of(school);
        }

        // If this point is reached, there is no match. Just insert this as a new school.
        return SchoolMatchResultType.INSERT.of();
    }


    /**
     * Save this {@link School} to the SQL database.
     *
     * @throws SQLException If there is an error saving to the database.
     */
    public void saveToDatabase() throws SQLException, IllegalArgumentException {
        // ---------- ---------- ---------- ---------- ----------
        // Check for a matching school and such
        // ---------- ---------- ---------- ---------- ----------


        logger.info("Saving school {} to database.", name());

        // Determine if there's already a school with the same name and URL
        SchoolMatchResult matchResult = determineMatch();
        logger.debug("Match result: {}", matchResult.toString());
        School school = matchResult.school;

        // If the school is null for APPEND, UPDATE, or REPLACE, something went wrong
        if (matchResult.type == SchoolMatchResultType.APPEND)
            if (school == null)
                throw new IllegalArgumentException(
                        "Unreachable state. Could not identify the school to " + matchResult.type.name() + "."
                );


        // ---------- ---------- ---------- ---------- ----------
        // Generate a SQL statement
        // ---------- ---------- ---------- ---------- ----------


        // If the matcher said to omit this school, don't add it to the database
        if (matchResult.type == SchoolMatchResultType.OMIT) {
            logger.debug("Omitting school {}.", name());
            return;
        }

        // Construct the SQL statement to execute
        String sql;
        // Obtain a list of attributes to put in the SQL statement, based on the matchResult.
        List<Attribute> attributes = new ArrayList<>();

        // On INSERT or REPLACE, add all attributes
        if (matchResult.type == SchoolMatchResultType.INSERT || matchResult.type == SchoolMatchResultType.REPLACE)
            attributes.addAll(this.attributes.keySet());

        // On APPEND, add only the attributes that are null for the original school but not for this one
        if (matchResult.type == SchoolMatchResultType.APPEND)
            for (Attribute a : this.attributes.keySet())
                if (school.isEffectivelyNull(a) && !isEffectivelyNull(a))
                    attributes.add(a);

        // On UPDATE, add all attributes except is_excluded, excluded_reason, and anything null for this school
        if (matchResult.type == SchoolMatchResultType.UPDATE)
            for (Attribute a : this.attributes.keySet())
                if (!a.equals(Attribute.is_excluded) && !a.equals(Attribute.excluded_reason) && !isEffectivelyNull(a))
                    attributes.add(a);

        // If there aren't any attributes, clearly there's nothing to do. Exit the method.
        if (attributes.size() == 0) return;

        // Generate SQL string
        if (matchResult.type == SchoolMatchResultType.INSERT) {
            // On INSERT, generate SQL INSERT statement
            StringBuilder columns = new StringBuilder();
            StringBuilder placeholders = new StringBuilder();
            for (Attribute attribute : attributes) {
                columns.append(attribute.name()).append(", ");
                placeholders.append("?, ");
            }
            columns.delete(columns.length() - 2, columns.length());
            placeholders.delete(placeholders.length() - 2, placeholders.length());
            sql = String.format("INSERT INTO Schools (%s) VALUES (%s)", columns, placeholders);
        } else {
            // For all other results, generate SQL UPDATE statement
            StringBuilder update = new StringBuilder();
            for (Attribute attribute : attributes)
                update.append(attribute.name()).append(" = ?, ");
            update.delete(update.length() - 2, update.length());
            sql = String.format("UPDATE Schools SET %s WHERE id = ?", update);
        }

        // ---------- ---------- ---------- ---------- ----------
        // Execute SQL statement
        // ---------- ---------- ---------- ---------- ----------


        // Open database connection
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            // Add values to the statement according to their type
            for (int i = 0; i < attributes.size(); i++)
                attributes.get(i).addToStatement(statement, get(attributes.get(i)), i + 1);

            // For anything besides an INSERT, add the school ID to the statement
            if (matchResult.type != SchoolMatchResultType.INSERT)
                statement.setInt(attributes.size() + 1, Objects.requireNonNull(school).id);

            // Execute the finished statement
            statement.execute();
        }
    }

    @NotNull
    public Organization get_organization() {
        return organization;
    }

    /**
     * See {@link #determineMatch()}.
     */
    public enum SchoolMatchResultType {
        INSERT("INSERT as new school", 1),
        OMIT("OMIT this new school", 2),
        APPEND("APPEND missing parameters to existing school", 3),
        UPDATE("UPDATE existing school parameters", 4),
        REPLACE("REPLACE existing school", 5);

        /**
         * An array of {@link Selection Selections} corresponding to the {@link SchoolMatchResultType
         * SchoolMatchResultTypes}.
         */
        public static final Selection[] SELECTIONS = Arrays.stream(values())
                .map(t -> Selection.of(t.longName, t.index))
                .toArray(Selection[]::new);

        private final String longName;
        private final int index;

        SchoolMatchResultType(String longName, int index) {
            this.longName = longName;
            this.index = index;
        }

        /**
         * Get a {@link SchoolMatchResultType} by providing its name.
         *
         * @param name The {@link #name() name} of the {@link SchoolMatchResultType Type}.
         *
         * @return The matching type, or {@link #OMIT OMIT} if no match was found.
         */
        public static SchoolMatchResultType fromName(String name) {
            for (SchoolMatchResultType type : values())
                if (type.name().equals(name))
                    return type;
            return OMIT;
        }

        public SchoolMatchResult of(@NotNull School school) {
            return new SchoolMatchResult(this, school);
        }

        public SchoolMatchResult of() {
            return new SchoolMatchResult(this, null);
        }
    }

    public static class SchoolMatchResult {
        /**
         * This is the {@link SchoolMatchResultType Type} of match result.
         */
        private final SchoolMatchResultType type;

        /**
         * This is the {@link School} that this match result is for.
         * <p>
         * This value is <code>null</code> for {@link #type types} {@link SchoolMatchResultType#OMIT OMIT} and {@link
         * SchoolMatchResultType#INSERT INSERT}.
         */
        @Nullable
        private final School school;

        private SchoolMatchResult(@NotNull SchoolMatchResultType type, @Nullable School school) {
            this.type = type;
            if (type != SchoolMatchResultType.OMIT && type != SchoolMatchResultType.INSERT)
                this.school = school;
            else
                this.school = null;
        }

        /**
         * Convert this {@link SchoolMatchResult} to a string in the following format:
         * <p>
         * If there is a {@link #school}: "<type> for <school>".
         * <p>
         * If there is not a school: "<type>".
         *
         * @return A string representation of this match result.
         */
        @Override
        public String toString() {
            if (school == null)
                return type.name();
            else
                return type.name() + " for " + school.name();
        }
    }
}
