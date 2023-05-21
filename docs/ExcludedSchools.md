### Excluded Schools

Schools have an attribute called `is_excluded`, which indicates whether they should be omitted from general analysis.
Schools may be automatically excluded
by [CreatedSchool#checkExclude()](/src/main/java/constructs/school/CreatedSchool.java) for one of two reasons:

1. They don't have a `name`
2. They don't have a `website_url`

In either of these cases, the `excluded_reason` attribute is set appropriately.

Schools may also be manually excluded. This could be for reasons that are hard to detect automatically, such as the
school's website being in Spanish. All manually excluded schools are listed here:

- *<N/A>*
