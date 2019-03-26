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

    // Searcher searcher=new Searcher(index, kgIndex);
    Searcher searcher;

    QueryType queryType = QueryType.INTERSECTION_QUERY;

    RankingType rankingType = RankingType.TF_IDF;

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
    private static final int MAX_EDIT_DISTANCE = 6;

    public SpellChecker(Index index, KGramIndex kgIndex, Searcher searcher) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.searcher = searcher;
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
        List<List<KGramStat>> qCorrections = new ArrayList<List<KGramStat>>();

        for (int i = 0; i < query.size(); i++) {
            HashMap<String, KGramStat> correctedTerms = new HashMap<String, KGramStat>();
            List<KGramStat> resultList = new ArrayList<KGramStat>();
            String token = query.queryterm.get(i).term;
            if (index.getPostings(token) == null) {
                correctedTerms = getCorrectedTerms(token);
                for (Map.Entry<String, KGramStat> entry : correctedTerms.entrySet()) {
                    resultList.add(entry.getValue());
                }
                Collections.sort(resultList);
                qCorrections.add(resultList);
            } else {
                resultList.add(new KGramStat(token, 1.0));
                qCorrections.add(resultList);
            }
        }

        List<KGramStat> suggestions = new ArrayList<KGramStat>();
        suggestions = mergeCorrections(qCorrections, limit);
        String[] result = new String[suggestions.size()];
        for (int i = 0; i < suggestions.size(); i++) {
            result[i] = suggestions.get(i).token;
        }

        hodingTerms.clear();
        return result;
    }

    public HashMap getCorrectedTerms(String token) {
        int kgramNum = token.length() + 1 - kgIndex.getK();
        String kgram;

        HashMap<String, KGramStat> correctedTerms = new HashMap<String, KGramStat>();

        ArrayList<String> kgrams_q = new ArrayList<String>();
        kgrams_q = kgIndex.getKgrams(token);
        for (int i = 0; i < kgramNum; i++) {
            kgram = token.substring(i, i + kgIndex.getK());
            List<KGramPostingsEntry> postings = new ArrayList<KGramPostingsEntry>();

            postings = kgIndex.getPostings(kgram);
            generateTermsMap(token, correctedTerms, postings, kgrams_q);

        }
        System.err.println("correctedTerms size: " + correctedTerms.size());

        return correctedTerms;
    }

    HashMap<String, Boolean> hodingTerms = new HashMap<String, Boolean>();

    public void generateTermsMap(String token, HashMap<String, KGramStat> correctedTerms,
            List<KGramPostingsEntry> postings, ArrayList<String> kgrams_q) {

        String term;
        for (int i = 0; i < postings.size(); i++) {
            int szA = kgrams_q.size();
            term = kgIndex.getTermByID(postings.get(i).tokenID);
            if (!hodingTerms.containsKey(term)) {
                ArrayList<String> kgrams_t = new ArrayList<String>();
                kgrams_t = kgIndex.getKgrams(term);
                int szB = kgrams_t.size();
                boolean ret = kgrams_t.containsAll(kgrams_q);
                //change JACCARD_THRESHOLD and MAX_EDIT_DISTANCE to get more suggestions 
                if (kgrams_t.size() >= 1) {
                    double jaccard = jaccard(szA, szB, kgrams_t.size());
                    if (jaccard >= JACCARD_THRESHOLD) {
                        int editDistance = editDistance(token, term);
                        if (editDistance <= MAX_EDIT_DISTANCE) {
                            double score = index.getPostings(term).size();
                            KGramStat a = new KGramStat(term, score);
                            correctedTerms.put(term, a);
                        }
                    }
                }
            }
            // System.err.println("hh: "+ hh);
            hodingTerms.put(term, true);
        }
        // return results;
    }

    /**
     * Merging ranked candidate spelling corrections for all query terms available
     * in <code>qCorrections</code> into one final merging of query phrases. Returns
     * up to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {
        // List<KGramStat> resultList = new ArrayList<KGramStat>();
        List<KGramStat> query = new ArrayList<KGramStat>();

        for (int i = 0; i < qCorrections.size(); i++) {
            int candidateSize = qCorrections.get(i).size();

            // initialize
            if (i == 0) {
                if (candidateSize > 1) {
                    if (candidateSize < limit) {
                        for (int j = 0; j < candidateSize; j++) {
                            String candidateToken = qCorrections.get(i).get(j).token;
                            query.add(new KGramStat(candidateToken, 1.0));
                        }
                    } else {
                        for (int j = 0; j < limit; j++) {
                            String candidateToken = qCorrections.get(i).get(j).token;
                            query.add(new KGramStat(candidateToken, 1.0));
                        }
                    }
                } else {
                    String candidateToken = qCorrections.get(i).get(0).token;

                    query.add(new KGramStat(candidateToken, 1.0));
                }
            } else {
                if (candidateSize > 1) {
                    List<KGramStat> currentList = new ArrayList<KGramStat>();
                    for (int j = 0; j < candidateSize; j++) {
                        String candidateToken = qCorrections.get(i).get(j).token;
                        for (int m = 0; m < query.size(); m++) {
                            String query_candidate = query.get(m).token + " " + candidateToken;
                            // System.err.println("query_candidate: "+query_candidate);
                            PostingsList searchR = new PostingsList();
                            Query _query = new Query(query_candidate);
                            searchR = searcher.search(_query, queryType, rankingType);
                            // System.err.println("score: "+searchR.size());
                            // set score;
                            currentList.add(new KGramStat(query_candidate, searchR.size()));
                        }
                    }
                    Collections.sort(currentList);
                    // System.err.println(currentList.size());
                    if (currentList.size() < limit) {
                        query.clear();
                        for (int n = 0; n < currentList.size(); n++) {
                            // add is ok here
                            query.add(currentList.get(n));
                        }
                    } else {
                        query.clear();
                        for (int n = 0; n < limit; n++) {
                            query.add(currentList.get(n));
                        }
                    }

                } else {
                    for (int m = 0; m < query.size(); m++) {
                        String candidateToken = qCorrections.get(i).get(0).token;
                        // System.err.println(candidateToken);
                        String query_candidate = query.get(m).token + " " + candidateToken;
                        query.set(m, new KGramStat(query_candidate, 1.0));
                    }
                    if (i == qCorrections.size() - 1) {
                        List<KGramStat> currentList = new ArrayList<KGramStat>();
                        for (int m = 0; m < query.size(); m++) {
                            String query_candidate = query.get(m).token;
                            PostingsList searchR = new PostingsList();
                            Query _query = new Query(query_candidate);
                            searchR = searcher.search(_query, queryType, rankingType);
                            // set score;
                            currentList.add(new KGramStat(query_candidate, searchR.size()));
                        }
                        Collections.sort(currentList);
                        if (currentList.size() < limit) {
                            query.clear();
                            for (int n = 0; n < currentList.size(); n++) {
                                // add is ok here
                                query.add(currentList.get(n));
                            }
                        } else {
                            query.clear();
                            for (int n = 0; n < limit; n++) {
                                query.add(currentList.get(n));
                            }
                        }
                    }
                }
            }

        }

        return query;
    }
}
