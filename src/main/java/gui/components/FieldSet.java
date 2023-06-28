package gui.components;

import com.googlecode.lanterna.gui2.*;
import gui.components.buttons.SymbolButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A <code>FieldSet</code> is a group of GUI {@link Component Components} that all appear together: a related set of
 * fields. They are intended to be used as rows in a {@link com.googlecode.lanterna.gui2.GridLayout GridLayout}
 * {@link Panel}, where the user can add and delete rows at will.
 */
@SuppressWarnings("UnusedReturnValue")
public class FieldSet {
    /**
     * The position of this set of fields within the panel to which they're {@link #addTo(Panel, int) attached}.
     */
    private int index;

    /**
     * These are each of the components associated with this set of fields. They can be
     * collectively {@link #addTo(Panel, int) added} to a panel.
     */
    private final List<Component> components = new ArrayList<>();

    /**
     * The button that deletes this field set in the GUI.
     * <p>
     * If this is <code>null</code>, it's not included in this field set.
     */
    @Nullable
    private final Button deleteButton;

    /**
     * The button that moves this field set up in the GUI.
     * <p>
     * If this is <code>null</code>, it's not included in this field set.
     */
    @Nullable
    private final Button upButton;

    /**
     * Initialize a new field set. This automatically initializes the close and up buttons, which appear at the end
     * of the field set.
     *
     * @param index  The {@link #index}.
     * @param moveUp The function to call when the user selects the {@link #upButton up} button. It should accept the
     *               index of this field set and move it up. If this is <code>null</code>, the button is disabled.
     * @param delete The function to call when the user selects the {@link #deleteButton delete} button. It should
     *               accept the index of this field set and delete it. If this is <code>null</code>, the button is
     *               disabled.
     */
    public FieldSet(int index, @Nullable Consumer<Integer> moveUp, @Nullable Consumer<Integer> delete) {
        // Initialize the up and close buttons
        if (moveUp == null) {
            upButton = null;
        } else {
            //noinspection UnnecessaryUnicodeEscape
            upButton = SymbolButton.of('\u25B2', () -> moveUp.accept(getIndex()));
            components.add(upButton);
        }

        if (delete == null) {
            deleteButton = null;
        } else {
            deleteButton = SymbolButton.of('X', () -> delete.accept(getIndex()));
            components.add(deleteButton);
        }

        setIndex(index);
    }

    /**
     * Set the {@link #index}.
     * <p>
     * If it is set to <code>0</code>, indicating that this is the first field set in the list and the
     * {@link #upButton} is present, it is {@link Button#setEnabled(boolean) disabled}. Otherwise, that it is
     * enabled.
     *
     * @param index The new button index.
     */
    @NotNull
    public FieldSet setIndex(int index) {
        this.index = index;
        if (upButton != null)
            upButton.setEnabled(index != 0);
        return this;
    }

    public int getIndex() {
        return index;
    }

    /**
     * Get the delete button, if one exists. If there is no such button, this is <code>null</code>.
     *
     * @return The {@link #deleteButton}.
     * @throws IllegalStateException If there is no delete button (i.e. it's <code>null</code>).
     */
    @NotNull
    public Button getDeleteButton() throws IllegalStateException {
        if (deleteButton == null)
            throw new IllegalStateException("Cannot retrieve the delete button as no button was created.");
        else
            return deleteButton;
    }

    /**
     * Get the up button, if one exists. If there is no such button, this is <code>null</code>.
     *
     * @return The {@link #upButton}.
     * @throws IllegalStateException If there is no up button (i.e. it's <code>null</code>).
     */
    @NotNull
    public Button getUpButton() throws IllegalStateException {
        if (upButton == null)
            throw new IllegalStateException("Cannot retrieve the up button as no button was created.");
        else
            return upButton;
    }

    /**
     * Toggle whether the {@link #upButton up} button is {@link Button#setEnabled(boolean) enabled}.
     *
     * @param enabled Whether to make the button enabled.
     * @return Itself, for chaining.
     * @throws IllegalStateException If there is no up button (i.e. it's <code>null</code>).
     */
    @NotNull
    public FieldSet setUpEnabled(boolean enabled) throws IllegalStateException {
        if (upButton == null)
            throw new IllegalStateException("Cannot retrieve the up button as no button was created.");
        else
            upButton.setEnabled(enabled);
        return this;
    }

