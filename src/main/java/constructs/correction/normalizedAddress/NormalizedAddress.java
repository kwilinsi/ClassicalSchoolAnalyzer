package constructs.correction.normalizedAddress;

import constructs.correction.Correction;
import constructs.correction.CorrectionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * This Correction is designed for providing manual normalizations of addresses that would otherwise be un-parseable
 * by the Python address parser. It is read and utilized by the Python parsing script, not by this Java program;
 * thus, it is used exclusively as encoded JSON data. That is why its attributes are <code>private</code> and
 * <code>final</code> and not accessed by any getters.
 *
 * @see processing.schoolLists.matching.AddressParser AddressParser
 */
public class NormalizedAddress extends Correction {
    /**
     * The raw address to normalize.
     */
    @NotNull
    private final String raw;

    /**
     * The first line of the parsed address.
     * <p>
     * Key: <code>"address_line_1"</code>
     */
    @Nullable
    private final String address_line_1;

    /**
     * The second line of the parsed address.
     * <p>
     * Key: <code>"address_line_2"</code>
     */
    @Nullable
    private final String address_line_2;

    /**
     * The city name in the parsed address.
     * <p>
     * Key: <code>"city"</code>
     */
    @Nullable
    private final String city;

    /**
     * The state name in the parsed address.
     * <p>
     * Key: <code>"state"</code>
     */
    @Nullable
    private final String state;

    /**
     * The postal code in the parsed address.
     * <p>
     * Key: <code>"postal_code"</code>
     */
    @Nullable
    private final String postal_code;

    /**
     * Create a new normalized address Correction with the required address parameters.
     *
     * @param raw            The {@link #raw} address.
     * @param address_line_1 The {@link #address_line_1}.
     * @param address_line_2 The {@link #address_line_2}.
     * @param city           The {@link #city}.
     * @param state          The {@link #state}.
     * @param postal_code    The {@link #postal_code}.
     * @param notes          The {@link #setNotes(String) notes}.
     */
    public NormalizedAddress(@NotNull String raw,
                             @Nullable String address_line_1,
                             @Nullable String address_line_2,
                             @Nullable String city,
                             @Nullable String state,
                             @Nullable String postal_code,
                             @Nullable String notes) {
        super(CorrectionType.NORMALIZED_ADDRESS, notes);
        this.raw = raw;
        this.address_line_1 = address_line_1;
        this.address_line_2 = address_line_2;
        this.city = city;
        this.state = state;
        this.postal_code = postal_code;
    }

    /**
     * Check whether the given object is equal to this normalized address. Two {@link NormalizedAddress} instances
     * are equal if and only if they have the same {@link #raw} address, {@link #address_line_1},
     * {@link #address_line_2},
     * {@link #city}, {@link #state}, and {@link #postal_code}.
     * <p>
     * This does not take into consideration the {@link #id}, {@link #setNotes(String) notes}, or
     * deserialization data.
     *
     * @param obj The object to compare to this one.
     * @return <code>True</code> if and only if it is equal to this object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NormalizedAddress a)
            return Objects.equals(raw, a.raw) &&
                    Objects.equals(address_line_1, a.address_line_1) &&
                    Objects.equals(address_line_2, a.address_line_2) &&
                    Objects.equals(city, a.city) &&
                    Objects.equals(state, a.state) &&
                    Objects.equals(postal_code, a.postal_code);
        else
            return false;
    }
}
