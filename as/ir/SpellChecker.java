/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.*;

import java.lang.Math;

//import sun.nio.cs.HistoricallyNamedCharset;

public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    /**
     * The auxiliary class for containing the value of your ranking function for a
     * token
     */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
           
            return Double.compare(((KGramStat) other).score, this.score);
        }

        public String toString() {
            return token + ";" + score;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling correction should
     * pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.3;

    /**
     * The threshold for edit distance for a candidate spelling correction to be
     * accepted.
     */
    private static final int MAX_EDIT_DISTANCE = 2;

    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     * Computes the Jaccard coefficient for two sets A and B, where the size of set
     * A is <code>szA</code>, the size of set B is <code>szB</code> and the
     * intersection of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        double jaccard = Double.valueOf(intersection) / Double.valueOf(szA + szB + intersection);
        return jaccard;
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming. Allowed
     * operations are: => insert (cost 1) => delete (cost 1) => substitute (cost 2)
     */
    private int Minimum(int a, int b, int c) {
        int mi;

        mi = a;
        if (b < mi) {
            mi = b;
        }
        if (c < mi) {
            mi = c;
        }
        return mi;

    }

    private int editDistance(String s1, String s2) {
        int d[][]; // matrix
        int n; // length of s
        int m; // length of t
        int i; // iterates through s
        int j; // iterates through t
        char s1_i; // ith character of s
        char s2_j; // jth character of t
        int cost; // cost

        n = s1.length();
        m = s2.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        d = new int[n + 1][m + 1];
        for (i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (i = 1; i <= n; i++) {
            s1_i = s1.charAt(i - 1);
            for (j = 1; j <= m; j++) {
                s2_j = s2.charAt(j - 1);
                if (s1_i == s2_j) {
                    cost = 0;
                } else {
                    cost = 2;
                }
                d[i][j] = Minimum(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);

            }
        }
        return d[n][m];
    }

    /**
     * Checks spelling of all terms in <code>query</code> and returns up to
     * <code>limit</code> ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {
        HashMap<String, KGramStat> correctedTerms = new HashMap<String, KGramStat>();
        for (int i = 0; i < query.size(); i++) {
            String token = query.queryterm.get(i).term;
            if (index.getPostings(token) == null) {
                correctedTerms = getCorrectedTerms(token);
            }
        }

        int resultSize = correctedTerms.size();
        ArrayList<KGramStat> resultList = new ArrayList<KGramStat>();
        for(Map.Entry<String, KGramStat> entry : correctedTerms.entrySet()){
            resultList.add(entry.getValue());
        }
        Collections.sort(resultList);

        String[] result= new String[resultSize];
        for(int i=0;i<resultSize;i++){
            result[i] = resultList.get(i).token;
        }

        return result;
    }

    public HashMap getCorrectedTerms(String token) {
        int kgramNum = token.length() + 1 - kgIndex.getK();
        String kgram;
        List<KGramPostingsEntry> postings = null;
        HashMap<String, KGramStat> correctedTerms = new HashMap<String, KGramStat>();

        ArrayList<String> kgrams_q = new ArrayList<String>();
        kgrams_q = kgIndex.getKgrams(token);
        for (int i = 0; i < kgramNum; i++) {
            kgram = token.substring(i, i + kgIndex.getK());
            //System.err.println(kgram);

            if (postings == null) {
                postings =kgIndex.getPostings(kgram);
                generateTermsMap(token, correctedTerms, postings, kgrams_q);
            } else {
                postings = kgIndex.getPostings(kgram);
                generateTermsMap(token, correctedTerms, postings, kgrams_q);
            }
        }
        System.err.println("correctedTerms size: "+correctedTerms.size());

        return correctedTerms;
    }

    HashMap<String, Boolean> hodingTerms = new HashMap<String, Boolean>();
    public void generateTermsMap(String token, HashMap<String, KGramStat> correctedTerms,
            List<KGramPostingsEntry> postings, ArrayList<String> kgrams_q) {
        //HashMap<String, KGramStat> results = new HashMap<String, List<KGramStat>>();
        //results = correctedTerms;
        String term ;
        for (int i = 0; i < postings.size(); i++) {
            int szA = kgrams_q.size();
            term = kgIndex.getTermByID(postings.get(i).tokenID);
            if (!hodingTerms.containsKey(term)) {
                ArrayList<String> kgrams_t = new ArrayList<String>();
                kgrams_t = kgIndex.getKgrams(term);
                int szB = kgrams_t.size();
                boolean ret = kgrams_t.containsAll(kgrams_q);
                if (kgrams_t.size() >= 2) {
                    double jaccard = jaccard(szA, szB, kgrams_t.size());
                    if (jaccard >= JACCARD_THRESHOLD) {
                        int editDistance = editDistance(token, term);
                        if (editDistance <= MAX_EDIT_DISTANCE) {
                            double score = index.getPostings(term).size();
                            KGramStat a = new KGramStat(term,score);
                            correctedTerms.put(term, a);
                        }
                    }
                }
            }
            //System.err.println("hh: "+ hh);
            hodingTerms.put(term, true);
        }
        //return results;
    }

    /**
     * Merging ranked candidate spelling corrections for all query terms available
     * in <code>qCorrections</code> into one final merging of query phrases. Returns
     * up to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {
        //
        // YOUR CODE HERE
        //
        return null;
    }
}