    /**
     * Add a new component to this field set.
     *
     * @param component The component.
     * @return Itself, for chaining.
     */
    @NotNull
    public FieldSet add(@NotNull Component component) {
        // Insert this component before the up and delete buttons, if those buttons are present
        components.add(
                components.size() - (upButton == null ? 0 : 1) - (deleteButton == null ? 0 : 1),
                component
        );
        return this;
    }

    /**
     * {@link #add(Component) Add} a {@link ComboBox} component, {@link ComboBox#setSelectedItem(Object)
     * setting} its selected item to the given selection.
     *
     * @param comboBox  The combo box to add.
     * @param selection The item to select by default, or <code>null</code> to clear the selection.
     * @param <T>       The type of the combo box values.
     * @return Itself, for chaining.
     */
    @NotNull
    public <T> FieldSet add(@NotNull ComboBox<T> comboBox, @Nullable T selection) {
        comboBox.setSelectedItem(selection);
        return add(comboBox);
    }

    /**
     * Get a component by its index.
     *
     * @param index The index of the component. This is based on the order it was {@link #add(Component)
     *              added}.
     * @return The component.
     * @throws IndexOutOfBoundsException If the index is invalid.
     */
    @NotNull
    public Component get(int index) throws IndexOutOfBoundsException {
        if (index < 0 || index >= components.size())
            throw new IndexOutOfBoundsException("Index out of bounds for size " + components.size());
        return components.get(index);
    }

    /**
     * {@link #get(int) Get} a component as a {@link ComboBox}.
     *
     * @param index The index of the combo box.
     * @return The combo box.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is not a combo box.
     */
    @NotNull
    public ComboBox<?> getComboBox(int index) throws IndexOutOfBoundsException, IllegalArgumentException {
        Component comp = get(index);
        if (comp instanceof ComboBox<?> c)
            return c;
        else
            throw new IllegalArgumentException("Component " + index + " is a " + comp.getClass() + ", not a ComboBox");
    }

    /**
     * {@link #get(int) Get} a component as a {@link TextBox}.
     *
     * @param index The index of the text box.
     * @return The text box.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is not a text box.
     */
    @NotNull
    public TextBox getTextBox(int index) throws IndexOutOfBoundsException, IllegalArgumentException {
        Component com = get(index);
        if (com instanceof TextBox t)
            return t;
        else
            throw new IllegalArgumentException("Component " + index + " is a " + com.getClass() + ", not a TextBox");
    }

    /**
     * {@link #get(int) Get} a component as a {@link CheckBox}.
     *
     * @param index The index of the checkbox.
     * @return The checkbox.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is not a checkbox.
     */
    @NotNull
    public CheckBox getCheckBox(int index) throws IndexOutOfBoundsException, IllegalArgumentException {
        Component com = get(index);
        if (com instanceof CheckBox c)
            return c;
        else
            throw new IllegalArgumentException("Component " + index + " is a " + com.getClass() + ", not a CheckBox");
    }

    /**
     * {@link #getComboBox(int) Get} the component at the given <code>index</code> as a {@link ComboBox}, and return
     * its {@link ComboBox#getSelectedItem() selected item}.
     *
     * @param index The index of the combo box.
     * @return The selected item.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is not a combo box.
     */
    @Nullable
    public Object getSelection(int index) throws IndexOutOfBoundsException, IllegalArgumentException {
        return getComboBox(index).getSelectedItem();
    }

    /**
     * Wrapper for {@link #getSelection(int)} that calls {@link Objects#requireNonNull(Object)}.
     *
     * @param index The index of the combo box.
     * @return The selected item.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is not a text box.
     * @throws NullPointerException      If the selected item is null.
     */
    @NotNull
    public Object getSelectionNonNull(int index)
            throws IndexOutOfBoundsException, IllegalArgumentException, NullPointerException {
        return Objects.requireNonNull(getSelection(index));
    }

    /**
     * {@link #get(int) Get} the component at the given <code>index</code>. If it's either a {@link TextBox} or a
     * {@link Label}, return its text.
     *
     * @param index The index of the component.
     * @return The text.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is neither a text box nor a label.
     */
    @NotNull
    public String getText(int index) {
        Component com = get(index);
        if (com instanceof TextBox t)
            return t.getText();
        else if (com instanceof Label l)
            return l.getText();
        else
            throw new IllegalArgumentException(
                    "Component " + index + " is a " + com.getClass() + ", not a TextBox or a Label"
            );
    }

