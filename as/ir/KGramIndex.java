/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.io.*;
import java.util.regex.Pattern;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class KGramIndex {

    /** Mapping from term ids to actual term strings */
    HashMap<Integer, String> id2term = new HashMap<Integer, String>();

    /** Mapping from term strings to term ids */
    HashMap<String, Integer> term2id = new HashMap<String, Integer>();

    /** Index from k-grams to list of term ids that contain the k-gram */
    HashMap<String, List<KGramPostingsEntry>> index = new HashMap<String, List<KGramPostingsEntry>>();

    /** The ID of the last processed term */
    int lastTermID = -1;

    /** Number of symbols to form a K-gram */
    int K = 2;

    public KGramIndex(int k) {
        K = k;
        if (k <= 0) {
            System.err.println("The K-gram index can't be constructed for a negative K value");
            System.exit(1);
        }
    }

    /** Generate the ID for an unknown term */
    private int generateTermID() {
        return ++lastTermID;
    }

    public int getK() {
        return K;
    }

    /**
     * Get intersection of two postings lists
     */
    public List<KGramPostingsEntry> intersect(List<KGramPostingsEntry> p1, List<KGramPostingsEntry> p2) {
        List<KGramPostingsEntry> result = new ArrayList<KGramPostingsEntry>();
        int m = 0;
        int n = 0;
        if (p2 == null) {
            for (int i = 0; i < p1.size(); i++) {
                result.add(p1.get(i));
            }

        } else {
            while (m < p1.size() && n < p2.size()) {
                if (p1.get(m).tokenID < p2.get(n).tokenID) {
                    m++;
                } else if (p1.get(m).tokenID == p2.get(n).tokenID && !result.contains(p1.get(m))) {
                    result.add(p1.get(m));
                    m++;
                    n++;
                } else {
                    n++;
                }
            }
        }

        for (int i = 0; i < result.size() - 1; i++) {
            if (result.get(i).tokenID == result.get(i + 1).tokenID) {
                result.remove(i);
                i--;
            }
        }

        return result;
    }

    /** Inserts all k-grams from a token into the index. */
    public void insert(String token) {
        // Whether this token is already indexed or not
        if (getIDByTerm(token) != null) {
            return;
        }
        int newid = generateTermID();
        term2id.put(token, newid);
        id2term.put(newid, token);
        int kgramNum = token.length() + 3 - getK();
        KGramPostingsEntry a = new KGramPostingsEntry(newid);

        String kgrams;
        String newToken = "^" + token + "$";
        for (int i = 0; i < kgramNum; i++) {
            kgrams = newToken.substring(i, i + getK());

            if (!index.containsKey(kgrams)) {
                index.put(kgrams, new ArrayList<KGramPostingsEntry>());
            }
            // deduplication
            if (!index.get(kgrams).contains(a)) {
                index.get(kgrams).add(a);
            }

        }

    }

    /** Get postings for the given k-gram */
    public List<KGramPostingsEntry> getPostings(String kgram) {
        if (index.containsKey(kgram)) {
            return index.get(kgram);
        } else {
            return null;
        }
    }

    /** Get id of a term */
    public Integer getIDByTerm(String term) {
        return term2id.get(term);
    }

    /** Get a term by the given id */
    public String getTermByID(Integer id) {
        return id2term.get(id);
    }

    /** Processing Wildcard Queries */
    public Query getWordofWildcard(String token) {
        Query query = new Query();
        if (token.contains("*")) {

            List<KGramPostingsEntry> postings = null;
            String newToken = "^" + token + "$";
            int kgramNum = token.length() + 3 - getK();
            String kgram;

            for (int i = 0; i < kgramNum; i++) {
                kgram = newToken.substring(i, i + getK());
                //System.err.println(kgram);

                if (postings == null) {
                    postings = getPostings(kgram);
                } else {
                    postings = intersect(postings, getPostings(kgram));
                }
            }

            String regexToken = token.replace("*", ".*");
            for (int i = 0; i < postings.size(); i++) {
                String term = getTermByID(postings.get(i).tokenID);

                if (Pattern.matches(regexToken, term)) {
                    query.addTerm(term);
                }
            }
        }
        else{
            query.addTerm(token);
        }
        return query;
    }

    private static HashMap<String, String> decodeArgs(String[] args) {
        HashMap<String, String> decodedArgs = new HashMap<String, String>();
        int i = 0, j = 0;
        while (i < args.length) {
            if ("-p".equals(args[i])) {
                i++;
                if (i < args.length) {
                    decodedArgs.put("patterns_file", args[i++]);
                }
            } else if ("-f".equals(args[i])) {
                i++;
                if (i < args.length) {
                    decodedArgs.put("file", args[i++]);
                }
            } else if ("-k".equals(args[i])) {
                i++;
                if (i < args.length) {
                    decodedArgs.put("k", args[i++]);
                }
            } else if ("-kg".equals(args[i])) {
                i++;
                if (i < args.length) {
                    decodedArgs.put("kgram", args[i++]);
                }
            } else {
                System.err.println("Unknown option: " + args[i]);
                break;
            }
        }
        return decodedArgs;
    }

    public static void main(String[] arguments) throws FileNotFoundException, IOException {
        HashMap<String, String> args = decodeArgs(arguments);

        int k = Integer.parseInt(args.getOrDefault("k", "3"));
        KGramIndex kgIndex = new KGramIndex(k);

        File f = new File(args.get("file"));
        Reader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
        Tokenizer tok = new Tokenizer(reader, true, false, true, args.get("patterns_file"));
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            kgIndex.insert(token);
            // System.err.println(token);
        }

        String[] kgrams = args.get("kgram").split(" ");
        List<KGramPostingsEntry> postings = null;
        for (String kgram : kgrams) {
            if (kgram.length() != k) {
                System.err.println(
                        "Cannot search k-gram index: " + kgram.length() + "-gram provided instead of " + k + "-gram");
                System.exit(1);
            }

            if (postings == null) {
                postings = kgIndex.getPostings(kgram);
            } else {
                postings = kgIndex.intersect(postings, kgIndex.getPostings(kgram));
            }
        }
        if (postings == null) {
            System.err.println("Found 0 posting(s)");
        } else {
            int resNum = postings.size();
            System.err.println("Found " + resNum + " posting(s)");
            // if (resNum > 10) {
            // System.err.println("The first 10 of them are:");
            // resNum = 10;
            // }
            for (int i = 0; i < resNum; i++) {
                System.err.println(kgIndex.getTermByID(postings.get(i).tokenID));
            }
        }
    }
}
