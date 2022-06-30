package constructs.schools;

import constructs.organizations.Organization;
import constructs.organizations.OrganizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Database;
import utils.Prompt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class ACCSSchool extends School {
    private static final Logger logger = LoggerFactory.getLogger(ACCSSchool.class);

    /**
     * Create a new {@link ACCSSchool} associated with the {@link OrganizationManager#ACCS ACCS} {@link Organization}.
     */
    public ACCSSchool() {
        super(OrganizationManager.ACCS);
    }

    /**
     * This overrides the {@link School#put(Attribute, Object)} method to modify the notion of a "match" for ACCS
     * schools.
     * <p>
     * A school is considered an <i>exact match</i> with this one if and only if <b>all</b> these conditions are met:
     * <ol>
     *     <li>The school's {@link Attribute#name name} is the same.
     *     <li>The school's {@link Attribute#website_url website_url} is the same.
     *     <li>The school's {@link Attribute#accs_page_url accs_page_url} is the same.
     * </ol>
     * <p>
     * If all three of those conditions are met, this method returns the <code>id</code> of the matching school in
     * the SQL database.
     * <p>
     * If a school is not an exact match and either of these conditions are met, the school is considered a
     * <i>partial match</i>.
     * <ol>
     *     <li>The school's {@link Attribute#website_url website_url} is the same (but not
     *     <code>null</code>).
     *     <li>The school's {@link Attribute#accs_page_url accs_page_url} is the same.
     * </ol>
     * <p>
     * If a school is a partial match, the user is {@link Prompt asked} whether this school is a match. If they answer
     * yes, the <code>id</code> of the matching school is returned. If they answer no, -1 is returned to indicate
     * no match. The user can also choose to ignore this school, meaning it won't be added to the database in any way.
     * This choice returns -2.
     *
     * @return The <code>id</code> of the matching school in the SQL database, or -1 to indicate no match, or -2 to
     *         ignore this school entirely.
     */
    @Override
    public int findMatchingSchool() {
        String myName = this.name();
        String myUrl = (String) get(constructs.schools.Attribute.website_url);
        String myAccsUrl = (String) get(constructs.schools.Attribute.accs_page_url);

        logger.debug("Testing for matching school with " + name() + " at " + myUrl + " (accs " + myAccsUrl + ").");

        // Open a connection and search for a matching school
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, name, website_url, accs_page_url FROM Schools " +
                    "WHERE name = ? OR website_url = ? OR accs_page_url = ?"
            );

            statement.setString(1, myName);
            statement.setString(2, myUrl);
            statement.setString(3, myAccsUrl);
            ResultSet results = statement.executeQuery();

            // Look for a match
            if (results.next()) {
                int id = results.getInt("id");
                String name = results.getString("name");
                String url = results.getString("website_url");
                String accsUrl = results.getString("accs_page_url");

                // Test if all three match
                if (myName.equals(name) && Objects.equals(myUrl, url) && Objects.equals(myAccsUrl, accsUrl))
                    return id;

                // Determine whether either attribute matches
                boolean urlMatch = myUrl != null && myUrl.equals(url);
                boolean accsUrlMatch = Objects.equals(myAccsUrl, accsUrl);

                // If neither match, return -1
                if (!urlMatch && !accsUrlMatch)
                    return -1;

                // Now prompt the user and ask whether there's a match
                String message = "Partial match flagged for manual review. Are these schools the same?\n" +
                                 "Existing school in database:\n" +
                                 "\tName: " + name + "\n" +
                                 "\tWebsite URL: " + url + "\n" +
                                 "\tACCS Page URL: " + accsUrl + "\n" +
                                 "New school:\n" +
                                 "\tName: " + myName + "\n" +
                                 "\tWebsite URL: " + myUrl + "\n" +
                                 "\tACCS Page URL: " + myAccsUrl;

                int choice = Prompt.run(message,
                        Prompt.Selection.of("Yes - Update database", 1),
                        Prompt.Selection.of("No - Insert new school", 0),
                        Prompt.Selection.of("No - Ignore this school", -2)
                );

                if (choice == 1) return id;
                if (choice == -2) return -2;
            }
        } catch (SQLException e) {
            logger.error("Failed to access SQL database to check for matching school: " + myName + ", " + myAccsUrl, e);
        }

        return -1;
    }
}
