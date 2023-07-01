package constructs.school;

import constructs.ConstructManager;
import constructs.correction.CorrectionManager;
import constructs.correction.schoolCorrection.SchoolCorrection;
import constructs.district.CachedDistrict;
import constructs.district.DistrictManager;
import constructs.districtOrganization.CachedDistrictOrganization;
import constructs.organization.Organization;
import constructs.organization.OrganizationManager;
import gui.windows.schoolMatch.SchoolListProgressWindow;
import gui.windows.schoolMatch.SchoolListProgressWindow.Phase;
import database.Database;
import main.Main;
import org.jetbrains.annotations.NotNull;
import processing.schoolLists.matching.AttributeComparison;
import processing.schoolLists.matching.MatchIdentifier;
import processing.schoolLists.matching.data.DistrictMatch;
import processing.schoolLists.matching.data.MatchData;
import processing.schoolLists.matching.data.SchoolComparison;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This utility class contains methods designed to produce the main list of schools.
 */
public class SchoolManager {
    /**
     * Download the list of schools from the {@link Organization#getSchoolListUrl() school list} pages of one or more
     * Organizations. Use this list to update the Schools table in the database, whether by adding new schools or
     * updating the attributes for existing ones.
     *
     * @param organizations The organizations to reference in getting the list of schools.
     * @param useCache      Whether to use cached versions of the school list pages when available.
     */
    public static void updateSchoolList(Collection<Organization> organizations, boolean useCache) {
        // Initialize the progress bar window
        SchoolListProgressWindow progress = SchoolListProgressWindow.of();
        Main.GUI.addWindow(progress);

        // ------------------------------
        // Retrieve Cached Constructs
        // ------------------------------

        List<CachedSchool> schoolsCache;
        List<CachedDistrict> districtCache;
        // This is a map of organization ids to lists of district-organization relations
        Map<Integer, @NotNull List<@NotNull CachedDistrictOrganization>> districtOrganizationCache =
                OrganizationManager.ORGANIZATIONS.stream()
                        .map(Organization::getId)
                        .collect(Collectors.toMap(id -> id, id -> new ArrayList<>()));

        progress.setGeneralTask("Loading database connection...")
                .setSubTask("Waiting...")
                .resetSubProgressBar(4);

        try (Connection connection = Database.getConnection()) {
            progress.setPhase(Phase.RETRIEVING_SCHOOL_CACHE)
                    .setGeneralTask("Retrieving existing schools...")
                    .setSubTask("Loading database connection...")
                    .incrementSubProgress();
            schoolsCache = ConstructManager.loadCacheNullable(connection, CachedSchool.class, progress);
            if (schoolsCache == null) return;

            progress.setPhase(Phase.RETRIEVING_DISTRICT_CACHE)
                    .setGeneralTask("Retrieving existing districts...")
                    .incrementSubProgress();
            districtCache = ConstructManager.loadCacheNullable(connection, CachedDistrict.class, progress);
            if (districtCache == null) return;

            progress.setPhase(Phase.RETRIEVING_DISTRICT_ORGANIZATION_CACHE)
                    .setGeneralTask("Retrieving existing district-organization relations...")
                    .incrementSubProgress();
            List<CachedDistrictOrganization> cdo = ConstructManager.loadCacheNullable(
                    connection, CachedDistrictOrganization.class, progress);
            if (cdo == null) return;
            for (CachedDistrictOrganization c : cdo)
                districtOrganizationCache.get(c.getOrganizationId()).add(c);

            progress.completeSubProgress();
        } catch (SQLException e) {
            progress.errorOut("Failed to establish database connection", e);
            return;
        }

        // ------------------------------
        // Download Schools from Organizations
        // ------------------------------

        progress.setPhase(Phase.DOWNLOADING_SCHOOLS);
        List<CreatedSchool> schools = new ArrayList<>();

        for (Organization organization : organizations) {
            try {
                progress.resetSubProgressBar(0);
                schools.addAll(organization.retrieveSchools(useCache, progress));
            } catch (IOException e) {
                //noinspection UnnecessaryUnicodeEscape
                if (progress.errorPrompt("Failed to load and process school list for " + organization +
                        " \u2014 omitting it", e))
                    return;
            }
        }

        // ------------------------------
        // Normalize School Attributes and check Corrections
        // ------------------------------

        progress.setPhase(Phase.PRE_PROCESSING_SCHOOLS)
                .setGeneralTask("Normalizing school attributes...")
                .resetSubProgressBar(Attribute.values().length, "Attributes");

        for (Attribute attribute : Attribute.values()) {
            progress.setSubTask("Normalizing " + attribute.name() + "...");

            List<?> normalized = AttributeComparison.normalize(attribute, schools);
            for (int i = 0; i < normalized.size(); i++)
                schools.get(i).put(attribute, normalized.get(i));

            progress.incrementSubProgress();
        }

        progress.setGeneralTask("Checking school corrections")
                .resetSubProgressBar(schools.size(), "Schools");

        List<SchoolCorrection> schoolCorrections = CorrectionManager.getSchoolCorrections();
        for (int i = schools.size() - 1; i >= 0; i--) {
            CreatedSchool school = schools.get(i);
            for (SchoolCorrection correction : schoolCorrections)
                if (correction.matches(school) && !correction.apply(school)) {
                    schools.remove(i);
                    break;
                }

            progress.incrementSubProgress().setSubTask("Checked corrections against " + school);
        }

        // ------------------------------
        // Validate Schools — Identify Matches
        // ------------------------------

        progress.setPhase(Phase.IDENTIFYING_MATCHES)
                .setGeneralTask("Checking for duplicates...")
                .resetSubProgressBar(schools.size(), "Schools");

        for (int i = 0; i < schools.size(); i++) {
            CreatedSchool school = schools.get(i);
            //noinspection UnnecessaryUnicodeEscape
            progress.setSubTask("Checking #" + i + " \u2014 " + school + "...");

            // Compare this school to the cache of database schools, and handle the result accordingly
            MatchData matchData = MatchIdentifier.compare(school, schoolsCache, districtCache);
            switch (matchData.getLevel()) {
                case OMIT -> schools.set(i, null);

                case NO_MATCH -> {
                    // Make a new district, and add this school to it
                    CachedDistrict district = DistrictManager.makeDistrict(school);
                    addDistrictOrganization(districtOrganizationCache, new CachedDistrictOrganization(
                            school.getOrganization(), district
                    ));
                    districtCache.add(district);

                    CachedSchool cachedSchool = new CachedSchool(school);
                    cachedSchool.setDistrict(district);
                    schoolsCache.add(cachedSchool);
                }

                case DISTRICT_MATCH -> {
                    // Add this new school to the district
                    CachedDistrict district = ((DistrictMatch) matchData).getDistrict();
                    addDistrictOrganization(districtOrganizationCache, new CachedDistrictOrganization(
                            school.getOrganization(), district
                    ));

                    CachedSchool cachedSchool = new CachedSchool(school);
                    cachedSchool.setDistrict(district);
                    schoolsCache.add(cachedSchool);
                }

                case SCHOOL_MATCH -> {
                    // Add a district organization relation, and set the new school's district
                    SchoolComparison comparison = Objects.requireNonNull((SchoolComparison) matchData);
                    addDistrictOrganization(districtOrganizationCache, new CachedDistrictOrganization(
                            school.getOrganization(), Objects.requireNonNull(comparison.getDistrict())
                    ));
                    comparison.updateExistingSchoolAttributes();
                }
            }

            progress.incrementSubProgress();
        }

        // ------------------------------
        // Execute SQL Updates
        // ------------------------------

        progress.setPhase(Phase.SAVING_TO_DATABASE)
                .setGeneralTask("Saving to database...")
                .setSubTask("Establishing connection...")
                .resetSubProgressBar(0);

        try (Connection connection = Database.getConnection()) {
            // Save districts and schools
            ConstructManager.saveToDatabase(connection, districtCache, progress);
            ConstructManager.saveToDatabase(connection, schoolsCache, progress);
            ConstructManager.saveToDatabase(
                    connection,
                    districtOrganizationCache.values().stream().flatMap(Collection::stream).toList(),
                    progress
            );
        } catch (SQLException e) {
            progress.errorOut("Failed to save the results to the database.", e);
            return;
        }

        progress.finishAndWait(schools.size());
    }

    /**
     * Add a new {@link CachedDistrictOrganization} relation to the map of existing relations, assuming it's not
     * already in the map. If it's already present, nothing happens.
     *
     * @param relationMap The map of existing relations.
     * @param relation    The relation to add.
     */
    private static void addDistrictOrganization(
            @NotNull Map<@NotNull Integer, List<@NotNull CachedDistrictOrganization>> relationMap,
            @NotNull CachedDistrictOrganization relation) {
        List<@NotNull CachedDistrictOrganization> list = relationMap.get(relation.getOrganizationId());
        for (CachedDistrictOrganization r : list)
            if (relation.equals(r))
                return;

        list.add(relation);
    }
}
