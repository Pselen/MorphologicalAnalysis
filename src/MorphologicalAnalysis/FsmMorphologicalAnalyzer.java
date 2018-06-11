package MorphologicalAnalysis;

import Corpus.Sentence;
import DataStructure.Cache.LRUCache;
import Dictionary.Trie.Trie;
import Dictionary.*;
import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.*;

public class FsmMorphologicalAnalyzer {

    private Trie dictionaryTrie;
    private ArrayList<State> states;
    private static final int MAX_DISTANCE = 2;
    private TxtDictionary dictionary;
    private LRUCache<String, FsmParseList> cache;

    /**
     * First no-arg constructor of FsmMorphologicalAnalyzer class. It generates a new TxtDictionary type dictionary from
     * turkish_dictionary.txt with fixed cache size 100000 and by using turkish_finite_state_machine.xml file.
     */
    public FsmMorphologicalAnalyzer() {
        this("turkish_finite_state_machine.xml", new TxtDictionary("Data/Dictionary/turkish_dictionary.txt", new TurkishWordComparator()), 100000);

    }

    /**
     * Another constructor of FsmMorphologicalAnalyzer class. It generates a new TxtDictionary type dictionary from
     * turkish_dictionary.txt with given input cacheSize and by using turkish_finite_state_machine.xml file.
     *
     * @param cacheSize the size of the LRUCache.
     */
    public FsmMorphologicalAnalyzer(int cacheSize) {
        this("turkish_finite_state_machine.xml", new TxtDictionary("Data/Dictionary/turkish_dictionary.txt", new TurkishWordComparator()), cacheSize);
    }


    /**
     * Another constructor of FsmMorphologicalAnalyzer class. It generates a new TxtDictionary type dictionary from
     * given input dictionary, with given inputs fileName and cacheSize.
     *
     * @param fileName   the file to read the finite state machine.
     * @param dictionary the dictionary file that will be used to generate dictionaryTrie.
     * @param cacheSize  the size of the LRUCache.
     */
    public FsmMorphologicalAnalyzer(String fileName, TxtDictionary dictionary, int cacheSize) {
        this.dictionary = dictionary;
        readFsm(fileName);
        dictionaryTrie = prepareTrie(dictionary);
        cache = new LRUCache<>(cacheSize);
    }

    /**
     * Another constructor of FsmMorphologicalAnalyzer class. It generates a new TxtDictionary type dictionary from
     * given input dictionary, with given input fileName and fixed size cacheSize = 100000.
     *
     * @param fileName   the file to read the finite state machine.
     * @param dictionary the dictionary file that will be used to generate dictionaryTrie.
     */
    public FsmMorphologicalAnalyzer(String fileName, TxtDictionary dictionary) {
        this(fileName, dictionary, 100000);
    }

    /**
     * readFsm method reads the finite state machine in the given input file. It has a NodeList which holds the states
     * of the nodes and there are 4 different type of nodes; stateNode, root Node, transitionNode and withNode.
     * Also there are two states; state that a node currently in and state that a node will be in.
     * <p>
     * DOMParser is used to parse the given file. Firstly it gets the document to parse then gets its elements by the
     * tag names. For instance, it gets states by the tag name 'state' and puts them into an ArrayList called stateList.
     * Secondly, it traverses this stateList and gets each Node's attributes. There are three attributes; name, start
     * and, end which will be named as states. If a node is in a startState it is tagged as 'yes', otherwise 'no'.
     * Also, if a node is in a startState, additional attribute will be fetched; originalPos that represents its original
     * part of speech.
     * <p>
     * At the last step, by starting rootNode's first child, it gets all the transitionNodes and next states called toState,
     * then continue with the nextSiblings. Also, if there is no possible toState, it prints this case and the causative states.
     *
     * @param fileName the file to read the finite state machine.
     */
    private void readFsm(String fileName) {
        int i;
        boolean startState, endState;
        NodeList stateList;
        Node stateNode, rootNode, transitionNode, withNode;
        State state, toState;
        String stateName, withName, originalPos, rootToPos, toPos;
        NamedNodeMap attributes;
        DOMParser parser = new DOMParser();
        Document doc;
        try {
            parser.parse(fileName);
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }
        doc = parser.getDocument();
        stateList = doc.getElementsByTagName("state");
        states = new ArrayList<>();
        for (i = 0; i < stateList.getLength(); i++) {
            stateNode = stateList.item(i);
            attributes = stateNode.getAttributes();
            stateName = attributes.getNamedItem("name").getNodeValue();
            startState = attributes.getNamedItem("start").getNodeValue().equalsIgnoreCase("yes");
            endState = attributes.getNamedItem("end").getNodeValue().equalsIgnoreCase("yes");
            if (startState) {
                originalPos = attributes.getNamedItem("originalpos").getNodeValue();
                states.add(new State(stateName, true, endState, originalPos));
            } else {
                states.add(new State(stateName, false, endState));
            }
        }
        rootNode = doc.getFirstChild();
        stateNode = rootNode.getFirstChild();
        while (stateNode != null) {
            if (stateNode.hasAttributes()) {
                attributes = stateNode.getAttributes();
                stateName = attributes.getNamedItem("name").getNodeValue();
                state = getState(stateName);
                transitionNode = stateNode.getFirstChild();
                while (transitionNode != null) {
                    if (transitionNode.hasAttributes()) {
                        attributes = transitionNode.getAttributes();
                        stateName = attributes.getNamedItem("name").getNodeValue();
                        if (attributes.getNamedItem("transitionname") != null) {
                            withName = attributes.getNamedItem("transitionname").getNodeValue();
                        } else {
                            withName = null;
                        }
                        if (attributes.getNamedItem("topos") != null) {
                            rootToPos = attributes.getNamedItem("topos").getNodeValue();
                        } else {
                            rootToPos = null;
                        }
                        toState = getState(stateName);
                        if (toState != null) {
                            withNode = transitionNode.getFirstChild();
                            while (withNode != null) {
                                if (withNode.getFirstChild() != null) {
                                    if (withNode.hasAttributes()) {
                                        attributes = withNode.getAttributes();
                                        withName = attributes.getNamedItem("name").getNodeValue();
                                        if (attributes.getNamedItem("toPos") != null) {
                                            toPos = attributes.getNamedItem("topos").getNodeValue();
                                        } else {
                                            toPos = null;
                                        }
                                    } else {
                                        toPos = null;
                                    }
                                    if (toPos == null) {
                                        if (rootToPos == null) {
                                            state.addTransition(toState, withNode.getFirstChild().getNodeValue(), withName);
                                        } else {
                                            state.addTransition(toState, withNode.getFirstChild().getNodeValue(), withName, rootToPos);
                                        }
                                    } else {
                                        state.addTransition(toState, withNode.getFirstChild().getNodeValue(), withName, toPos);
                                    }
                                }
                                withNode = withNode.getNextSibling();
                            }
                        } else {
                            System.out.println("From state " + state.getName() + " to state " + stateName + " does not exist");
                        }
                    }
                    transitionNode = transitionNode.getNextSibling();
                }
            }
            stateNode = stateNode.getNextSibling();
        }
    }

