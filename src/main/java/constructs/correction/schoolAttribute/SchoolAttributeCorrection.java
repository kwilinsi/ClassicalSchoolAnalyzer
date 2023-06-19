package constructs.correction.schoolAttribute;

import constructs.correction.CorrectionType;
import constructs.correction.Correction;
import constructs.school.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * This is a type of Correction intended for correcting the attributes of schools. When a school attempts to
 * {@link constructs.school.School#put(Attribute, Object) put()} some {@link #initialValue value} for an attribute,
 * one of these corrections can intercept that value to replace it with the {@link #newValue correct} one.
 */
public class SchoolAttributeCorrection extends Correction {
    /**
     * The {@link Attribute} to which this correction applies.
     */
    private final Attribute attribute;

    /**
     * The initial value for the {@link #attribute} that should be replaced. This attribute should not be added to a
     * school. It should be replaced with the {@link #newValue}.
     */
    private final Object initialValue;

    /**
     * The new value for the {@link #attribute}. If the {@link #initialValue} value will be added to a school, this
     * should be used instead.
     */
    private final Object newValue;

    /**
     * Create a new Correction, specifying the necessary parameters.
     *
     * @param attribute    The {@link #attribute}.
     * @param initialValue The {@link #initialValue}.
     * @param newValue     The {@link #newValue}.
     * @param notes        The {@link #notes}.
     */
    public SchoolAttributeCorrection(@NotNull Attribute attribute,
                                     @NotNull Object initialValue,
                                     @Nullable Object newValue,
                                     @Nullable String notes) {
        super(CorrectionType.SCHOOL_ATTRIBUTE, notes);
        this.attribute = attribute;
        this.initialValue = initialValue;
        this.newValue = newValue;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public Object getNewValue() {
        return newValue;
    }

    /**
     * Check whether the given attribute and value pair match this Correction.
     *
     * @param attribute The attribute to check against this {@link #attribute}.
     * @param value     The value to {@link Objects#equals(Object, Object) check} against this {@link #initialValue
     *                  value}.
     * @return <code>True</code> if and only if the attribute and value match.
     */
    public boolean matches(@Nullable Attribute attribute, @Nullable Object value) {
        return this.attribute == attribute && Objects.equals(this.initialValue, value);
    }
}
