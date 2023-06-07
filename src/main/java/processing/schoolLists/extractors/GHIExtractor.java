package processing.schoolLists.extractors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import constructs.school.Attribute;
import constructs.school.CreatedSchool;
import constructs.school.SchoolManager;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.schoolLists.extractors.helpers.ExtUtils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GHIExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(GHIExtractor.class);

    /**
     * Extract schools from the {@link constructs.OrganizationManager#GHI Great Hearts Institute} website.
     *
     * @param document The HTML document from which to extract the list.
     *
     * @return An array of created schools.
     */
    @Override
    @NotNull
    public List<CreatedSchool> extract(@NotNull Document document) {
        List<CreatedSchool> list = new ArrayList<>();
        logHeader();

        JsonArray jsonSchools;
        JsonArray jsonSchoolMap;
        try {
            // Extract the text of the document and parse to a JSON object using gson.
            String rawText = document.text();
            rawText = rawText.substring(6); // Remove the "per = " thing at the start
            JsonReader reader = new JsonReader(new StringReader(rawText));
            reader.setLenient(true); // Allow malformed JSON
            JsonObject rootObj = JsonParser.parseReader(reader).getAsJsonObject();

            // This array contains the main information on each school.
            jsonSchools = rootObj.getAsJsonArray("dataRS");
            // This includes the latitude and longitude of each school.
            jsonSchoolMap = rootObj.getAsJsonArray("mapRS");
        } catch (IndexOutOfBoundsException | JsonParseException | IllegalStateException e) {
            logger.error("Failed to extract GHI schools.", e);
            return list;
        }

        // Iterate through each school, extracting the information from the JSON structures.
        for (int i = 0; i < jsonSchools.size(); i++) {
            try {
                JsonArray schoolArr = jsonSchools.get(i).getAsJsonArray();
                JsonObject schoolObj = jsonSchoolMap.get(i).getAsJsonObject();

                // Extract values from the school's array
                String name = ExtUtils.extractJson(schoolArr, 0);
                String address = ExtUtils.extractJson(schoolArr, 1);
                String servingGrades = ExtUtils.extractJson(schoolArr, 2);
                String website = ExtUtils.extractJson(schoolArr, 3);

                // Extract values from the school's object
                String name2 = ExtUtils.extractJson(schoolObj, "t");
                String address2 = ExtUtils.extractJson(schoolObj, "a");
                String servingGrades2 = ExtUtils.extractJson(schoolObj, "g");
                String mailingAddress = ExtUtils.extractJson(schoolObj, "u");
                double latitude = schoolObj.get("lt").getAsDouble();
                double longitude = schoolObj.get("ln").getAsDouble();
                String latLongAccuracy = ExtUtils.extractJson(schoolObj, "accuracy");

                // Make sure these values correspond. If they don't, log a warning and skip this school
                if (!Objects.equals(name, name2)) {
                    logger.warn("GHI school name mismatch: '{}' != '{}'. Skipping school.", name, name2);
                    continue;
                } else if (!Objects.equals(address, address2)) {
                    logger.warn("GHI school address mismatch: '{}' != '{}'. Skipping school.", address, address2);
                    continue;
                } else if (!Objects.equals(servingGrades, servingGrades2)) {
                    logger.warn("GHI school serving grades mismatch: '{}' != '{}'. Skipping school.",
                            servingGrades, servingGrades2);
                    continue;
                }

                // If the previous school has the same website and address, it is almost certainly the same school
                // with a different name. Change the name of the previous school to this one, and then continue to
                // avoid adding duplicates.
                // There are many Great Hearts schools with the name "Archway Classical Academy - <City>" followed
                // immediately in the list by "<City> Preparatory Academy". I intend to keep the latter name.
                if (list.size() > 0) {
                    CreatedSchool prevSchool = list.get(list.size() - 1);
                    if (Objects.equals(prevSchool.get(Attribute.website_url), website) &&
                        Objects.equals(prevSchool.get(Attribute.address), address)) {
                        prevSchool.put(Attribute.name, name);
                        continue;
                    }
                }

                // Create a school instance from this information
                CreatedSchool school = SchoolManager.newGHI();
                school.put(Attribute.name, ExtUtils.validateName(name));
                school.put(Attribute.address, address);
                school.put(Attribute.grades_offered, servingGrades);
                school.put(Attribute.website_url, ExtUtils.aliasNullLink(website));
                school.put(Attribute.mailing_address, mailingAddress);
                school.put(Attribute.latitude, latitude);
                school.put(Attribute.longitude, longitude);
                school.put(Attribute.lat_long_accuracy, latLongAccuracy);

                logger.debug("Added GHI school: " + school.name());
                list.add(school);
            } catch (IndexOutOfBoundsException | IllegalStateException | ClassCastException | NullPointerException e) {
                logger.debug("Failed to parse GHI school at index " + i + ".", e);
            }
        }

        logger.info("Extracted " + list.size() + " GHI schools.");
        return list;
    }
}