    /**
     * The addWordWhenRootSoften is used to add word to Trie whose last consonant will be soften.
     * For instance,
     * In the case of Dative Case Suffix, the word is 'müzik' when '-e' is added to the word, the last char is drooped
     * and root became 'müzi' and by changing 'k' into 'ğ' the word transformed into 'müziğe' as in the case of
     * 'Herkes müziğe doğru geldi'.
     * <p>
     * In the case of accusative, possessive of third person and a derivative suffix, the word is 'kanat' when '-i' is
     * added to word, last char is dropped, root became 'kana' then 't' transformed into 'd' and added to Trie. The word is
     * changed into 'kanadı' as in the case of 'Kuşun kırık kanadı'.
     *
     * @param trie the name of the Trie to add the word.
     * @param last the last char of the word to be soften.
     * @param root the substring of the word whose last one or two chars are omitted from the word to bo softed.
     * @param word the original word.
     */
    private void addWordWhenRootSoften(Trie trie, char last, String root, TxtWord word) {
        switch (last) {
            case 'p':
                trie.addWord(root + 'b', word);
                break;
            case '\u00e7': //ç
                trie.addWord(root + 'c', word);
                break;
            case 't':
                trie.addWord(root + 'd', word);
                break;
            case 'k':
            case 'g':
                trie.addWord(root + '\u011f', word); //ğ
                break;
        }
    }

    /**
     * The prepareTrie method is used to create a Trie with the given dictionary. First, it gets the word from dictionary,
     * then checks some exceptions like 'ben' which does not fit in the consonant softening rule and transform into 'bana',
     * and later on it generates a root by removing the last char from the word however if the length of the word is greater
     * than 1, it also generates the root by removing the last two chars from the word.
     * <p>
     * Then, it gets the last char of the root and adds root and word to the result Trie. There are also special cases
     * such as; if lastIdropsDuringSuffixation condition holds then addWordWhenRootSoften method will be used rather than addWord,
     * if isPortmanteauEndingWithSI condition holds then addWord method with rootWithoutLastTwo will be used, if isPortmanteau
     * condition holds then addWord method with rootWithoutLast will be used, if vowelEChangesToIDuringYSuffixation
     * condition holds then addWord method with rootWithoutLast will be used depending on the last char whether it is
     * 'e' or 'a' and, if endingKChangesIntoG condition holds then addWord method with rootWithoutLast will be used with added 'g'.
     *
     * @param currentDictionary the dictionary that Trie will be created.
     * @return the resulting Trie.
     */
    private Trie prepareTrie(TxtDictionary currentDictionary) {
        Trie result = new Trie();
        String root, rootWithoutLast, rootWithoutLastTwo;
        char last;
        for (int i = 0; i < currentDictionary.size(); i++) {
            TxtWord word = (TxtWord) currentDictionary.getWord(i);
            root = word.getName();
            if (root.equals("ben")) {
                result.addWord("bana", word);
            }
            rootWithoutLast = root.substring(0, root.length() - 1);
            if (root.length() > 1) {
                rootWithoutLastTwo = root.substring(0, root.length() - 2);
            } else {
                rootWithoutLastTwo = "";
            }
            last = root.charAt(root.length() - 1);
            // TODO: 6/10/2018 why did we add word to result before checking the cases?
            result.addWord(root, word);
            if (word.lastIdropsDuringSuffixation() || word.lastIdropsDuringPassiveSuffixation()) {
                if (word.rootSoftenDuringSuffixation()) {
                    addWordWhenRootSoften(result, last, rootWithoutLastTwo, word);
                } else {
                    result.addWord(rootWithoutLastTwo + last, word);
                }
            }
            // NominalRootNoPossesive
            if (word.isPortmanteauEndingWithSI()) {
                result.addWord(rootWithoutLastTwo, word);
            }
            if (word.rootSoftenDuringSuffixation()) {
                addWordWhenRootSoften(result, last, rootWithoutLast, word);
            }
            if (word.isPortmanteau()) {
                result.addWord(rootWithoutLast, word);
            }
            if (word.vowelEChangesToIDuringYSuffixation() || word.vowelAChangesToIDuringYSuffixation()) {
                switch (last) {
                    // TODO: 6/10/2018 both do the same thing
                    case 'e':
                        result.addWord(rootWithoutLast, word);
                        break;
                    case 'a':
                        result.addWord(rootWithoutLast, word);
                        break;
                }
            }
            if (word.endingKChangesIntoG()) {
                result.addWord(rootWithoutLast + 'g', word);
            }
        }
        return result;
    }

    // TODO: 6/10/2018 do this part after mophologicalparse class.


    public HashSet<String> getPossibleWords(MorphologicalParse morphologicalParse, MetamorphicParse parse) {
        boolean isRootVerb = morphologicalParse.getRootPos().equals("VERB");
        boolean containsVerb = morphologicalParse.containsTag(MorphologicalTag.VERB);
        Transition transition, verbTransition = new Transition("mAk");
        TxtWord compoundWord, currentRoot;
        HashSet<String> result = new HashSet<>();
        if (parse == null || parse.getWord() == null) {
            return result;
        }
        String verbWord, pluralWord, currentWord = parse.getWord().getName();
        int pluralIndex = -1;
        compoundWord = dictionaryTrie.getCompoundWordStartingWith(currentWord);
        if (!isRootVerb) {
            if (compoundWord != null && compoundWord.getName().length() - currentWord.length() < 3) {
                result.add(compoundWord.getName());
            }
            result.add(currentWord);
        }
        currentRoot = (TxtWord) dictionary.getWord(parse.getWord().getName());
        if (currentRoot == null && compoundWord != null) {
            currentRoot = compoundWord;
        }
        if (currentRoot != null) {
            if (isRootVerb) {
                verbWord = verbTransition.makeTransition(currentRoot, currentWord);
                result.add(verbWord);
            }
            pluralWord = null;
            for (int i = 1; i < parse.size(); i++) {
                transition = new Transition(null, parse.getMetaMorpheme(i), null);
                if (parse.getMetaMorpheme(i).equals("lAr")) {
                    pluralWord = currentWord;
                    pluralIndex = i + 1;
                }
                currentWord = transition.makeTransition(currentRoot, currentWord);
                result.add(currentWord);
                if (containsVerb) {
                    verbWord = verbTransition.makeTransition(currentRoot, currentWord);
                    result.add(verbWord);
                }
            }
            if (pluralWord != null) {
                currentWord = pluralWord;
                for (int i = pluralIndex; i < parse.size(); i++) {
                    transition = new Transition(null, parse.getMetaMorpheme(i), null);
                    currentWord = transition.makeTransition(currentRoot, currentWord);
                    result.add(currentWord);
                    if (containsVerb) {
                        verbWord = verbTransition.makeTransition(currentRoot, currentWord);
                        result.add(verbWord);
                    }
                }
            }
        }
        return result;
    }

