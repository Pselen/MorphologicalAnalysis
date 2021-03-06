package MorphologicalAnalysis;

import java.io.*;
import java.util.*;
import Corpus.*;
import DataStructure.CounterHashMap;

public class TestMorphologicalAnalysis {

    public static void testWord(FsmMorphologicalAnalyzer fsm, String word){
        FsmParseList fsmParses = fsm.morphologicalAnalysis(word);
        for (int i = 0; i < fsmParses.size(); i++){
            System.out.println(fsmParses.getFsmParse(i).transitionList());
        }
    }

    public static void testWordForLemma(FsmMorphologicalAnalyzer fsm, String word, String pos){
        FsmParseList fsmParses = fsm.morphologicalAnalysis(word);
        for (int i = 0; i < fsmParses.size(); i++){
            System.out.println(fsmParses.getFsmParse(i).getLastLemmaWithTag(pos));
        }
    }

    public static void analyze(){
        FsmMorphologicalAnalyzer fsm = new FsmMorphologicalAnalyzer();
        Scanner sc = new Scanner(System.in);
        String word = "ali topu at";
        while (word.length() > 1){
            System.out.print("->");
            word = sc.next();
            testWord(fsm, word);
        }
    }

    public static void checkSpeed(){
        int k = 0;
        FsmMorphologicalAnalyzer fsm = new FsmMorphologicalAnalyzer(1000000);
        long previous = Calendar.getInstance().getTimeInMillis();
        String line;
        try {
            FileReader fr = new FileReader("./Data/Corpus/gazete.txt");
            BufferedReader br = new BufferedReader(fr);
            line = br.readLine();
            while (line != null){
                Sentence sentence = new Sentence(line);
                for (int j = 0; j < sentence.wordCount(); j++){
                    fsm.morphologicalAnalysis(sentence.getWord(j).getName());
                    k++;
                    if (k % 10000 == 0){
                        long now = Calendar.getInstance().getTimeInMillis();
                        System.out.println((1000.0 * k) / (now - previous) + " words per sec");
                    }
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void generateDistinctWordWithPos(){
        FsmMorphologicalAnalyzer fsm = new FsmMorphologicalAnalyzer(1000000);
        String line;
        CounterHashMap<String> hashMap = new CounterHashMap<>();
        try {
            FileReader fr = new FileReader("./Data/Corpus/gazete.txt");
            BufferedReader br = new BufferedReader(fr);
            line = br.readLine();
            while (line != null){
                Sentence sentence = new Sentence(line);
                for (int j = 0; j < sentence.wordCount(); j++){
                    FsmParseList parseList = fsm.morphologicalAnalysis(sentence.getWord(j).getName());
                    ArrayList<FsmParseList> parseLists = parseList.constructParseListForDifferentRootWithPos();
                    if (parseLists.size() > 1){
                        String key = "";
                        for (FsmParseList fsmParseList : parseLists){
                            if (key.length() != 0){
                                key += "$" + fsmParseList.getFsmParse(0).getWordWithPos().toString();
                            } else {
                                key = fsmParseList.getFsmParse(0).getWordWithPos().toString();
                            }
                        }
                        hashMap.put(key);
                    }
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String key : hashMap.keySet()){
            System.out.println(key + ":" + hashMap.get(key));
        }
    }

    public static void generateDistinctSubAnalyses(){
        FsmMorphologicalAnalyzer fsm = new FsmMorphologicalAnalyzer(1000000);
        String line;
        CounterHashMap<String> hashMap = new CounterHashMap<>();
        try {
            FileReader fr = new FileReader("./Data/Corpus/gazete.txt");
            BufferedReader br = new BufferedReader(fr);
            line = br.readLine();
            while (line != null){
                Sentence sentence = new Sentence(line);
                for (int j = 0; j < sentence.wordCount(); j++){
                    FsmParseList parseList = fsm.morphologicalAnalysis(sentence.getWord(j).getName());
                    ArrayList<FsmParseList> parseLists = parseList.constructParseListForDifferentRootWithPos();
                    for (FsmParseList fsmParseList : parseLists){
                        if (fsmParseList.size() > 1){
                            String key = fsmParseList.parsesWithoutPrefixAndSuffix();
                            if (!hashMap.containsKey(key)){
                                System.out.println(sentence.getWord(j).getName() + "->" + key);
                            }
                            hashMap.put(key);
                        }
                    }
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String key : hashMap.keySet()){
            System.out.println(key + ":" + hashMap.get(key));
        }
    }

    public static void generateParsed(){
        HashSet<String> parsed = new HashSet<>();
        FsmMorphologicalAnalyzer fsm = new FsmMorphologicalAnalyzer(1000000);
        String line;
        try {
            PrintWriter output = new PrintWriter(new File("gazete-parsed1.txt"));
            FileReader fr = new FileReader("./Data/Corpus/gazete.txt");
            BufferedReader br = new BufferedReader(fr);
            line = br.readLine();
            while (line != null){
                Sentence sentence = new Sentence(line);
                for (int j = 0; j < sentence.wordCount(); j++){
                    String word = sentence.getWord(j).getName();
                    if (!parsed.contains(word)){
                        parsed.add(word);
                        FsmParseList parseList = fsm.morphologicalAnalysis(word);
                        if (parseList.size() > 0){
                            output.println(word + " " + parseList.size());
                            output.print(parseList.toString());
                        }
                    }
                }
                line = br.readLine();
            }
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void checkCorrectness(){
        FsmMorphologicalAnalyzer fsm = new FsmMorphologicalAnalyzer();
        Scanner s;
        try {
            s = new Scanner(new File("gazete-parsed.txt"));
            while (s.hasNext()){
                String word = s.next();
                int parseCount = s.nextInt();
                boolean ok = true;
                FsmParseList parseList = fsm.morphologicalAnalysis(word);
                if (parseList.size() != parseCount){
                    ok = false;
                    System.out.println(word);
                    System.out.println("---------------------");
                    System.out.print(parseList);
                    System.out.println("---------------------");
                }
                for (int i = 0; i < parseCount; i++){
                    if (!ok){
                        System.out.println(s.next());
                    } else {
                        s.next();
                    }
                }
                if (!ok){
                    System.out.println("*********************");
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (InputMismatchException i){
            i.printStackTrace();
        }
    }

    public static void main(String[] args){
        analyze();
    }

}