    /**
     * {@link #getCheckBox(int) Get} the component at the given <code>index</code> as a {@link CheckBox}, and return
     * whether it is {@link CheckBox#isChecked() checked}.
     *
     * @param index The index of the checkbox.
     * @return <code>True</code> if and only if it is checked.
     * @throws IndexOutOfBoundsException If the index is invalid.
     * @throws IllegalArgumentException  If the component at that index is not a checkbox.
     */
    public boolean isChecked(int index) {
        return getCheckBox(index).isChecked();
    }

    /**
     * Determine whether the component at the given <code>index</code> is empty. This has the following definition
     * for various components:
     * <ul>
     *     <li>{@link ComboBox}: the {@link ComboBox#getSelectedIndex() selected index} is <code>-1</code>.
     *     <li>{@link TextBox}: the {@link TextBox#getText() text} is {@link String#isBlank() blank}.
     *     <li>Other components are not supported. They will always return <code>false</code>.
     * </ul>
     *
     * @param index The index of the component.
     * @return <code>True</code> if and only if the component is empty.
     * @throws IndexOutOfBoundsException If the index is invalid.
     */
    public boolean isEmpty(int index) throws IndexOutOfBoundsException {
        Component comp = get(index);
        if (comp instanceof ComboBox<?> c)
            return c.getSelectedIndex() == -1;
        else if (comp instanceof TextBox t)
            return t.getText().isBlank();
        else
            return false;
    }

    /**
     * Add each of the {@link #components} to a given panel, starting at the specified index.
     *
     * @param panel         The panel.
     * @param startingIndex The index at which to add them.
     * @return Itself, for chaining.
     */
    @NotNull
    public FieldSet addTo(@NotNull Panel panel, int startingIndex) {
        // If the starting index is already Integer.MAX_VALUE (or close to it), back it up to avoid weird overflow
        // issues when we do 'startingIndex + i'
        startingIndex = Math.min(startingIndex, Integer.MAX_VALUE - components.size());

        for (int i = 0; i < components.size(); i++)
            panel.addComponent(startingIndex + i, components.get(i));
        return this;
    }

    /**
     * This is a convenience method for {@link #addTo(Panel, int)} that adds to the end by specifying an index of
     * {@link Integer#MAX_VALUE}, similar to {@link Panel#addComponent(Component) Panel.addComponent()}.
     *
     * @param panel The panel.
     * @return Itself, for chaining.
     */
    @NotNull
    public FieldSet addTo(@NotNull Panel panel) {
        return addTo(panel, Integer.MAX_VALUE);
    }

    /**
     * Remove each of the {@link #components} from the given panel. IF the components have not been
     * {@link #addTo(Panel, int) added} to that panel, this has no effect.
     *
     * @param panel The panel.
     * @return Itself, for chaining.
     */
    @NotNull
    public FieldSet removeFrom(@NotNull Panel panel) {
        for (int i = components.size() - 1; i >= 0; i--)
            panel.removeComponent(components.get(i));
        return this;
    }

    /**
     * Attempt to set focus on one of the {@link #components} in this field set according to the following procedure:
     * <ol>
     *     <li>If <code>preferUpButton</code> is <code>true</code> and the {@link #upButton up} button is not
     *     <code>null</code> and is {@link Button#isEnabled() enabled}, it {@link Button#takeFocus() takes} focus.
     *     <li>If that doesn't work and the {@link #deleteButton delete} button is not <code>null</code> and enabled,
     *     it takes focus.
     *     <li>If that doesn't work, the first available component that is {@link Interactable} and enabled takes focus.
     *     <li>If nothing is available, this throws {@link IllegalStateException}.
     * </ol>
     *
     * @param preferUpButton Whether to prefer focusing the up button before checking the delete button.
     * @return Itself, for chaining.
     */
    @NotNull
    public FieldSet takeFocus(boolean preferUpButton) {
        if (preferUpButton && upButton != null && upButton.isEnabled()) {
            upButton.takeFocus();
            return this;
        } else if (deleteButton != null) {
            deleteButton.takeFocus();
            return this;
        } else {
            for (Component component : components) {
                if (component instanceof Interactable i && i.isEnabled()) {
                    i.takeFocus();
                    return this;
                }
            }
        }

        throw new IllegalStateException("Unable to set focus on " + this);
    }

