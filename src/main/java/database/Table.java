package database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;

/**
 * These enums represent each of the tables in the SQL database.
 * <p>
 * Note that the {@link #ordinal() order} of these tables is very important. It reflects the order they are added via
 * the setup script, thus following the natural order of the foreign key dependencies. When these tables should be
 * cleared, this must be done in the reverse order of the list here.
 */
public enum Table {
    Cache("Cache", List.of()),

    Corrections("Corrections", List.of()),

    Organizations("Organizations", List.of()),

    Districts("Districts", List.of()),

    DistrictOrganizations("DistrictOrganizations", List.of(Organizations, Districts)),

    Schools("Schools", List.of(Districts)),

    Pages("Pages", List.of(Schools)),

    Links("Links", List.of(Schools, Pages)),

    PageTexts("PageTexts", List.of(Schools, Pages)),

    PageWords("PageWords", List.of(Schools, Pages));

    /**
     * The name of the actual table as it appears in the SQL database.
     */
    @NotNull
    private final String tableName;

    /**
     * An immutable list of the other tables referenced by the foreign keys of this table. Note that this only goes
     * one step; it doesn't include the dependencies of its dependencies.
     */
    @NotNull
    @Unmodifiable
    private final List<Table> dependencies;

    Table(@NotNull String tableName, @NotNull List<Table> dependencies) {
        this.tableName = tableName;
        this.dependencies = dependencies;
    }

    @NotNull
    public String getTableName() {
        return tableName;
    }

    @NotNull
    @Unmodifiable
    public List<Table> getDependencies() {
        return dependencies;
    }

    /**
     * Get a list of the other {@link Table Tables} that list this one as a {@link #dependencies dependency}. Note
     * that this only goes one step; it doesn't include the dependants of its dependants.
     *
     * @return An immutable list of dependants.
     */
    @NotNull
    @Unmodifiable
    public List<Table> getDependants() {
        return Arrays.stream(Table.values()).filter(t -> t.getDependencies().contains(this)).toList();
    }

    /**
     * Same as {@link #getTableName()}.
     *
     * @return The table name.
     */
    @Override
    public String toString() {
        return getTableName();
    }
}
