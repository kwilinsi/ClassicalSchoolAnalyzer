package constructs;

import constructs.organization.Organization;
import constructs.school.School;
import org.jetbrains.annotations.NotNull;
import utils.Config;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * This is the top level interface for all objects that represent entries in the SQL database, such as
 * {@link Organization Organizations} and {@link School Schools}.
 */
public interface Construct {
    int getId();

    void setId(int id);

    /**
     * Take the given file name (or often directory) and append it to the root {@link Config#DATA_DIRECTORY
     * data_directory} defined in the program configuration.
     *
     * @param file The name of the file or directory.
     * @return The full path to the file or directory.
     * @throws NullPointerException If the data directory cannot be retrieved from the program configuration.
     */
    @NotNull
    default Path getFilePath(@NotNull String file) throws NullPointerException {
        Path base = Path.of(Config.DATA_DIRECTORY.get());
        return base.resolve(getClass().getSimpleName()).resolve(file);
    }

    /**
     * Add all the values of this construct to a standard <code>INSERT</code> SQL statement. Finish with
     * {@link PreparedStatement#addBatch() addBatch()}.
     *
     * @param statement The statement to which to add this construct.
     * @throws SQLException If there is an error modifying the statement.
     */
    void addToInsertStatement(@NotNull PreparedStatement statement) throws SQLException;

    /**
     * Force {@link Construct} implementations to provide user-friendly strings.
     *
     * @return The string representation of this construct.
     */
    @Override
    @NotNull
    String toString();
}