    // TODO: 6/10/2018 never used method.

    /**
     * The isValidTransition loops through states ArrayList and checks transitions between states. If the actual transition
     * equals to the given transition input, method returns true otherwise returns false.
     *
     * @param transition is used to compare with the actual transition of a state.
     * @return true when the actual transition equals to the transition input, false otherwise.
     */
    public boolean isValidTransition(String transition) {
        for (State state : states) {
            for (int i = 0; i < state.transitionCount(); i++) {
                if (state.getTransition(i).toString() != null && state.getTransition(i).toString().equals(transition)) {
                    return true;
                }
            }
        }
        return false;
    }

    // TODO: 6/10/2018 never used.

    /**
     * The getDictionary method is used to get TxtDictionary.
     *
     * @return dictionary.
     */
    public TxtDictionary getDictionary() {
        return dictionary;
    }

    /**
     * The getState method is used to loop through the states ArrayList and return the state whose name equals
     * to the given input name.
     *
     * @param name is used to compare with the state's actual name.
     * @return state if found any, null otherwise.
     */
    private State getState(String name) {
        for (State state : states) {
            if (state.getName().equalsIgnoreCase(name)) {
                return state;
            }
        }
        return null;
    }

    /**
     * The isPossibleSubstring method first checks whether given short and long strings are equal to root word.
     * Then, compares both short and long strings' chars till the last two chars of short string. In the presence of mismatch,
     * false is returned. On the other hand, it counts the distance between two strings until it becomes greater than 2,
     * which is the MAX_DISTANCE also finds the finds the index of the last char.
     * <p>
     * If the substring is a rootWord and equals to 'ben', which is a special case or root holds the lastIdropsDuringSuffixation or
     * lastIdropsDuringPassiveSuffixation conditions, then it returns true if distance is not greater than MAX_DISTANCE.
     * <p>
     * On the other hand, if the shortStrong ends with one of these chars 'e, a, p, ç, t, k' and 't 's a rootWord with
     * the conditions of rootSoftenDuringSuffixation, vowelEChangesToIDuringYSuffixation, vowelAChangesToIDuringYSuffixation
     * or endingKChangesIntoG then it returns true if the last index is not equal to 2 and distance is not greater than
     * MAX_DISTANCE and false otherwise.
     *
     * @param shortString the possible substring.
     * @param longString  the long string to compare with substring.
     * @param root        the root of the long string.
     * @return true if given substring is the actual substring of the longString, false otherwise.
     */
    private boolean isPossibleSubstring(String shortString, String longString, TxtWord root) {
        boolean rootWord = ((shortString == root.getName()) || longString == root.getName());
        int distance = 0, j, last = 1;
        for (j = 0; j < shortString.length(); j++) {
            if (shortString.charAt(j) != longString.charAt(j)) {
                if (j < shortString.length() - 2) {
                    return false;
                }
                last = shortString.length() - j;
                distance++;
                if (distance > MAX_DISTANCE) {
                    break;
                }
            }
        }
        if (rootWord && (root.getName().equals("ben") || root.lastIdropsDuringSuffixation() || root.lastIdropsDuringPassiveSuffixation())) {
            return (distance <= MAX_DISTANCE);
        } else {
            if (shortString.endsWith("e") || shortString.endsWith("a") || shortString.endsWith("p") || shortString.endsWith("ç") || shortString.endsWith("t") || shortString.endsWith("k") || (rootWord && (root.rootSoftenDuringSuffixation() || root.vowelEChangesToIDuringYSuffixation() || root.vowelAChangesToIDuringYSuffixation() || root.endingKChangesIntoG()))) {
                return (last != 2 && distance <= MAX_DISTANCE - 1);
            } else {
                return (distance <= MAX_DISTANCE - 2);
            }
        }
    }

