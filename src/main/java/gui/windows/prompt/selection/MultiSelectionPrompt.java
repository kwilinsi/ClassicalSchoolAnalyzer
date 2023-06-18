package gui.windows.prompt.selection;

import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import gui.utils.GUIUtils;
import gui.windows.prompt.Prompt;
import main.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.Pair;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Similar to a {@link SelectionPrompt}, a multi-selection prompt allows the user to choose from a list of options.
 * However, the options are listed as checkboxes, allowing the user to select more than one.
 * <p>
 * Note that this prompt has a {@link MessageDialogButton#Cancel Cancel} button which, if selected, will yield
 * <code>null</code>.
 */
public class MultiSelectionPrompt<T> extends Prompt<List<T>> {
    /**
     * The checkboxes the user can choose from.
     */
    @NotNull
    private final CheckBoxList<Option<T>> checkBoxes;

    /**
     * This is an optional validation function that is called when the user {@link #onOk() submits} the prompt. If it
     * fails, the associated message is displayed and the prompt is not submitted.
     * <p>
     * It takes as input an immutable list of currently selected items. It returns a {@link Pair} of a {@link Boolean}
     * (<code>true</code> if the validation passed; <code>false</code> if it failed) and an optional {@link String},
     * which, if present, contains the error message to display.
     *
     * @see #confirmation
     */
    @Nullable
    private final Function<List<T>, Pair<Boolean, String>> validator;

    /**
     * This is an optional function for generating a confirmation message when the user {@link #onOk() submits} the
     * prompt. If it fails, prompt is not submitted.
     * <p>
     * It takes as input an immutable list of currently selected items. It returns the confirmation message to
     * display the user. The message is automatically suffixed with <code>"Are you sure you want to
     * continue?"</code>, and the user is given {@link MessageDialogButton#Yes Yes} and
     * {@link MessageDialogButton#No No} buttons.
     * <p>
     * If this is <code>null</code>, it is not used. If it's present but the produced message is <code>null</code>,
     * this is also not shown.
     * <p>
     * Note that this runs after the {@link #validator}.
     */
    @Nullable
    private final Function<List<T>, String> confirmation;

    /**
     * Create a new multi-selection prompt window.
     *
     * @param windowTitle     The title of the window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of {@link Option Options} to present to the user.
     * @param checkedStates   The current state of the checkbox for each option.
     * @param validator       The {@link #validator}.
     * @param confirmation    The {@link #confirmation}.
     * @throws IllegalArgumentException If the <code>checkStates</code> list is not <code>null</code> and a different
     *                                  length than the <code>options</code> list.
     */
    protected MultiSelectionPrompt(@Nullable String windowTitle,
                                   @Nullable Component promptComponent,
                                   @NotNull List<Option<T>> options,
                                   @Nullable List<Boolean> checkedStates,
                                   @Nullable Function<List<T>, Pair<Boolean, String>> validator,
                                   @Nullable Function<List<T>, String> confirmation)
            throws IllegalArgumentException {
        super(windowTitle, promptComponent, new Panel());

        if (checkedStates != null && checkedStates.size() != options.size())
            throw new IllegalArgumentException("Checked states list must be the same size as the options list");

        this.validator = validator;
        this.confirmation = confirmation;

        checkBoxes = new CheckBoxList<>();
        for (int i = 0; i < options.size(); i++)
            checkBoxes.addItem(options.get(i), checkedStates != null && checkedStates.get(i));

        checkBoxes.addListener((index, checked) -> {
            if (!checked && !checkBoxes.getItemAt(index).isConfirmed())
                checkBoxes.toggleChecked(index);
        });

        this.optionsPanel
                .addComponent(new EmptySpace())
                .addComponent(checkBoxes);

        this.panel
                .addComponent(new EmptySpace())
                .addComponent(new Panel()
                        .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                        .addComponent(new Button("All", this::checkAll))
                        .addComponent(new Button("None", this::checkNone))
                )
                .addComponent(new EmptySpace())
                .addComponent(new Panel()
                        .setLayoutManager(new LinearLayout(Direction.HORIZONTAL))
                        .setLayoutData(GridLayout.createHorizontallyEndAlignedLayoutData(1))
                        .addComponent(new Button(MessageDialogButton.Cancel.toString(), this::onCancel))
                        .addComponent(new Button(MessageDialogButton.OK.toString(), this::onOk))
                );

        setFocusedInteractable(checkBoxes);
    }

    /**
     * Create a new multi-selection prompt window.
     *
     * @param windowTitle     The title of the window.
     * @param promptComponent The {@link Component} that contains the prompt message for the user.
     * @param options         The list of {@link Option Options} to present to the user.
     * @param checkedStates   The current state of the checkbox for each option.
     * @param validator       The {@link #validator}.
     * @param confirmation    The {@link #confirmation}.
     * @param <T>             The type of value.
     * @return The new window.
     */
    public static <T> MultiSelectionPrompt<T> of(@Nullable String windowTitle,
                                                 @Nullable Component promptComponent,
                                                 @NotNull List<Option<T>> options,
                                                 @Nullable List<Boolean> checkedStates,
                                                 @Nullable Function<List<T>, Pair<Boolean, String>> validator,
                                                 @Nullable Function<List<T>, String> confirmation) {
        return new MultiSelectionPrompt<>(
                windowTitle, promptComponent, options, checkedStates, validator, confirmation
        );
    }

    /**
     * Create a new multi-selection prompt window.
     *
     * @param windowTitle   The title of the widow.
     * @param text          The prompt message.
     * @param options       The list of options to show the user.
     * @param checkedStates The current state of the checkbox for each option.
     * @param validator     The {@link #validator}.
     * @param confirmation  The {@link #confirmation}.
     * @param <T>           The type of value.
     * @return The new window.
     */
    public static <T> MultiSelectionPrompt<T> of(@Nullable String windowTitle,
                                                 @NotNull String text,
                                                 @NotNull List<Option<T>> options,
                                                 @Nullable List<Boolean> checkedStates,
                                                 @Nullable Function<List<T>, Pair<Boolean, String>> validator,
                                                 @Nullable Function<List<T>, String> confirmation) {
        return new MultiSelectionPrompt<>(
                windowTitle,
                new Panel().addComponent(new Label(text)).addComponent(new EmptySpace()),
                options,
                checkedStates,
                validator,
                confirmation
        );
    }

    /**
     * Create a new multi-selection prompt window.
     *
     * @param windowTitle  The title of the widow.
     * @param text         The prompt message.
     * @param options      The list of options to show the user. All options are not selected by default.
     * @param validator    The {@link #validator}.
     * @param confirmation The {@link #confirmation}.
     * @param <T>          The type of value.
     * @return The new window.
     */
    public static <T> MultiSelectionPrompt<T> of(@Nullable String windowTitle,
                                                 @NotNull String text,
                                                 @NotNull List<Option<T>> options,
                                                 @Nullable Function<List<T>, Pair<Boolean, String>> validator,
                                                 @Nullable Function<List<T>, String> confirmation) {
        return of(windowTitle, text, options, null, validator, confirmation);
    }

    /**
     * Create a new multi-selection prompt window.
     *
     * @param windowTitle The title of the widow.
     * @param text        The prompt message.
     * @param options     The list of options to show the user. All options are not selected by default.
     * @param <T>         The type of value.
     * @return The new window.
     */
    public static <T> MultiSelectionPrompt<T> of(@Nullable String windowTitle,
                                                 @NotNull String text,
                                                 @NotNull List<Option<T>> options) {
        return of(windowTitle, text, options, null, null, null);
    }

    /**
     * Create a new multi-selection prompt window.
     *
     * @param windowTitle  The title of the widow.
     * @param text         The prompt message.
     * @param options      The list of options to show the user. All options are not selected by default.
     * @param checkedState The checked state to use for all the options.
     * @param <T>          The type of value.
     * @return The new window.
     */
    public static <T> MultiSelectionPrompt<T> of(@Nullable String windowTitle,
                                                 @NotNull String text,
                                                 @NotNull List<Option<T>> options,
                                                 boolean checkedState) {
        return of(windowTitle, text, options,
                Stream.generate(() -> checkedState).limit(options.size()).collect(Collectors.toList()),
                null, null);
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#Cancel Cancel} button.
     */
    private void onCancel() {
        closeAndSet(null);
    }

    /**
     * Called when the user chooses the {@link MessageDialogButton#OK OK} button.
     * <p>
     * This runs the {@link #validator} function, if one is present. Then, it runs the {@link #confirmation}
     * function, if one is present. Finally, if none of the options are selected, it shows a warning confirmation.
     * Finally, it records the selected options and closes the window.
     */
    private void onOk() {
        List<Option<T>> items = checkBoxes.getCheckedItems();

        // If there's a validation function, run it
        if (validator != null) {
            Pair<Boolean, String> result = validator.apply(items.stream().map(Option::getValue).toList());
            if (!result.a) {
                MessageDialog.showMessageDialog(
                        Main.GUI.getWindowGUI(),
                        "Error",
                        GUIUtils.wrapLabelText(result.b == null ? "The validator failed for the current selections. " +
                                "Try a different selection." : result.b),
                        MessageDialogButton.OK
                );
                return;
            }
        }

        // If there's a confirmation message, show it
        if (confirmation != null) {
            String message = confirmation.apply(items.stream().map(Option::getValue).toList());
            if (message != null)
                if (MessageDialogButton.No == MessageDialog.showMessageDialog(
                        Main.GUI.getWindowGUI(),
                        "Confirm Selection",
                        GUIUtils.wrapLabelText(message + " Are you sure you want to continue?"),
                        MessageDialogButton.No,
                        MessageDialogButton.Yes
                ))
                    return;
        }

        // Show a warning if nothing is selected
        if (items.size() == 0) {
            if (MessageDialogButton.No == MessageDialog.showMessageDialog(
                    Main.GUI.getWindowGUI(),
                    "Confirm Selection",
                    GUIUtils.wrapLabelText(
                            "None of the options are currently selected. Are you sure want to continue?"
                    ),
                    MessageDialogButton.No,
                    MessageDialogButton.Yes
            ))
                return;
        }

        closeAndSet(items.stream().map(Option::getValue).toList());
    }

    /**
     * Check all the {@link #checkBoxes boxes}.
     */
    private void checkAll() {
        for (int i = 0; i < checkBoxes.getItemCount(); i++)
            if (!checkBoxes.isChecked(i))
                checkBoxes.toggleChecked(i);
    }

    /**
     * Un-check all the {@link #checkBoxes boxes}.
     */
    private void checkNone() {
        for (int i = 0; i < checkBoxes.getItemCount(); i++)
            if (checkBoxes.isChecked(i))
                checkBoxes.toggleChecked(i);
    }

    /**
     * Handle the following input:
     * <ul>
     *     <li><code>"A"</code> — Check {@link #checkAll() all} the boxes.
     *     <li><code>"N"</code> or <code>"U"</code> — Check {@link #checkNone() none} of the boxes.
     *     <li>A number 1-9 — Toggle that item, if it exists.
     *     <ul>
     *         <li>If {@link KeyStroke#isAltDown() alt} is down, select the specified item and de-select all the
     *         others.
     *     </ul>
     *     <li><code>"C"</code> — The window is {@link #onCancel() cancelled}.
     *     <li><code>"O"</code> — The window is accepted with {@link #onOk() ok}.
     * </ul>
     * Otherwise, if the input is still unhandled after the above checks, defer to the
     * {@link Prompt#handleInput(KeyStroke) super} method.
     *
     * @param key The keyboard input to handle.
     * @return <code>True</code> if and only if the input was handled.
     */
    @Override
    public boolean handleInput(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character)
            switch (key.getCharacter()) {
                case 'a' -> {
                    checkAll();
                    return true;
                }

                case 'n', 'u' -> {
                    checkNone();
                    return true;
                }

                case 'o' -> {
                    onOk();
                    return true;
                }

                case 'c' -> {
                    onCancel();
                    return true;
                }

                case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    int index = Character.getNumericValue(key.getCharacter());
                    if (index <= checkBoxes.getItemCount()) {
                        if (key.isAltDown()) {
                            for (int i = 0; i < checkBoxes.getItemCount(); i++)
                                checkBoxes.setChecked(checkBoxes.getItemAt(i), i + 1 == index);
                        } else {
                            checkBoxes.toggleChecked(index - 1);
                        }
                        return true;
                    }
                }
            }

        return super.handleInput(key);
    }
}
