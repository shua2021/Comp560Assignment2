import java.io.*;
import java.util.*;

public class MDP {
    protected static HashMap<String, Integer> transitionTable = new HashMap<>();

    //An object specifically for holding the weight, or probability, associated with going to and from a state
    protected static class weightedState {
        String startState;
        String action;
        double weight; // Probability of going from start state to final state
        double freq; //Will later use this to in order to determine how often I ended up at the final state
        //for the relative frequencies
        double util;
        String finalState;
        double upperWeight = 0; //Will later use in order to randomly pick a state when simulating a hole
        double lowerWeight = 0;

        private weightedState(String sState, String actionA, String fState, double wght, double freq, double util) {
            this.startState = sState;
            this.action = actionA;
            this.finalState = fState;
            this.weight = wght;
            this.freq = freq;
            this.util = util;
        }
    }

    protected static ArrayList<weightedState> readFile(String pathName) throws FileNotFoundException {
        File file = new File(pathName);
        Scanner input = new Scanner(file);

        ArrayList<weightedState> allStates = new ArrayList<weightedState>();

        while(input.hasNext()) {
            String line = input.nextLine();

            if(line.equals("")) {
                break;
            }

            int firstIndexOf = line.indexOf('/');
            String startState = line.substring(0,firstIndexOf);

            int secondIndexOf = line.indexOf('/', firstIndexOf + 1);
            String action = line.substring(firstIndexOf + 1, secondIndexOf);

            int thirdIndexOf = line.indexOf('/', secondIndexOf + 1);
            String finalState = line.substring(secondIndexOf + 1, thirdIndexOf);

            String weightString = line.substring(thirdIndexOf + 1);
            double weight = Double.parseDouble(weightString);

            double tempFreq = 0;
            double tempUtil = 0;

            weightedState stateToAdd = new weightedState(startState, action, finalState, weight, tempFreq, tempUtil);
            allStates.add(stateToAdd);
        }
        return allStates;
    }

    protected static int simHole(ArrayList<weightedState> states, String firstState, boolean readyToUpdate, String modelType,
                                 double exploreValue) {
        int strokes = 0;
        double explore = exploreValue; //A value close to one will explore often and close to 0 will exploit
        String action;
        String stateSPrime;
        String state;

        //For the first iteration select where you would like to start from, I went with the assumption that we started from
        //The fairway every time
        action = pickRandomAction(states, firstState);
        state = firstState;
        while(true) {
            //If we have yet to hit a ball then we should go with the first action we have chosen, otherwise
            //we want to figure out the next action based off the current state
            //Also controls the exploration versus exploitation since
            if(strokes != 0) {
                double r = Math.random();
                if(r < explore) {
                    action = pickRandomAction(states, state);
                } else {
                    action = pickBestAction(states,state);
                }
                explore *= .9;
            }

            //The pickNextState function searches through the list of all possible transitions and only
            //Selects states that have a matching startState and action --> see function for additional comments
            //On how the resulting state is chosen
            weightedState stateS = pickNextState(states, state, action);
            stateSPrime = stateS.finalState;

            //This table keeps track of the total number of times a state/action pair have appeared
            updateTransitionTable(state, action);
            stateS.freq++; //This keeps track of the number of times a specific triplet has occurred
            //From what I believe I think that the transition probability will be:
            //(Freq of getting to S' from S given action a) / (Total number of times you are at state S and take action a)
            //In short: (Freq of triplet) / (Freq or state/action pair)


            if(readyToUpdate && modelType.equals("model based")) {
                updateModelBasedUtil(states, stateS); //Only want to do this after I have simulated all the games
            }

            if(readyToUpdate && modelType.equals("model free")) {
                updateModelFreeUtil(states, stateS);

            }
            strokes++;

            //Check to see if we put the ball in the hole
            if (stateSPrime.equals("In")) {
                break;
            } else {
                //If we haven't put the ball in the hole then we search again, using
                // the result of the action We took earlier as the new state to hit the ball from
                state = stateSPrime;
            }
        }

        //Return the number of strokes it took in order to get to the hole
        return strokes;
    }

    protected static weightedState pickNextState(ArrayList<weightedState> states, String startState, String action) {
        //Every time that we find a state that matches the startState and action criteria we will add that
        //state to the list and the respective states weight
        double accumulatedWeight = 0;

        //By creating a new list of states that match the criteria I avoid searching the allStates list twice later on
        ArrayList<weightedState> matchingStates = new ArrayList<weightedState>();


        for(weightedState s : states) {
            //Make sure the startState and action match the params passed in
            if (s.startState.equals(startState) && s.action.equals(action)) {
                matchingStates.add(s);

                //The following was intended to give each state a weight range within the accumulatedWeight range
                if(accumulatedWeight == 0) {s.lowerWeight = accumulatedWeight;}
                else {s.lowerWeight = accumulatedWeight + .0000001;} //so that there will not be an overlap in the ranges of the numbers
                s.upperWeight = s.lowerWeight + s.weight;
                accumulatedWeight += s.weight;
            }
        }

        //Select a random variable between 0 and accumulated weight
        //I didn't want to assume that the weight was 1.0 every time which is why I did not
        //Hardcode in a 0 to 1 random number generator
        double r = (new Random().nextDouble()) * accumulatedWeight;

        for(weightedState s2: matchingStates) {
            //Compare the random string that we generated and see which range it falls in
            //To me this was a valid process because each state occupies an amount of space equal to their weight within
            //The overall accumulated weight, that is to say for example if the first transition is "Fairway/At/Close/.25"
            //Then it would range from 0 to .25 and therefore in a uniformly distributed random number generator
            //Would have a 25% chance of being chosen
            if(r >= s2.lowerWeight && r <= s2.upperWeight) {
                return s2;
            }
        }
        System.out.println("Error in pickNextState");
        return null;
    }

