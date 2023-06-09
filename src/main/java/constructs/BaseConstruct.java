package constructs;

import constructs.organization.Organization;
import constructs.school.School;
import utils.Config;

import java.nio.file.Path;

/**
 * This is the parent class for all objects that represent entries in the SQL database, such as {@link Organization
 * Organizations} and {@link School Schools}.
 */
public class BaseConstruct {
    /**
     * Take the given file name (or often directory) and append it to the root {@link Config#DATA_DIRECTORY
     * data_directory} defined in the program configuration.
     *
     * @param file The name of the file or directory.
     *
     * @return The full path to the file or directory.
     * @throws NullPointerException If the data directory cannot be retrieved from the program configuration.
     */
    public Path getFilePath(String file) throws NullPointerException {
        Path base = Path.of(Config.DATA_DIRECTORY.get());
        return base.resolve(getClass().getSimpleName()).resolve(file);
    }
}
