package main;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Scanner in = new Scanner(System.in);

        System.out.println("Welcome to Classical School Analyzer");

        while (true) {
            System.out.println();
            System.out.println("Select Action");
            System.out.println("[1] Download School List");
            System.out.println("[2] Download School Websites");
            System.out.println("[3] Perform Analysis");
            System.out.println("[4] Exit");
            System.out.print("\n> ");

            String val = in.next();
            int action;

            try {
                action = Integer.parseInt(val);
                if (action < 1 || action > 4)
                    throw new IndexOutOfBoundsException();
            } catch (NumberFormatException e) {
                System.out.println("ERR: Please enter a valid integer selection.");
                continue;
            } catch (IndexOutOfBoundsException e) {
                System.out.println("ERR: Selection out of bounds (must be 1–4).");
                continue;
            }

            System.out.println();
            switch (action) {
                case 1 -> Actions.downloadSchoolList();
                case 2 -> Actions.downloadSchoolWebsites();
                case 3 -> Actions.performAnalysis();
                case 4 -> {
                    return;
                }
            }
        }
    }
}