    protected static String pickRandomAction(ArrayList<weightedState> states, String stateName) {
        ArrayList<String> possibleActions = new ArrayList<String>();
        for(weightedState s: states) {
            if(s.startState.equals(stateName)) {
                if(!(possibleActions.contains(s.action))) {
                    possibleActions.add(s.action);
                }
            }
        }
        Random r = new Random();
        double randomIndex = Math.floor((r.nextDouble() * possibleActions.size()));
        return possibleActions.get((int)randomIndex);
    }

    protected static String pickBestAction(ArrayList<weightedState> states, String state) {
        double maxUtil = Double.NEGATIVE_INFINITY;
        String bestAction = "";
        for(weightedState s : states) {
            if(s.startState.equals(state)) {
                if(s.util > maxUtil) {
                    maxUtil = s.util;
                    bestAction = s.action;
                }
            }
        }
        return bestAction;
    }

    //protected static String pickBestAction(ArrayList<weightedState> states, String stateName) { }
    //Later when I implement the exploration vs exploitation I need to have a way of picking the action with the
    //Highest expected util rather than just a random action

    protected static void updateTransitionTable(String startState, String action) {
        String hashKey = startState + action; // So the keys will look something like "FairwayAt" or "FairwayLeft"
        if(transitionTable.containsKey(hashKey)) {
            transitionTable.replace(hashKey, transitionTable.get(hashKey) + 1);
        } else {
            //Used a hard coded value of 1 because I should only get to this else block if the hash-map
            //Does not already contain the hash key
            transitionTable.put(hashKey, 1);
        }
    }

    public static void setupTransitionProbabilities(ArrayList<weightedState> states, double epsilon) {
        //The purpose of this method is only to simulate the episodes without updating utilities
        //The purpose of this is so that we can obtain the transition probability values for each triplet for model based learning
        int i = 0;
        while (i < epsilon) {
            simHole(states, "Fairway", false, "model based", 1);
            i++;
        }
    }

    protected static void updateModelBasedUtil(ArrayList<weightedState> states, weightedState initState) {
        double discountVal = .99;
        double maxUtil = Double.NEGATIVE_INFINITY;
        for(weightedState s : states) {
            if(s.startState.equals(initState.startState) && s.action.equals(initState.action)) {
                double probsOfSPrime;
                if(transitionTable.get(s.startState+s.action) == 0) {
                    probsOfSPrime = 0;
                } else {
                    probsOfSPrime = s.freq / transitionTable.get(s.startState + s.action);
                }

                //The value below it meant to calculate the utility of taking a certain action a, at state s
                //I used the equation and slides for the value iteration method involving grid world
                //In the grid world example we looked at the utility of taking an action, in the example left, right, up or down
                //And then from there take the action that has the highest expected value and set that as the new utility for
                //The original state s.
                double newUtil = 1 + discountVal * (s.util * probsOfSPrime); //R(s) in this scenario = 1
                if(newUtil > maxUtil) {
                    maxUtil = newUtil;
                }
            }
        }
        //Optimal policy comes from maximizing the utility of each action
        initState.util = maxUtil;
    }

    protected static void updateModelFreeUtil(ArrayList<weightedState> states, weightedState initState) {
        //For the model free method I approached this in a different way suggested to me by Juan
        //I used the equation:
        //Q(S,A) = (1 - learningRate) * Q(S,A) + learningRate * sample
        //Where sample = reward function + discountValue * (the maximum expected utility of state S')

        double discountValue = .9;
        double learningRate = .02;
        double maxUtil = Double.NEGATIVE_INFINITY;
        double sample;

        //I was struggling to understand the notation of Q(S',a), I understood that
        //We were looking for the maximum expected utility of the next state, however
        //I don't know that I computed the value correctly
        for(weightedState s : states) {
            //Need to find Q(S',a) which is the maximum expected utility of all actions in state S'
            //Look at the triplets whose initial states are S'
            if(s.startState.equals(initState.finalState)) {
                if(s.util > maxUtil) {
                    //Compare the util of the new triplet that would look something like
                    // S'/a'/S" and if the new triplet has a higher utility set that to be
                    //the new maximum utility
                    maxUtil = s.util;
                }
            }
        }

        if(initState.finalState.equals("In")) {maxUtil = 1;}

        sample = learningRate * maxUtil;
        initState.util = 1 + (1-learningRate) * initState.util + learningRate * sample;
    }
}
