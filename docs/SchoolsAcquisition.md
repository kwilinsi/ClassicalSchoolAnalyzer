# School List Acquisition

Acquiring the school list is the first process in analyzing the classical schools. It involves going to
each [organization's](Database.md#organizations) website and downloading the info from their school directory page.

These [schools](Database.md#schools) are grouped into [districts](Database.md#districts). This allows separate
elementary, middle, and high schools to be grouped into a single district.

This page describes in detail the process of creating the school list.

## 1. Scraping Organization Websites

The first step in obtaining the school list is downloading the lists from each organization's
website. [Actions#updateSchoolList()](/src/main/java/main/Actions.java)
calls [Organization#retrieveSchools()](/src/main/java/constructs/Organization.java) on each organization, which returns
a list of [CreatedSchool](/src/main/java/constructs/school/CreatedSchool.java) objects; these will be combined into
Districts
later.

Each Organization has its own Extractor, a class implementing the
custom [Extractor](/src/main/java/processing/schoolLists/extractors/Extractor.java) interface. Each class is
specifically designed to handle the structure of the school list page for its Organization, because every Organization
has a unique way of displaying their member schools.

## 2. Validate Schools

We now have a long list of [CreatedSchools](/src/main/java/constructs/school/CreatedSchool.java). These are objects that
represent newly created School instances before being put in the database (not to be confused with
the [School](/src/main/java/constructs/school/School.java) class).

Before entering the database, each school must be validated
with [CreatedSchool#validate()](/src/main/java/constructs/school/CreatedSchool.java). This means calling three methods:

* [CreatedSchool#checkURLs()](/src/main/java/constructs/school/CreatedSchool.java) - Make sure all URL-based attributes
  conform to the standard format. If they don't, try to fix them.
* [CreatedSchool#checkHasWebsite()](/src/main/java/constructs/school/CreatedSchool.java) - Make sure the school has a
  website URL associated with it, and set the `has_website` attribute accordingly.
* [CreatedSchool#checkExclude()](/src/main/java/constructs/school/CreatedSchool.java) - Determine whether
  the `is_excluded` attribute should be made `true` (it's `false` by default). See [this](ExcludedSchools.md) for more
  info.

## 3. Save to Database

Finally, each school is saved to the database
via [CreatedSchool#saveToDatabase()](/src/main/java/constructs/school/CreatedSchool.java). This consists of a few steps:

### a. Identify matches

Before adding the school (hereafter referred to as the "incoming school") to the database, we must ensure it isn't
already
there. [MatchIdentifier#determineMatch()](/src/main/java/processing/schoolLists/matching/MatchIdentifier.java) checks
for a match, returning a custom [MatchResult](/src/main/java/processing/schoolLists/matching/MatchResult.java).

There are a few possible [MatchResultTypes](/src/main/java/processing/schoolLists/matching/MatchResultType.java):

- `NEW_DISTRICT` - No match identified. Add this school to a brand new district by itself in the database.
- `OMIT` - Don't do anything with this school. It might match something, but it should be ignored.
- `DUPLICATE` - This school is an exact match of another school in the database (or possibly contains less information
  than the existing school). Ignore it, but make sure the [DistrictOrganizations](Database.md#districtorganizations)
  table links the existing School to the Organization this duplicate came from.
- `ADD_TO_DISTRICT` - This school belongs in the same district as an existing school (or schools). Add it as a new
  school in the existing district.
- `APPEND` - This school matches an existing school, but this one has more info. Update any `null` values in the
  database that we have values for now.
- `OVERWRITE` - This school matches an existing school, but some database values conflict. Prompt the user to manually
  resolve conflicts.

To check for a match, the incoming school is compared against every school already in the database one at a
time. The process for comparing with a specific school is as follows:

1. [MatchIdentifier#processIndicatorAttributes()](/src/main/java/processing/schoolLists/matching/MatchIdentifier.java)
   compares the *indicator* attributes between both schools. This is particular set of attributes, unique to each
   Organization, which are very likely to match between duplicate schools. If none of these match, it's extremely
   unlikely
   that the schools are the same. For detailed information on matching attributes, see [this](AttributeMatching.md).
2. If any of the indicator attributes have a [MatchLevel](/src/main/java/constructs/school/MatchLevel.java) of at
   least `RELATED` (without being [effectively null](AttributeMatching.md#2-both-effectively-null)), the school is
   considered a partial match.
3. In this case, further processing is done, in which *every* attribute (not just the indicator ones) are compared
   between
   the two schools. If the incoming school is found to be an exact match
   by [SchoolMatch#isExactMatchOrSubset()](/src/main/java/processing/schoolLists/matching/SchoolMatch.java), the match
   result type `DUPLICATE` is used immediately without any further processing.

After checking in the incoming school against all existing schools, if no partial matches are identified, there is
clearly no match, and `NEW_DISTRICT` is used.

Otherwise, if there are matches, they are processed one at a time, starting with stronger matches (i.e. schools that
match more non-null attributes).

Next, the schools are grouped by district, so that possibly matching schools from the same district can be considered
together. At this point, each matching district is processed one at a time by
[MatchIdentifier#processDistrictMatch()](/src/main/java/processing/schoolLists/matching/MatchIdentifier.java). This step
involves user input, where the user is asked to manually resolve any matches.

