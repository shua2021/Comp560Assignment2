import java.util.*;
import  java.io.*;
public class Main {
    public static void main(String[]args) throws FileNotFoundException {
        Scanner scan = new Scanner(System.in);
        System.out.print("Please enter input file path (works with .txt file): ");

        String filePath = scan.nextLine();
        ArrayList<MDP.weightedState> allStates = MDP.readFile(filePath);

        System.out.print("Would you like to run model based or model free (case insensitive): ");
        String modelType = scan.nextLine().toLowerCase();

        if(modelType.equals("model based")) {
            MDP.setupTransitionProbabilities(allStates,7500);
            for (int i = 0; i < 520; i++) {
                MDP.simHole(allStates, allStates.get(i % 52).startState, true, modelType, .9);
            }

            for (MDP.weightedState s : allStates) {
                System.out.print(s.startState + " " + s.action + " " + s.finalState + "     Transition Probability: ");
                System.out.printf("%.3f, %n",s.freq / MDP.transitionTable.get(s.startState+s.action));
                // System.out.printf("%.3f %n", s.util);
            }
        }

        if(modelType.equals("model free")) {
            for (int i = 0; i < 520; i++) {
                MDP.simHole(allStates, allStates.get(i % 52).startState, true, modelType, .9);
                // MDP.simHole(allStates, "Fairway", true, modelType);
            }
            for (MDP.weightedState s : allStates) {
                System.out.print(s.startState + " " + s.action + " " + s.finalState + "     Utility: ");
                System.out.printf("%.3f, %n", s.util);
                // System.out.printf("%.3f %n", s.util);
            }
        }

        // MDP.updateModelFreeUtil(allStates, allStates.get(0));
    }
}