    /**
     * Move this field set up one in a {@link Panel}. This is intended for use with {@link GridLayout GridLayouts}.
     * The process is as follows:
     * <ol>
     *     <li>Identify the current index of the first {@link #components component} within the given
     *     <code>panel</code>.
     *     <li>{@link #removeFrom(Panel) Remove} every component from the panel.
     *     <li>{@link #addTo(Panel, int) Add} each component back to the panel at the determined index <i>minus</i>
     *     the number of {@link #components}. This will work so long as the previous components are another
     *     {@link FieldSet} with the same number of components, as will be true for a grid layout approach.
     *     <li>Decrease the current {@link #index}.
     *     <li>Set focus on the {@link #upButton up} button, unless it's <code>null</code> or {@link Button#isEnabled()
     *     disabled}, in which case the focus goes to the {@link #deleteButton} (unless that's also
     *     <code>null</code>, in which case it goes to the first intractable).
     * </ol>
     *
     * @param panel The panel to which the components are currently added.
     * @return Itself, for chaining.
     * @throws IllegalStateException If the components are not already in the specified panel or the current
     *                               {@link #index} is <code>0</code>.
     */
    @NotNull
    public FieldSet moveUp(@NotNull Panel panel) throws IllegalStateException {
        if (index == 0)
            throw new IllegalStateException("Cannot move FieldSet up as its index is 0.");

        int currentPanelIndex = panel.getChildrenList().indexOf(components.get(0));
        if (currentPanelIndex == -1)
            throw new IllegalStateException("Cannot move FieldSet up as it is not added to the provided panel.");

        return setIndex(getIndex() - 1)
                .removeFrom(panel)
                .addTo(panel, currentPanelIndex - components.size())
                .takeFocus(true);
    }

    /**
     * Prevent the specified {@link #components component} from having the same value as another {@link FieldSet}.
     * When the user attempts to change the value of that component, the <code>getOthers</code> supplier is
     * called to
     * get a list of the other field sets. If any of them already have the same value for the component at the same
     * index, the user's value is blocked and reverted.
     * <p>
     * This supports the following component types:
     * <ul>
     *     <li>{@link ComboBox}
     *     <li>{@link TextBox}
     * </ul>
     * <p>
     * Note that this only checks changes to components made by the user, not changes made by some function.
     *
     * @param index       The index of the component.
     * @param getOthers   A function to retrieve the other field sets to compare against this one. The list main
     *                    contain this field set; any field set with the same {@link #index} as this one is simply
     *                    skipped.
     * @param onDuplicate An optional function to call if a duplicate is detected. It takes as input the value of
     *                    the duplicate component.
     * @return Itself, for chaining.
     * @throws IndexOutOfBoundsException     If the index is invalid.
     * @throws UnsupportedOperationException If the component at the specified index is an unsupported type.
     */
    @NotNull
    public FieldSet blockDuplicates(int index,
                                    @NotNull Supplier<@NotNull List<@NotNull FieldSet>> getOthers,
                                    @Nullable Consumer<String> onDuplicate)
            throws IndexOutOfBoundsException, UnsupportedOperationException {
        Component component = get(index);

        if (component instanceof ComboBox<?> c) {
            c.addListener((sel, prev, user) -> {
                if (user && sel != -1) {
                    List<FieldSet> fieldSets = getOthers.get();
                    for (FieldSet fs : fieldSets) {
                        if (getIndex() != fs.getIndex() && sel == fs.getComboBox(index).getSelectedIndex()) {
                            if (onDuplicate != null)
                                onDuplicate.accept(c.getText());
                            c.setSelectedIndex(prev);
                        }
                    }
                }
            });
        } else if (component instanceof TextBox t) {
            t.setTextChangeListener((newText, user) -> {
                if (user && !newText.isEmpty()) {
                    List<FieldSet> fieldSets = getOthers.get();
                    for (FieldSet fs : fieldSets) {
                        if (getIndex() != fs.getIndex() && newText.equals(fs.getTextBox(index).getText())) {
                            if (onDuplicate != null)
                                onDuplicate.accept(t.getText());
                            t.setText(newText.substring(0, newText.length() - 1));
                        }
                    }
                }
            });
        }

        return this;
    }

    /**
     * Return a string with some basic info about this field set. This is intended for debugging purposes.
     *
     * @return A string representation of this field set.
     */
    @Override
    public String toString() {
        return String.format("[FieldSet at index %d with %d components]", index, components.size());
    }
}
