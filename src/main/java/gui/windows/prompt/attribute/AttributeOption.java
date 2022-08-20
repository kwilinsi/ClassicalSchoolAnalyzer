package gui.windows.prompt.attribute;

import constructs.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AttributeOption {
    @NotNull
    private final Attribute attribute;

    @Nullable
    private final Object firstValue;

    @Nullable
    private final Object secondValue;

    /**
     * This is a string that contains the {@link #attribute} {@link Attribute#name() name}, along with the
     * {@link #firstValue first} and {@link #secondValue second} value for each school.
     * <p>
     * This is set by {@link #setStringRepresentation(String)} and retrieved by {@link #toString()}. Before this has
     * been set, it simply contains the name of the attribute and the value for each school without any proper spacing.
     */
    private String stringRepresentation;

    private AttributeOption(@NotNull Attribute attribute,
                            @Nullable Object firstValue,
                            @Nullable Object secondValue) {
        this.attribute = attribute;
        this.firstValue = firstValue;
        this.secondValue = secondValue;

        this.stringRepresentation = String.format("%s %s %s", attribute.name(), firstValue, secondValue);
    }

    /**
     * Create a new {@link AttributeOption} to be displayed in an {@link AttributePrompt} window.
     *
     * @param attribute   The {@link Attribute} to display.
     * @param firstValue  The value of the attribute for the first school.
     * @param secondValue The value of the attribute for the second school.
     *
     * @return A new attribute option.
     */
    public static AttributeOption of(@NotNull Attribute attribute,
                                     @Nullable Object firstValue,
                                     @Nullable Object secondValue) {
        return new AttributeOption(attribute, firstValue, secondValue);
    }

    /**
     * Get the name of the {@link #attribute}. This will have an asterisk (*) prefix if the {@link #firstValue first}
     * and {@link #secondValue second} values {@link Attribute#matches(Object, Object) match}.
     *
     * @return The name of the {@link #attribute}.
     */
    @NotNull
    public String getAttrName() {
        return (attribute.matches(firstValue, secondValue) ? "(*) " : "") + attribute.name();
    }

    /**
     * Get the attribute associated with this option.
     *
     * @return The {@link #attribute}.
     */
    @NotNull
    public Attribute getAttribute() {
        return attribute;
    }

    /**
     * Get the value of the attribute for the first school.
     *
     * @return The {@link #firstValue}.
     */
    @Nullable
    public Object getFirstValue() {
        return firstValue;
    }

    /**
     * Get the value of the attribute for the second school.
     *
     * @return The {@link #secondValue}.
     */
    @Nullable
    public Object getSecondValue() {
        return secondValue;
    }

    /**
     * Set the string representation that is returned by {@link #toString()}.
     *
     * @param stringRepresentation The {@link #stringRepresentation}.
     */
    public void setStringRepresentation(String stringRepresentation) {
        this.stringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }
}