package utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Scanner;

/**
 * Prompt the user to select an action from a list of options.
 */
public class Prompt {
    /**
     * This is the initial message shown to the user before the list of actions.
     */
    private final String prompt;

    /**
     * The list of options to show the user.
     */
    private final Option[] options;

    /**
     * Create a new prompt with a list of possible options.
     *
     * @param prompt  The message to show the user before the options.
     * @param options The list of options to show the user.
     */
    public Prompt(String prompt, Option... options) {
        this.prompt = prompt;
        this.options = options;
    }

    /**
     * Create a new prompt and immediately {@link #display()} it.
     *
     * @param prompt  The message to show the user before the options.
     * @param options The list of options to show the user.
     *
     * @return The result of {@link #display()}.
     */
    public static String run(String prompt, Option... options) {
        return new Prompt(prompt, options).display();
    }

    /**
     * Prompt the user with the list of {@link #options}. If the user chooses a {@link Selection Selection}, its {@link
     * Selection#value value} is returned. If the user chooses an {@link Action Action}, it's {@link Action#run() run}.
     *
     * @return The value if a user chose a Selection, or <code>null</code> if the user chose an Action.
     */
    @Nullable
    public String display() {
        System.out.println(prompt);
        for (int i = 0; i < options.length; i++)
            System.out.printf(" [%d] %s%n", i + 1, options[i].name);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n> ");
            String input = scanner.nextLine();
            try {
                int choice = Integer.parseInt(input);

                if (choice <= 0 || choice > options.length)
                    System.out.println("ERR: Invalid selection.");
                else {
                    if (options[choice - 1].isRunnable) {
                        if (((Action) options[choice - 1]).run()) return null;
                    } else {
                        return ((Selection) options[choice - 1]).value;
                    }
                }

            } catch (NumberFormatException e) {
                System.out.println("ERR: Please enter a valid number.");
            }
        }
    }

    public static class Option {
        /**
         * The name associated with this option, shown to the user.
         */
        @NotNull
        private final String name;

        /**
         * Whether this option can be run (i.e. whether it's an {@link Action Action}.)
         */
        private final boolean isRunnable;

        private Option(@NotNull String name, boolean isRunnable) {
            this.name = name;
            this.isRunnable = isRunnable;
        }
    }

    public static class Action extends Option {
        /**
         * The action to perform when this action is selected.
         */
        @NotNull
        private final Runnable runnable;

        /**
         * If this is present, the user will be shown this message as a confirmation before performing the action.
         */
        @Nullable
        private final String confirmationWarning;

        private Action(@NotNull String name, @NotNull Runnable runnable, @Nullable String confirmationPrompt) {
            super(name, true);
            this.runnable = runnable;
            this.confirmationWarning = confirmationPrompt;
        }

        /**
         * Create a new action with a {@link #confirmationWarning}, requiring the user to confirm the action before it
         * is called.
         *
         * @param name               The name to display to the user.
         * @param runnable           The action to perform when this action is selected.
         * @param confirmationPrompt The message to display to the user before performing the action.
         */
        public static Action of(@NotNull String name, @NotNull Runnable runnable, @Nullable String confirmationPrompt) {
            return new Action(name, runnable, confirmationPrompt);
        }

        /**
         * Create a standard action without a {@link #confirmationWarning}. The {@link #runnable} will be called as soon
         * as the user selects this action.
         *
         * @param name     The name to display to the user.
         * @param runnable The action to perform when this action is selected.
         */
        public static Action of(@NotNull String name, @NotNull Runnable runnable) {
            return new Action(name, runnable, null);
        }

        /**
         * Run this action. If it has a {@link #confirmationWarning}, first prompt the user whether they really want to
         * do this.
         *
         * @return True if the action was performed and false if the user cancelled it.
         */
        public boolean run() {
            if (confirmationWarning == null) {
                runnable.run();
                return true;
            }

            System.out.println("Are you sure you wish to continue? " + confirmationWarning);
            System.out.print("Enter [Y]es or [N]o.\n");
            Scanner in = new Scanner(System.in);

            while (true) {
                System.out.print("> ");
                String input = in.nextLine();

                if (input.equalsIgnoreCase("y")) {
                    System.out.println();
                    runnable.run();
                    return true;
                } else if (input.equalsIgnoreCase("n")) {
                    return false;
                } else {
                    System.out.println("ERR: Please enter either 'Y' or 'N'.");
                }
            }
        }
    }

    public static class Selection extends Option {
        @NotNull
        private final String value;

        private Selection(@NotNull String name, @NotNull String value) {
            super(name, false);
            this.value = value;
        }

        public static Selection of(@NotNull String name, @NotNull String value) {
            return new Selection(name, value);
        }
    }
}
