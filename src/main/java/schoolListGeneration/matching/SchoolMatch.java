package schoolListGeneration.matching;

import constructs.Attribute;
import constructs.CreatedSchool;
import constructs.School;
import org.jetbrains.annotations.NotNull;
import utils.URLUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a record of an attempted match between some incoming school and an existing school in the database. It is
 * created and handled exclusively by {@link MatchIdentifier#determineMatch(CreatedSchool, List)}.
 */
public class SchoolMatch {
    /**
     * This is the {@link School} unique to this {@link SchoolMatch} instance. During a single run of
     * {@link MatchIdentifier#determineMatch(CreatedSchool, List)}, multiple instances of this class may be created,
     * each with their own value for this school field.
     */
    private final School school;

    /**
     * This is a list of every {@link Attribute} that is found to {@link Attribute#matches(School, School) match}
     * between this {@link #school} and some <code>incomingSchool</code>. It includes attributes that are null for both
     * schools.
     */
    private final List<Attribute> matchingAttributes = new ArrayList<>();

    /**
     * This is the number of attributes that {@link Attribute#matches(School, School) match} between this
     * {@link #school} and some <code>incomingSchool</code> and are not
     * {@link School#isEffectivelyNull(Attribute) null}. This is always less than or equal to the size of
     * {@link #matchingAttributes}, always starting at 0 until matches are identified.
     */
    private int nonNullMatchCount = 0;

    /**
     * This becomes <code>true</code> if this {@link #school} is determined to match the <code>incomingSchool</code>. In
     * other words, {@link #matchingAttributes} is just a list of every attribute.
     */
    private boolean isExactMatch = false;

    /**
     * This becomes <code>true</code> if at least one of the
     * {@link constructs.Organization#getMatchIndicatorAttributes() matchIndicatorAttributes} are found to
     * {@link Attribute#matches(School, School) match} (and not be null) between this {@link #school} and the
     * <code>incomingSchool</code>.
     */
    private boolean isPartialMatch = false;

    /**
     * Create a new {@link SchoolMatch} attached to a particular {@link School}.
     *
     * @param school The {@link #school} considered by this match.
     */
    private SchoolMatch(School school) {
        this.school = school;
    }

    /**
     * Return the {@link School} around which this {@link SchoolMatch} is constructed.
     *
     * @return The {@link #school}.
     */
    public School getSchool() {
        return school;
    }

    /**
     * Return the matching attributes between this {@link #school} and some <code>incomingSchool</code>.
     *
     * @return The {@link #matchingAttributes}.
     */
    public List<Attribute> getMatchingAttributes() {
        return matchingAttributes;
    }

    /**
     * Return whether this {@link #school} is an exact match with some <code>incomingSchool</code>.
     *
     * @return {@link #isExactMatch}
     */
    public boolean isExactMatch() {
        return isExactMatch;
    }

    /**
     * Return whether this {@link #school} is a partial match with some <code>incomingSchool</code>.
     *
     * @return {@link #isPartialMatch}
     */
    public boolean isPartialMatch() {
        return isPartialMatch;
    }

    /**
     * Return the number of non-null {@link #matchingAttributes} between this {@link #school} and some
     * <code>incomingSchool</code>.
     *
     * @return {@link #nonNullMatchCount}
     */
    public int getNonNullMatchCount() {
        return nonNullMatchCount;
    }

    /**
     * Create a new {@link SchoolMatch} with the given {@link School school} being matched against some
     * <code>incomingSchool</code>. Determine any {@link #matchingAttributes} between the schools, and record whether
     * it's an {@link #isExactMatch exact} or {@link #isPartialMatch partial} match (or neither).
     *
     * @param school         This is the school considered exclusively in this {@link SchoolMatch} instance. There will
     *                       be many SchoolMatch instances created, each with a different {@link #school}.
     * @param incomingSchool This is the incoming school that is being matched against the {@link #school}. It will be
     *                       the same for all newly created SchoolMatch instances.
     */
    @NotNull
    public static SchoolMatch create(@NotNull School school, @NotNull CreatedSchool incomingSchool) {
        SchoolMatch match = new SchoolMatch(school);

        // Get a list of every matching attribute
        for (Attribute a : Attribute.values()) {
            boolean isMatch;

            // Check whether the values match for this attribute. Use a.matches(), except for the website_url attribute
            if (a == Attribute.website_url)
                isMatch = URLUtils.domainEquals(school.getStr(a), incomingSchool.getStr(a));
            else
                isMatch = a.matches(incomingSchool, school);

            // If the values match, add the attribute to the list of matching attributes. If it's non-null, increment
            // the appropriate counter.
            if (isMatch) {
                match.matchingAttributes.add(a);
                if (!incomingSchool.isEffectivelyNull(a))
                    match.nonNullMatchCount++;
            }
        }

        // Determine whether this school is an exact match
        if (match.matchingAttributes.size() == Attribute.values().length) {
            match.isExactMatch = true;
            // Return here to skip unnecessary work looking for partial matches or districts
            return match;
        }

        // Determine whether this school is a partial match
        for (Attribute a : incomingSchool.getOrganization().getMatchIndicatorAttributes())
            if (match.matchingAttributes.contains(a) && !incomingSchool.isEffectivelyNull(a)) {
                match.isPartialMatch = true;
                break;
            }

        return match;
    }


}
