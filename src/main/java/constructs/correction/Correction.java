package constructs.correction;

import org.jetbrains.annotations.Nullable;

/**
 * A Correction represents some manually created rule that affects the behavior of the program with respect to the
 * SQL database. It's a means of permanently accounting for some anomaly that is not handled by other automated means
 * (e.g. parsing or normalization scripts).
 * <p>
 * For example, if some school listed their country as <code>"caanda"</code> when it should clearly be
 * <code>"canada"</code>, it's probably not worth rewriting the code base to detect and intercept this misspelling
 * in the normalization process. Instead, a Correction can be added to the database that tells all schools to replace
 * <code>"caanda"</code> with <code>"canada"</code>.
 * <p>
 * Corrections can also be used to simplify the school match detection process by storing particular patterns of
 * schools that belong in the same district but are not automatically detected.
 * <p>
 * Everything done via Corrections could also be done by refactoring the code, but Corrections are intended for cases
 * that only affect one or two individual schools, districts, or other constructs. Additionally, since they're
 * sometimes used for typos, it's reasonable to assume that those typos may be resolved as Organization and School
 * websites correct their information.
 * <p>
 * Corrections are especially useful during the testing process, as this process often involves frequently clearing
 * and recreating the rest of the SQL database, such as when testing the school list aggregation process. Many
 * schools that trigger manual action can be dealt with using Corrections, thereby avoiding wasted time.
 * <hr>
 * <p>
 * Corrections are stored in the SQL database. They consist of a {@link CorrectionManager.Type Type} parameter that
 * specifies what processes within the program they impact, which greatly improves filtering time. Their logic is
 * stored in JSON objects within the databse that contain information partaining to the type of Correction. This
 * structure allows for easily creating new types of Corrections as the program expands, without the need to
 * constantly refactor the Corrections SQL table.
 */
@SuppressWarnings("SpellCheckingInspection")
public abstract class Correction {
    /**
     * These are user-friendly notes associated with the Correction. These are for use in the SQL database to record
     * any helpful information about what the Correction does and/or why it was created.
     * <p>
     * The SQL database imposes a maximum 300-character limit on these notes.
     * <p>
     * This is <code>transient</code> as it is stored separately from the serialized Corretion data in the database.
     */
    @Nullable
    protected transient String notes;

    /**
     * Initialize a new Correction with the given notes.
     *
     * @param notes The {@link #notes}.
     * @throws IllegalArgumentException If the notes are more than 300 characters long, the maximum length allowed by
     *                                  the SQL database.
     */
    protected Correction(@Nullable String notes) throws IllegalArgumentException {
        if (notes != null && notes.length() > 300)
            throw new IllegalArgumentException(
                    "Correction notes length " + notes.length() + " exceeds 300-character limit"
            );

        this.notes = notes;
    }

    /**
     * Set the notes.
     *
     * @param notes The {@link #notes}.
     */
    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }
}