    /**
     * The initializeParseList method initializes the given given fsm ArrayList with given root words by parsing them.
     * <p>
     * It checks many conditions;
     * +1st one is isPlural; if root holds the condition then it gets the state with the name of
     * NominalRootPlural, then creates a new parsing and adds this to the input fsmParse Arraylist.
     * Ex -> açıktohumlular
     * +2nd one is !isPlural and isPortmanteauEndingWithSI, if root holds the conditions then it gets the state with the
     * name of NNominalRootNoPossesive.
     * Ex -> balarısı
     * +3rd one is !isPlural and isPortmanteau, if root holds the conditions then it gets the state with the name of
     * CompoundNounRoot.
     * Ex -> aslanağızı
     * +4th one is !isPlural, !isPortmanteau and isHeader, if root holds the conditions then it gets the state with the
     * name of HeaderRoot.
     * Ex ->  </title>
     * +5th one is !isPlural, !isPortmanteau and isInterjection, if root holds the conditions then it gets the state
     * with the name of InterjectionRoot.
     * Ex -> Hey!, Aa!
     * +6th one is !isPlural, !isPortmanteau and isDuplicate, if root holds the conditions then it gets the state
     * with the name of DuplicateRoot.
     * Ex -> Bullak
     * +7th one is !isPlural, !isPortmanteau and isNumeral, if root holds the conditions then it gets the state
     * with the name of CardinalRoot.
     * Ex -> Two
     * +8th one is !isPlural, !isPortmanteau and isReal, if root holds the conditions then it gets the state
     * with the name of RealRoot.
     * Ex -> 1, 2
     * +9th one is !isPlural, !isPortmanteau and isFraction, if root holds the conditions then it gets the state
     * with the name of FractionRoot.
     * Ex -> ondalık
     * +10th one is !isPlural, !isPortmanteau and isDate, if root holds the conditions then it gets the state
     * with the name of DateRoot.
     * Ex -> 11/06/2018
     * +11th one is !isPlural, !isPortmanteau and isPercent, if root holds the conditions then it gets the state
     * with the name of PercentRoot.
     * Ex -> %12.5
     * +12th one is !isPlural, !isPortmanteau and isRange, if root holds the conditions then it gets the state
     * with the name of RangeRoot.
     * Ex -> 3-5
     * +13th one is !isPlural, !isPortmanteau and isTime, if root holds the conditions then it gets the state
     * with the name of TimeRoot.
     * Ex -> 13:16:08
     * +14th one is !isPlural, !isPortmanteau and isOrdinal, if root holds the conditions then it gets the state
     * with the name of OrdinalRoot.
     * Ex -> altıncı
     * +15th one is !isPlural, !isPortmanteau, and isVerb if root holds the conditions then it gets the state
     * with the name of VerbalRoot. Or isPassive, then it gets the state with the name of PassiveHn.
     * Ex -> anla (!isPAssive)
     * Ex -> çağrıl (osPassive)
     * +16th one is !isPlural, !isPortmanteau and isPronoun, if root holds the conditions then it gets the state
     * with the name of PronounRoot. There are 6 different Pronoun state names, REFLEX, QUANT, QUANTPLURAL, DEMONS, PERS, QUES.
     * REFLEX = Reflexive Pronouns Ex -> kendi
     * QUANT = Quantitative Pronouns Ex -> öbür, hep, kimse, hiçbiri, bazı, kimi, biri
     * QUANTPLURAL = Quantitative Plural Pronouns Ex -> tümü, çoğu, hepsi
     * DEMONS = Demonstrative Pronouns Ex -> o, bu, şu
     * PERS = Personal Pronouns Ex -> kben, sen, o, biz, siz, onlar
     * QUES = Interrogatıve Pronouns Ex -> nere, ne, kim, hangi
     * +17th one is !isPlural, !isPortmanteau and isAdjective, if root holds the conditions then it gets the state
     * with the name of AdjectiveRoot.
     * Ex -> absürt, abes
     * +18th one is !isPlural, !isPortmanteau and isPureAdjective, if root holds the conditions then it gets the state
     * with the name of Adjective.
     * Ex -> geçmiş, cam
     * +19th one is !isPlural, !isPortmanteau and isNominal, if root holds the conditions then it gets the state
     * with the name of NominalRoot.
     * Ex -> görüş
     * +20th one is !isPlural, !isPortmanteau and isProper, if root holds the conditions then it gets the state
     * with the name of ProperRoot.
     * Ex -> Abdi
     * +21th one is !isPlural, !isPortmanteau and isQuestion, if root holds the conditions then it gets the state
     * with the name of QuestionRoot.
     * Ex -> mi, mü
     * +22nd one is !isPlural, !isPortmanteau and isDeterminer, if root holds the conditions then it gets the state
     * with the name of DeterminerRoot.
     * Ex -> çok, bir
     * +23rd one is !isPlural, !isPortmanteau and isConjunction, if root holds the conditions then it gets the state
     * with the name of ConjunctionRoot.
     * Ex -> ama , ancak
     * +24th one is !isPlural, !isPortmanteau and isPostP, if root holds the conditions then it gets the state
     * with the name of PostP.
     * Ex -> ait, dair
     * +25th one is !isPlural, !isPortmanteau and isAdverb, if root holds the conditions then it gets the state
     * with the name of AdverbRoot.
     * Ex -> acilen, ZICIK
     *
     * @param fsmParse ArrayList to initialize.
     * @param root     word to check properties and add to fsmParse according to them.
     * @param isProper is used to check a word is proper or not.
     */
    private void initializeParseList(ArrayList<FsmParse> fsmParse, TxtWord root, boolean isProper) {
        FsmParse currentFsmParse;
        if (root.isPlural()) {
            currentFsmParse = new FsmParse(root, getState("NominalRootPlural"));
            fsmParse.add(currentFsmParse);
        } else {
            if (root.isPortmanteauEndingWithSI()) {
                currentFsmParse = new FsmParse(root, getState("NominalRootNoPossesive"));
                fsmParse.add(currentFsmParse);
            } else {
                if (root.isPortmanteau()) {
                    currentFsmParse = new FsmParse(root.getName().substring(0, root.getName().length() - 1), getState("CompoundNounRoot"));
                    fsmParse.add(currentFsmParse);
                } else {
                    if (root.isHeader()) {
                        currentFsmParse = new FsmParse(root, getState("HeaderRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isInterjection()) {
                        currentFsmParse = new FsmParse(root, getState("InterjectionRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isDuplicate()) {
                        currentFsmParse = new FsmParse(root, getState("DuplicateRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isNumeral()) {
                        currentFsmParse = new FsmParse(root, getState("CardinalRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isReal()) {
                        currentFsmParse = new FsmParse(root, getState("RealRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isFraction()) {
                        currentFsmParse = new FsmParse(root, getState("FractionRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isDate()) {
                        currentFsmParse = new FsmParse(root, getState("DateRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isPercent()) {
                        currentFsmParse = new FsmParse(root, getState("PercentRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isRange()) {
                        currentFsmParse = new FsmParse(root, getState("RangeRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isTime()) {
                        currentFsmParse = new FsmParse(root, getState("TimeRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isOrdinal()) {
                        currentFsmParse = new FsmParse(root, getState("OrdinalRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isVerb() || root.isPassive()) {
                        if (!root.verbType().equalsIgnoreCase("")) {
                            currentFsmParse = new FsmParse(root, getState("VerbalRoot(" + root.verbType() + ")"));
                        } else {
                            if (!root.isPassive()) {
                                currentFsmParse = new FsmParse(root, getState("VerbalRoot"));
                            } else {
                                currentFsmParse = new FsmParse(root, getState("PassiveHn"));
                            }
                        }
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isPronoun()) {
                        if (root.getName().equalsIgnoreCase("kendi")) {
                            currentFsmParse = new FsmParse(root, getState("PronounRoot(REFLEX)"));
                            fsmParse.add(currentFsmParse);
                        }
                        if (root.getName().equalsIgnoreCase("öbür") || root.getName().equalsIgnoreCase("hep") || root.getName().equalsIgnoreCase("kimse") || root.getName().equalsIgnoreCase("hiçbiri") || root.getName().equalsIgnoreCase("birbiri") || root.getName().equalsIgnoreCase("birbirleri") || root.getName().equalsIgnoreCase("biri") || root.getName().equalsIgnoreCase("bazı") || root.getName().equalsIgnoreCase("kimi")) {
                            currentFsmParse = new FsmParse(root, getState("PronounRoot(QUANT)"));
                            fsmParse.add(currentFsmParse);
                        }
                        if (root.getName().equalsIgnoreCase("tümü") || root.getName().equalsIgnoreCase("çoğu") || root.getName().equalsIgnoreCase("hepsi")) {
                            currentFsmParse = new FsmParse(root, getState("PronounRoot(QUANTPLURAL)"));
                            fsmParse.add(currentFsmParse);
                        }
                        if (root.getName().equalsIgnoreCase("o") || root.getName().equalsIgnoreCase("bu") || root.getName().equalsIgnoreCase("şu")) {
                            currentFsmParse = new FsmParse(root, getState("PronounRoot(DEMONS)"));
                            fsmParse.add(currentFsmParse);
                        }
                        if (root.getName().equalsIgnoreCase("ben") || root.getName().equalsIgnoreCase("sen") || root.getName().equalsIgnoreCase("o") || root.getName().equalsIgnoreCase("biz") || root.getName().equalsIgnoreCase("siz") || root.getName().equalsIgnoreCase("onlar")) {
                            currentFsmParse = new FsmParse(root, getState("PronounRoot(PERS)"));
                            fsmParse.add(currentFsmParse);
                        }
                        if (root.getName().equalsIgnoreCase("nere") || root.getName().equalsIgnoreCase("ne") || root.getName().equalsIgnoreCase("kim") || root.getName().equalsIgnoreCase("hangi")) {
                            currentFsmParse = new FsmParse(root, getState("PronounRoot(QUES)"));
                            fsmParse.add(currentFsmParse);
                        }
                    }
                    if (root.isAdjective()) {
                        currentFsmParse = new FsmParse(root, getState("AdjectiveRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isPureAdjective()) {
                        currentFsmParse = new FsmParse(root, getState("Adjective"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isNominal()) {
                        currentFsmParse = new FsmParse(root, getState("NominalRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isProperNoun() && isProper) {
                        currentFsmParse = new FsmParse(root, getState("ProperRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isQuestion()) {
                        currentFsmParse = new FsmParse(root, getState("QuestionRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isDeterminer()) {
                        currentFsmParse = new FsmParse(root, getState("DeterminerRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isConjunction()) {
                        currentFsmParse = new FsmParse(root, getState("ConjunctionRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isPostP()) {
                        currentFsmParse = new FsmParse(root, getState("PostP"));
                        fsmParse.add(currentFsmParse);
                    }
                    if (root.isAdverb()) {
                        currentFsmParse = new FsmParse(root, getState("AdverbRoot"));
                        fsmParse.add(currentFsmParse);
                    }
                }
            }
        }
    }

    /**
     * The initializeRootList method is used to create an ArrayList which consists of initial fsm parsings. First,
     * it calls getWordsWithPrefix methods by using input String surfaceForm and generates a HashSet. Then, traverses
     * this HashSet and uses each word as a root and calls initializeParseList method with this root and ArrayList.
     * <p>
     *
     * @param surfaceForm the String used to generate a HashSet of words.
     * @param isProper    is used to check a word is proper or not.
     * @return initialFsmParse ArrayList.
     */
    private ArrayList<FsmParse> initializeRootList(String surfaceForm, boolean isProper) {
        TxtWord root;
        ArrayList<FsmParse> initialFsmParse;
        initialFsmParse = new ArrayList<>();
        if (surfaceForm.length() == 0) {
            return initialFsmParse;
        }
        HashSet<Word> words = dictionaryTrie.getWordsWithPrefix(surfaceForm);
        for (Word word : words) {
            root = (TxtWord) word;
            initializeParseList(initialFsmParse, root, isProper);
        }
        return initialFsmParse;
    }

    // TODO: 6/10/2018 check this explanation

    /**
     * The addNewParsesFromCurrentParse method initially gets the final suffixes from input currentFsmParse called as currentState,
     * and by using the currentState information it gets the currentSurfaceForm. Then loops through each currentState's transition.
     * If the currentTransition is possible, it makes the transition
     *
     * @param currentFsmParse FsmParse type input.
     * @param fsmParse        an ArrayList of FsmParse.
     * @param surfaceForm     String to use during transition.
     * @param root            TxtWord used to make transition.
     */
    private void addNewParsesFromCurrentParse(FsmParse currentFsmParse, ArrayList<FsmParse> fsmParse, String surfaceForm, TxtWord root) {
        State currentState = currentFsmParse.getFinalSuffix();
        String currentSurfaceForm = currentFsmParse.getSurfaceForm();
        for (int i = 0; i < currentState.transitionCount(); i++) {
            Transition currentTransition = currentState.getTransition(i);
            if (currentTransition.transitionPossible(currentFsmParse.getSurfaceForm(), surfaceForm) && currentTransition.transitionPossible(currentFsmParse) && (currentSurfaceForm.compareTo(root.getName()) != 0 || (currentSurfaceForm.compareTo(root.getName()) == 0 && currentTransition.transitionPossible(root, currentState)))) {
                String tmp = currentTransition.makeTransition(root, currentSurfaceForm, currentFsmParse.getStartState());
                if ((tmp.length() < surfaceForm.length() && isPossibleSubstring(tmp, surfaceForm, root)) || (tmp.length() == surfaceForm.length() && (root.lastIdropsDuringSuffixation() || (tmp.equalsIgnoreCase(surfaceForm))))) {
                    FsmParse newFsmParse = currentFsmParse.clone();
                    newFsmParse.addSuffix(currentTransition.toState(), tmp, currentTransition.with(), currentTransition.toString(), currentTransition.toPos());
                    newFsmParse.setAgreement(currentTransition.with());
                    fsmParse.add(newFsmParse);
                }
            }
        }
    }

    /**
     * The parseExists method is used to check the existence of the parse.
     *
     * @param fsmParse    an ArrayList of FsmParse
     * @param surfaceForm String to use during transition.
     * @return true when the currentState is end state and input surfaceForm id equal to currentSurfaceForm, otherwise false.
     */
    private boolean parseExists(ArrayList<FsmParse> fsmParse, String surfaceForm) {
        FsmParse currentFsmParse;
        TxtWord root;
        State currentState;
        String currentSurfaceForm;
        while (fsmParse.size() > 0) {
            currentFsmParse = fsmParse.remove(0);
            root = (TxtWord) currentFsmParse.getWord();
            currentState = currentFsmParse.getFinalSuffix();
            currentSurfaceForm = currentFsmParse.getSurfaceForm();
            if (currentState.isEndState() && currentSurfaceForm.compareTo(surfaceForm) == 0) {
                return true;
            }
            addNewParsesFromCurrentParse(currentFsmParse, fsmParse, surfaceForm, root);
        }
        return false;
    }

    /**
     * The parseWord method is used to parse a given fsmParse. It simply adds new parses to the current parse by
     * using addNewParsesFromCurrentParse method.
     *
     * @param fsmParse    an ArrayList of FsmParse
     * @param surfaceForm String to use during transition.
     * @return result {@link ArrayList} which has the currentFsmParse.
     */
    private ArrayList<FsmParse> parseWord(ArrayList<FsmParse> fsmParse, String surfaceForm) {
        ArrayList<FsmParse> result;
        FsmParse currentFsmParse;
        TxtWord root;
        State currentState;
        String currentSurfaceForm;
        int i;
        boolean exists;
        result = new ArrayList<>();
        while (fsmParse.size() > 0) {
            currentFsmParse = fsmParse.remove(0);
            root = (TxtWord) currentFsmParse.getWord();
            currentState = currentFsmParse.getFinalSuffix();
            currentSurfaceForm = currentFsmParse.getSurfaceForm();
            if (currentState.isEndState() && currentSurfaceForm.compareTo(surfaceForm) == 0) {
                exists = false;
                for (i = 0; i < result.size(); i++) {
                    if (currentFsmParse.suffixList().equalsIgnoreCase(result.get(i).suffixList())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    result.add(currentFsmParse);
                    currentFsmParse.constructInflectionalGroups();
                }
            }
            addNewParsesFromCurrentParse(currentFsmParse, fsmParse, surfaceForm, root);
        }
        return result;
    }

    /**
     * The morphologicalAnalysis with 3 inputs is used to initialize an {@link ArrayList} and add a new FsmParse
     * with given root and state.
     *
     * @param root        TxtWord input.
     * @param surfaceForm String input to use for parsing.
     * @param state       String input.
     * @return parseWord method with newly populated FsmParse ArrayList and input surfaceForm.
     */
    public ArrayList<FsmParse> morphologicalAnalysis(TxtWord root, String surfaceForm, String state) {
        ArrayList<FsmParse> initialFsmParse;
        initialFsmParse = new ArrayList<>();
        initialFsmParse.add(new FsmParse(root, getState(state)));
        return parseWord(initialFsmParse, surfaceForm);
    }

    /**
     * The morphologicalAnalysis with 2 inputs is used to initialize an {@link ArrayList} and add a new FsmParse
     * with given root. Then it calls initializeParseList method to initialize list with newly created ArrayList, input root,
     * and input surfaceForm.
     *
     * @param root        TxtWord input.
     * @param surfaceForm String input to use for parsing.
     * @return parseWord method with newly populated FsmParse ArrayList and input surfaceForm.
     */
    public ArrayList<FsmParse> morphologicalAnalysis(TxtWord root, String surfaceForm) {
        ArrayList<FsmParse> initialFsmParse;
        initialFsmParse = new ArrayList<>();
        initializeParseList(initialFsmParse, root, isProperNoun(surfaceForm));
        return parseWord(initialFsmParse, surfaceForm);
    }

    /**
     * The analysisExists method checks several cases. If the given surfaceForm is a punctuation or double then it
     * returns true. If it is not a root word, then it initializes the parse list and returns the parseExists method with
     * this newly initialized list and surfaceForm.
     *
     * @param rootWord    TxtWord root.
     * @param surfaceForm String input.
     * @param isProper    boolean variable indicates a word is proper or not.
     * @return true if surfaceForm is punctuation or double, otherwise returns parseExist method with given surfaceForm.
     */
    private boolean analysisExists(TxtWord rootWord, String surfaceForm, boolean isProper) {
        ArrayList<FsmParse> initialFsmParse;
        if (Word.isPunctuation(surfaceForm)) {
            return true;
        }
        if (isDouble(surfaceForm)) {
            return true;
        }
        if (rootWord != null) {
            initialFsmParse = new ArrayList<>();
            initializeParseList(initialFsmParse, rootWord, isProper);
        } else {
            initialFsmParse = initializeRootList(surfaceForm, isProper);
        }
        return parseExists(initialFsmParse, surfaceForm);
    }

    // TODO: 6/10/2018 after Fsmparse

    /**
     * The analysis method is used by the morphologicalAnalysis method. It gets String surfaceForm as an input and checks
     * its type such as punctuation, number or compares with the regex for date, fraction, percent, time, range, hashtag,
     * and mail or checks its variable type as integer or double. After finding the right case for given surfaceForm, it calls
     * constructInflectionalGroups method which creates sub-word units.
     *
     * @param surfaceForm String to analyse.
     * @param isProper    is used to indicate the proper words.
     * @return ArrayList type initialFsmParse which holds the analyses.
     */
    private ArrayList<FsmParse> analysis(String surfaceForm, boolean isProper) {
        ArrayList<FsmParse> initialFsmParse;
        FsmParse fsmParse;
        System.out.println();
        if (Word.isPunctuation(surfaceForm) && !surfaceForm.equals("%")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("Punctuation"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (isNumber(surfaceForm)) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("CardinalRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.matches("(\\d\\d|\\d)/(\\d\\d|\\d)/\\d+") || surfaceForm.matches("(\\d\\d|\\d)\\.(\\d\\d|\\d)\\.\\d+")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("DateRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.matches("\\d+/\\d+")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("FractionRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            fsmParse = new FsmParse(surfaceForm, new State(("DateRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.matches("\\d+\\\\/\\d+")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("FractionRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.equalsIgnoreCase("%") || surfaceForm.matches("%(\\d\\d|\\d)") || surfaceForm.matches("%(\\d\\d|\\d)\\.\\d+")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("PercentRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.matches("(\\d\\d|\\d):(\\d\\d|\\d):(\\d\\d|\\d)") || surfaceForm.matches("(\\d\\d|\\d):(\\d\\d|\\d)")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("TimeRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.matches("\\d+-\\d+") || surfaceForm.matches("(\\d\\d|\\d):(\\d\\d|\\d)-(\\d\\d|\\d):(\\d\\d|\\d)") || surfaceForm.matches("(\\d\\d|\\d)\\.(\\d\\d|\\d)-(\\d\\d|\\d)\\.(\\d\\d|\\d)")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("RangeRoot"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.startsWith("#")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("Hashtag"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.contains("@")) {
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(surfaceForm, new State(("Email"), true, true));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (surfaceForm.endsWith(".") && isInteger(surfaceForm.substring(0, surfaceForm.length() - 1))) {
            Integer.parseInt(surfaceForm.substring(0, surfaceForm.length() - 1));
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(Integer.parseInt(surfaceForm.substring(0, surfaceForm.length() - 1)), getState("OrdinalRoot"));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (isInteger(surfaceForm)) {
            Integer.parseInt(surfaceForm);
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(Integer.parseInt(surfaceForm), getState("CardinalRoot"));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        if (isDouble(surfaceForm)) {
            Double.parseDouble(surfaceForm);
            initialFsmParse = new ArrayList<>(1);
            fsmParse = new FsmParse(Double.parseDouble(surfaceForm), getState("RealRoot"));
            fsmParse.constructInflectionalGroups();
            initialFsmParse.add(fsmParse);
            return initialFsmParse;
        }
        initialFsmParse = initializeRootList(surfaceForm, isProper);
        return parseWord(initialFsmParse, surfaceForm);
    }

    /**
     * The isProperNoun method takes surfaceForm String as input and checks its each char whether they are in the range
     * of letters between A to Z or one of the Turkish letters such as İ, Ü, Ğ, Ş, Ç, and Ö.
     *
     * @param surfaceForm
     * @return false if surfaceForm is null or length of 0, return true if it is a letter.
     */
    public boolean isProperNoun(String surfaceForm) {
        if (surfaceForm == null || surfaceForm.length() == 0) {
            return false;
        }
        return (surfaceForm.charAt(0) >= 'A' && surfaceForm.charAt(0) <= 'Z') || (surfaceForm.charAt(0) == '\u0130') || (surfaceForm.charAt(0) == '\u00dc') || (surfaceForm.charAt(0) == '\u011e') || (surfaceForm.charAt(0) == '\u015e') || (surfaceForm.charAt(0) == '\u00c7') || (surfaceForm.charAt(0) == '\u00d6'); // İ, Ü, Ğ, Ş, Ç, Ö
    }

    /**
     * The robustMorphologicalAnalysis is used to analyse surfaceForm String. First it gets the currentParse of the surfaceForm
     * then, if the size of the currentParse is 0, and given surfaceForm is a proper noun, it adds the surfaceForm
     * whose state name is ProperRoot to an {@link ArrayList}, of it is not a proper noon, it adds the surfaceForm
     * whose state name is NominalRoot to the {@link ArrayList}.
     *
     * @param surfaceForm String to analyse.
     * @return FsmParseList type currentParse which holds morphological analysis of the surfaceForm.
     */
    public FsmParseList robustMorphologicalAnalysis(String surfaceForm) {
        ArrayList<FsmParse> fsmParse;
        FsmParseList currentParse;
        if (surfaceForm == null || surfaceForm.isEmpty()) {
            return new FsmParseList(new ArrayList<>());
        }
        currentParse = morphologicalAnalysis(surfaceForm);
        if (currentParse.size() == 0) {
            fsmParse = new ArrayList<>(1);
            if (isProperNoun(surfaceForm)) {
                fsmParse.add(new FsmParse(surfaceForm, getState("ProperRoot")));
                return new FsmParseList(parseWord(fsmParse, surfaceForm));
            } else {
                fsmParse.add(new FsmParse(surfaceForm, getState("NominalRoot")));
                return new FsmParseList(parseWord(fsmParse, surfaceForm));
            }
        } else {
            return currentParse;
        }
    }

    /**
     * The morphologicalAnalysis is used for debug purposes.
     *
     * @param sentence  to get word from.
     * @param debugMode states whether in the debug mode.
     * @return FsmParseList type result.
     */
    public FsmParseList[] morphologicalAnalysis(Sentence sentence, boolean debugMode) {
        FsmParseList wordFsmParseList;
        FsmParseList[] result = new FsmParseList[sentence.wordCount()];
        for (int i = 0; i < sentence.wordCount(); i++) {
            wordFsmParseList = morphologicalAnalysis(sentence.getWord(i).getName());
            if (wordFsmParseList.size() == 0 && debugMode) {
                System.out.println("Word " + sentence.getWord(i).getName() + " can not be parsed\n");
            }
            result[i] = wordFsmParseList;
        }
        return result;
    }

    // TODO: 6/11/2018 never used method.

    /**
     * The robustMorphologicalAnalysis method takes just one argument as an input. It gets the name of the words from
     * input sentence then calls robustMorphologicalAnalysis with surfaceForm.
     *
     * @param sentence Sentence type input used to get surfaceForm.
     * @return FsmParseList array which holds the result of the analysis.
     */
    public FsmParseList[] robustMorphologicalAnalysis(Sentence sentence) {
        FsmParseList fsmParseList;
        FsmParseList[] result = new FsmParseList[sentence.wordCount()];
        for (int i = 0; i < sentence.wordCount(); i++) {
            fsmParseList = robustMorphologicalAnalysis(sentence.getWord(i).getName());
            result[i] = fsmParseList;
        }
        return result;
    }

    /**
     * The isInteger method compares input surfaceForm with regex \+?\d+ and returns the result.
     *
     * @param surfaceForm String to check.
     * @return true if surfaceForm matches with the regex.
     */
    private boolean isInteger(String surfaceForm) {
        return surfaceForm.matches("\\+?\\d+") && surfaceForm.length() < 11;
    }

    /**
     * The isDouble method compares input surfaceForm with regex \+?(\d+)?\.\d* and returns the result.
     *
     * @param surfaceForm String to check.
     * @return true if surfaceForm matches with the regex.
     */
    private boolean isDouble(String surfaceForm) {
        return surfaceForm.matches("\\+?(\\d+)?\\.\\d*");
    }

    /**
     * The isNumber method compares input surfaceForm with the array of written numbers and returns the result.
     *
     * @param surfaceForm String to check.
     * @return true if surfaceForm matches with the regex.
     */
    private boolean isNumber(String surfaceForm) {
        boolean found;
        int count = 0;
        String[] numbers = {"bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz",
                "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan",
                "yüz", "bin", "milyon", "milyar", "trilyon", "katrilyon"};
        String word = surfaceForm;
        while (!word.isEmpty()) {
            found = false;
            for (String number : numbers) {
                if (word.startsWith(number)) {
                    found = true;
                    count++;
                    word = word.substring(number.length());
                    break;
                }
            }
            if (!found) {
                break;
            }
        }
        if (word.isEmpty() && count > 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * The morphologicalAnalysis method is used to analyse a FsmParseList by comparing with the regex.
     * It creates an {@link ArrayList} fsmParse to hold the result of the analysis method. For each surfaceForm input,
     * it gets a substring and considers it as a possibleRoot. Then compares with the regex.
     * <p>
     * If the surfaceForm input string matches with Turkish chars like Ç, Ş, İ, Ü, Ö, it adds the surfaceForm to Trie with IS_OA tag.
     * If the possibleRoot contains /, then it is added to the Trie with IS_KESIR tag.
     * If the possibleRoot contains \d\d|\d)/(\d\d|\d)/\d+, then it is added to the Trie with IS_DATE tag.
     * If the possibleRoot contains \\d\d|\d, then it is added to the Trie with IS_PERCENT tag.
     * If the possibleRoot contains \d\d|\d):(\d\d|\d):(\d\d|\d), then it is added to the Trie with IS_ZAMAN tag.
     * If the possibleRoot contains \d+-\d+, then it is added to the Trie with IS_RANGE tag.
     * If the possibleRoot is an Integer, then it is added to the Trie with IS_SAYI tag.
     * If the possibleRoot is a Double, then it is added to the Trie with IS_REELSAYI tag.
     *
     * @param surfaceForm String to analyse.
     * @return fsmParseList which holds the analysis.
     */
    public FsmParseList morphologicalAnalysis(String surfaceForm) {
        FsmParseList fsmParseList;
        if (cache != null && cache.contains(surfaceForm)) {
            return cache.get(surfaceForm);
        }
        if (surfaceForm.matches("(\\w|Ç|Ş|İ|Ü|Ö)\\.")) {
            dictionaryTrie.addWord(surfaceForm.toLowerCase(new Locale("tr")), new TxtWord(surfaceForm.toLowerCase(new Locale("tr")), "IS_OA"));
        }
        ArrayList<FsmParse> defaultFsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
        if (defaultFsmParse.size() > 0) {
            fsmParseList = new FsmParseList(defaultFsmParse);
            if (cache != null) {
                cache.add(surfaceForm, fsmParseList);
            }
            return fsmParseList;
        }
        // TODO: 6/11/2018 what is OA and why there are multiple tags like kesir and date/zaman.
        ArrayList<FsmParse> fsmParse = new ArrayList<>();
        if (surfaceForm.contains("'")) {
            String possibleRoot = surfaceForm.substring(0, surfaceForm.indexOf('\''));
            if (!possibleRoot.isEmpty()) {
                if (possibleRoot.contains("/") || possibleRoot.contains("\\/")) {
                    dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_KESIR"));
                    fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                } else {
                    if (possibleRoot.matches("(\\d\\d|\\d)/(\\d\\d|\\d)/\\d+") || possibleRoot.matches("(\\d\\d|\\d)\\.(\\d\\d|\\d)\\.\\d+")) {
                        dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_DATE"));
                        fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                    } else {
                        if (possibleRoot.matches("\\d+/\\d+")) {
                            dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_KESIR"));
                            fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                        } else {
                            if (possibleRoot.matches("%(\\d\\d|\\d)") || possibleRoot.matches("%(\\d\\d|\\d)\\.\\d+")) {
                                dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_PERCENT"));
                                fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                            } else {
                                if (possibleRoot.matches("(\\d\\d|\\d):(\\d\\d|\\d):(\\d\\d|\\d)") || possibleRoot.matches("(\\d\\d|\\d):(\\d\\d|\\d)")) {
                                    dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_ZAMAN"));
                                    fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                                } else {
                                    if (possibleRoot.matches("\\d+-\\d+") || possibleRoot.matches("(\\d\\d|\\d):(\\d\\d|\\d)-(\\d\\d|\\d):(\\d\\d|\\d)") || possibleRoot.matches("(\\d\\d|\\d)\\.(\\d\\d|\\d)-(\\d\\d|\\d)\\.(\\d\\d|\\d)")) {
                                        dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_RANGE"));
                                        fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                                    } else {
                                        if (isInteger(possibleRoot)) {
                                            Integer.parseInt(possibleRoot);
                                            dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_SAYI"));
                                            fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                                        } else {
                                            if (isDouble(possibleRoot)) {
                                                Double.parseDouble(possibleRoot);
                                                dictionaryTrie.addWord(possibleRoot, new TxtWord(possibleRoot, "IS_REELSAYI"));
                                                fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                                            } else {
                                                if (Word.isCapital(possibleRoot)) {
                                                    TxtWord newWord = null;
                                                    if (dictionary.getWord(possibleRoot.toLowerCase(new Locale("tr"))) != null) {
                                                        ((TxtWord) dictionary.getWord(possibleRoot.toLowerCase(new Locale("tr")))).addFlag("IS_OA");
                                                    } else {
                                                        newWord = new TxtWord(possibleRoot.toLowerCase(new Locale("tr")), "IS_OA");
                                                        dictionaryTrie.addWord(possibleRoot.toLowerCase(new Locale("tr")), newWord);
                                                    }
                                                    fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                                                    if (fsmParse.size() == 0 && newWord != null) {
                                                        newWord.addFlag("IS_KIS");
                                                        fsmParse = analysis(surfaceForm.toLowerCase(new Locale("tr")), isProperNoun(surfaceForm));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        fsmParseList = new FsmParseList(fsmParse);
        if (cache != null) {
            cache.add(surfaceForm, fsmParseList);
        }
        return fsmParseList;
    }

    /**
     * The morphologicalAnalysisExists method calls analysisExists to check the existence of the analysis with given
     * root and surfaceForm.
     *
     * @param surfaceForm String to check.
     * @param rootWord    TxtWord input root.
     * @return true an analysis exists, otherwise return false.
     */
    public boolean morphologicalAnalysisExists(TxtWord rootWord, String surfaceForm) {
        return analysisExists(rootWord, surfaceForm.toLowerCase(new Locale("tr")), true);
    }

}