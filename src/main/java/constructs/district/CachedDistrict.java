package constructs.district;


import constructs.CachedConstruct;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * A cached district is a subclass of {@link District} designed to represent either districts already in the SQL
 * database or districts which still need to be added. After its initial creation, all changes to the data
 * members of this district are tracked separately.
 * <p>
 * From there, it's easy to check whether this district {@link #didChange() changed} since its initial creation, and a
 * SQL statement can be generated to add or update this district in the database.
 * <p>
 * Note that the {@link #id} is not tracked, as it is necessary for identifying the database district when
 * {@link #toUpdateStatement(Connection) updating} it.
 */
public class CachedDistrict extends District implements CachedConstruct {
    /**
     * Records whether this cached district is new, meaning it isn't stored in the database yet. Cached districts
     * {@link #CachedDistrict(ResultSet) created} from a {@link ResultSet} are marked <i>not</i> new
     * (<code>false</code>), and those {@link #CachedDistrict(String, String) created} from scratch are marked new
     * (<code>true</code>).
     *
     * @see #isNew()
     */
    private final boolean isNew;

    /**
     * The new name. This is identical to {@link #name} until {@link #setName(String) changed}.
     */
    @Nullable
    private String newName;

    /**
     * The new website_url. This is identical to {@link #website_url} until {@link #setWebsiteURL(String) changed}.
     */
    @Nullable
    private String newWebsite;

    private CachedDistrict(int id, @Nullable String name, @Nullable String website_url, boolean isNew) {
        super(id, name, website_url);
        this.newName = name;
        this.newWebsite = website_url;
        this.isNew = isNew;
    }

    /**
     * Create a cached district from scratch with the specified parameters. This is considered a {@link #isNew() new}
     * district. The {@link #id} is automatically set to <code>-1</code>.
     *
     * @param name        The {@link #name}.
     * @param website_url The {@link #website_url}.
     */
    public CachedDistrict(@Nullable String name, @Nullable String website_url) {
        this(-1, name, website_url, true);
    }

    /**
     * Create a cached district from a {@link ResultSet}, the result of a query of the <code>Districts</code> table.
     * It's expected that "<code>SELECT *</code>" was used, meaning the <code>resultSet</code> contains every column.
     * <p>
     * This is considered not a {@link #isNew new} district.
     *
     * @param resultSet The results from the query.
     * @throws SQLException If there is any error parsing the <code>resultSet</code>.
     */
    public CachedDistrict(@NotNull ResultSet resultSet) throws SQLException {
        this(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("website_url"),
                false
        );
    }

    @Override
    public @Nullable String getName() {
        return newName;
    }

    public void setName(@Nullable String name) {
        this.newName = name;
    }

    @Override
    public String getWebsiteURL() {
        return newWebsite;
    }

    public void setWebsiteURL(@Nullable String website_url) {
        this.newWebsite = website_url;
    }

    /**
     * Return whether this district has changed from what's saved in the database (i.e., either the {@link #name} or
     * {@link #website_url} have changed). This is always <code>true</code> for {@link #isNew() new}
     * districts.
     * <p>
     * Note that changes to the {@link #id} are not tracked.
     *
     * @return <code>True</code> if and only if this district has changed in any way or is new.
     */
    @Override
    public boolean didChange() {
        return isNew || !Objects.equals(name, newName) || !Objects.equals(website_url, newWebsite);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    /**
     * Check to ensure this {@link #usesInsert() uses} an <code>INSERT</code> statement before calling the
     * {@link District#addToInsertStatement(PreparedStatement) super} implementation.
     *
     * @param statement The statement to modify.
     * @throws SQLException             If there is an error modifying the statement.
     * @throws IllegalArgumentException If this instance should be using an {@link #usesUpdate() update} statement
     *                                  instead.
     */
    @Override
    public void addToInsertStatement(@NotNull PreparedStatement statement)
            throws SQLException, IllegalArgumentException {
        if (!usesInsert())
            throw new IllegalArgumentException("Cannot add district " + this + " to INSERT statement");
        super.addToInsertStatement(statement);
    }

    @Override
    @NotNull
    public PreparedStatement toUpdateStatement(@NotNull Connection connection)
            throws IllegalArgumentException, SQLException {
        if (!usesUpdate())
            throw new IllegalArgumentException("Cannot create UPDATE statement for district " + this + ".");

        // Determine which values need to be updated
        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        if (!Objects.equals(this.name, this.newName))
            map.put("name", newName);
        if (!Objects.equals(this.website_url, this.newWebsite))
            map.put("website_url", newWebsite);

        PreparedStatement statement = connection.prepareStatement(String.format(
                "UPDATE Districts SET %s WHERE id = ?",
                String.join(", ", map.keySet().stream().map(k -> k + " = ?").toList())
        ));

        // Add the values
        ArrayList<String> values = new ArrayList<>(map.values());
        for (int i = 0; i < values.size(); i++)
            statement.setString(i + 1, values.get(i));

        statement.setInt(values.size() + 1, id);
        return statement;

    }

    @Override
    public void saveChanges() {
        name = newName;
        website_url = newWebsite;
    }
}
