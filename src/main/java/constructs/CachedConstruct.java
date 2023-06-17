package constructs;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * At times, it is efficient to retrieve many objects (i.e. constructs) from the database at a single time. These
 * objects can be stored and re-used locally, avoiding many successive calls to the database and greatly improving
 * overall performance. This is known as a cache.
 * <p>
 * The problem with caching is that the cached objects might need to be updated. If a
 * {@link constructs.school.School School} is copied from the database and some of its attributes are changed, those
 * changes will not be reflected in the database.
 * <p>
 * That's what a cached construct is for. Classes that implement this interface extend the general {@link Construct}
 * implementation. Now, they track whether parameters of that construct change and report those changes via
 * {@link #didChange()}. They also allow the generation of a SQL statement to bac-propagate any changes to the
 * database via {@link #toUpdateStatement(Connection) toPreparedStatement()} or add it to the database for the first
 * time via {@link Construct#addToInsertStatement(PreparedStatement) Construct.addToInsertStatement()}.
 * <p>
 * Additionally, they support the creation of new instances that are not in the database at all. These instances are
 * not cached in the sense of being a local copy of database objects. Rather, they are new objects that will be added
 * to the database at a later time. The {@link #isNew()} method indicates whether an instance is a new object not yet
 * in the database. When this is true, the generated SQL statement will be <code>INSERT</code> rather than
 * <code>UPDATE</code>.
 */
public interface CachedConstruct extends Construct {
    /**
     * Determine whether this object has changed, meaning its changes must be propagated back to the database via
     * {@link Construct#addToInsertStatement(PreparedStatement) Construct.addToInsertStatement()} or
     * {@link #toUpdateStatement(Connection) toPreparedStatement()}. For {@link #isNew() new} instances, this will
     * always return <code>true</code>.
     *
     * @return <code>True</code> if and only if this object has changed any way or is new.
     */
    boolean didChange();

    /**
     * Get whether this instance is new. New instances are not yet saved in the database at all; they can be added
     * via {@link Construct#addToInsertStatement(PreparedStatement) Construct.addToInsertStatement()}.
     *
     * @return <code>True</code> if and only if this is a new instance.
     */
    boolean isNew();

    /**
     * Get whether this instance should be added to the database via an <code>INSERT</code> SQL statement with
     * {@link Construct#addToInsertStatement(PreparedStatement) Construct.addToInsertStatement()}. This is identical
     * to {@link #isNew()}.
     *
     * @return <code>True</code> if and only if this uses an insert statement.
     */
    default boolean usesInsert() {
        return isNew();
    }

    /**
     * Get whether changes to this instance should be propagated to the database via an <code>UPDATE</code> SQL
     * statement with {@link #toUpdateStatement(Connection) toPreparedStatement()}. This is <code>true</code> when
     * {@link #didChange()} is <code>true</code> and {@link #usesInsert()} is <code>false</code>.
     *
     * @return <code>True</code> if and only if this uses an update statement.
     */
    default boolean usesUpdate() {
        return !usesInsert() && didChange();
    }

    /**
     * Create a new {@link PreparedStatement} for the given connection, namely a SQL <code>UPDATE</code> statement to
     * propagate any changes to this construct back to the original database object.
     * <p>
     * If {@link #usesUpdate()} is <code>false</code>, this will throw an error.
     *
     * @param connection The database connection.
     * @return The new statement.
     * @throws IllegalArgumentException If this does not use an update statement.
     * @throws SQLException             If there is a library error while creating the statement.
     */
    @NotNull
    PreparedStatement toUpdateStatement(@NotNull Connection connection) throws IllegalArgumentException, SQLException;

    /**
     * Save any changes that have been tracked on this construct, thus finalizing them. After calling this method,
     * {@link #didChange()} should always be <code>false</code>, as all the changes have been saved.
     * <p>
     * This is typically called after {@link #toUpdateStatement(Connection)}.
     */
    void saveChanges();
}
