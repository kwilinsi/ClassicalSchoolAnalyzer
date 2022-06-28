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
     * The list of actions to show the user.
     */
    private final Action[] actions;

    /**
     * Create a new prompt with a list of possible actions.
     *
     * @param prompt  The message to show the user before the actions.
     * @param actions The list of actions to show the user.
     */
    public Prompt(String prompt, Action... actions) {
        this.prompt = prompt;
        this.actions = actions;
    }

    /**
     * Create a new prompt and immediately {@link #display()} it.
     *
     * @param prompt  The message to show the user before the actions.
     * @param actions The list of actions to show the user.
     */
    public static void run(String prompt, Action... actions) {
        new Prompt(prompt, actions).display();
    }

    /**
     * Prompt the user with the list of {@link #actions}.
     */
    public void display() {
        System.out.println(prompt);
        for (int i = 0; i < actions.length; i++)
            System.out.printf(" [%d] %s%n", i + 1, actions[i].name);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n> ");
            String input = scanner.nextLine();
            try {
                int choice = Integer.parseInt(input);

                if (choice <= 0 || choice > actions.length)
                    System.out.println("ERR: Invalid selection.");
                else if (actions[choice - 1].run()) return;

            } catch (NumberFormatException e) {
                System.out.println("ERR: Please enter a valid number.");
            }
        }
    }

    public static class Action {
        /**
         * The name associated with this action, shown to the user.
         */
        @NotNull
        private final String name;

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
            this.name = name;
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
}
