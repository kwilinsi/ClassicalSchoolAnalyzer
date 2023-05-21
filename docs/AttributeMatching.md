# Attribute Matching

When adding Schools to the database, it's important to identify whether two schools match. That way, we can avoid
duplicate entries. In order to do that, we need to compare the attributes from different schools.

When comparing two values for an attribute, there are four
possible [MatchLevels](/src/main/java/constructs/school/MatchLevel.java):

- `EXACT` - The values are exactly the same (i.e. they match with `Object#equals()`).
- `INDICATOR` - The values *mean* the same thing, despite not being visually identical. (e.g. for `grades_offered`,
  "K–2" and "K, 1, 2").
- `RELATED` - The values aren't the same, but they're related (e.g. two URLs pointing to different pages on the same
  host).
- `NONE` - The values are entirely unrelated.

The task of checking for a match is done by [Attribute#matches()](/src/main/java/constructs/school/Attribute.java),
which runs through the following procedure:

### 1. Equal Objects

Compare the values with `Objects#equals()`. If that yields `true`, then the match is `EXACT`.

### 2. Both Effectively Null

Both values are checked for being *effectively null*
via [Attribute#isEffectivelyNull()](/src/main/java/constructs/school/Attribute.java). Effectively null values are
anything from `null` to empty strings or the word `"null"`, depending on the particular attribute under
consideration. If both values are effectively null, the match level is `INDICATOR`.

### 3. One Effectively Null

If one value is *effectively null* but not the other, the match level is `NONE`.

### 4. Exact Match

The values are compared via [Attribute#isExactMatch()](/src/main/java/constructs/school/Attribute.java). This is
different from `Object#equals()` in that it compares certain attribute types differently. Attributes of
type `LocalDate` are compared via `LocalDate#isEqual()`, and attributes of type `double` are marked equal if they're
no more than 0.00001 apart. If this passes, the match level is `EXACT`.

### 5. Indicator Match

The values are compared via [Attribute#isIndicatorMatch()](/src/main/java/constructs/school/Attribute.java), which
runs special comparisons based on the attribute's type. URLs are compared
via [URLUtils#equals()](/src/main/java/utils/URLUtils.java). The `grades_offered` attribute is compared
via [GradeLevel#rangesEqual()](/src/main/java/constructs/school/GradeLevel.java). If this passes, the match level
is `INDICATOR`.

### 6. Possible Match

The values are compared via [Attribute#isPossibleMatch()](/src/main/java/constructs/school/Attribute.java), which is
again based on the attribute. The `website_url` attribute is compared
via [URLUtils#hostEquals()](/src/main/java/utils/URLUtils.java), which checks only the host names of the URLs for a
match. If this passes, the match level is `POSSIBLE`.

### 7. No Match

If nothing has matched thus far, the match level is `NONE`.
