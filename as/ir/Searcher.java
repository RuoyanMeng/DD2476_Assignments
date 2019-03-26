/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */

package ir;

//import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory.Default;

import java.util.*;
import java.util.Collections.*;
import java.io.*;

/**
 * Searches an index for results of a query.
 */
public class Searcher {

    /**
     * The index to be searched by this Searcher.
     */
    Index index;

    /**
     * The k-gram index to be searched by this Searcher
     */
    KGramIndex kgIndex;

    /**
     * Constructor
     */
    public Searcher(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    int MAX_NUMBER_OF_DOCS = 2000000;
    double w1 = 0.007;

    /**
     * Searches the index for postings matching the query.
     *
     * @return A postings list representing the result of the query.
     */
    public PostingsList search(Query query, QueryType queryType, RankingType rankingType) {
        PostingsList result = new PostingsList();

        if (query.size() != 0) {
            for (int i = 0; i < query.size(); i++) {
                String token = query.queryterm.get(i).term;
                if (index.getPostings(token) == null && !token.contains("*")) {
                    return null;
                }
            }
        }

        // INTERSECTION_QUERY
        if (queryType == QueryType.INTERSECTION_QUERY) {
            HashMap<Integer, ArrayList<Integer>> queriesList = new HashMap<Integer, ArrayList<Integer>>();
            PostingsList list = new PostingsList();
            if (query.size() != 0) {
                for (int i = 0; i < query.size(); i++) {

                    String token = query.queryterm.get(i).term;
                    // System.err.println(token);
                    // System.err.println(index.getPostings(token));

                    HashMap<Integer, ArrayList<Integer>> listMapIntersect = new HashMap<Integer, ArrayList<Integer>>();
                    listMapIntersect = unionPostinglist(token);

                    if (queriesList.size() == 0) {
                        queriesList = listMapIntersect;
                        if (queriesList.size() == 0) {
                            queriesList = list.intersect(queriesList, listMapIntersect);
                        }
                    } else {
                        queriesList = list.intersect(queriesList, listMapIntersect);
                    }
                }
                for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                    list.addElements(entry.getKey(), 1, 1.0);
                }
            }
            queriesList.clear();
            list.deduplication();
            result = list;

        } else if (queryType == QueryType.PHRASE_QUERY) {
            PostingsList list = new PostingsList();
            int count = 0;

            // rewrite phase_query
            HashMap<Integer, ArrayList<Integer>> queriesList = new HashMap<Integer, ArrayList<Integer>>();
            for (int i = 0; i < query.size(); i++) {
                String token = query.queryterm.get(i).term;
                // PostingsList listPhrase = new PostingsList();
                HashMap<Integer, ArrayList<Integer>> listMapPhrase = new HashMap<Integer, ArrayList<Integer>>();
                listMapPhrase = unionPostinglist(token);

                if (queriesList.size() == 0 && count == 0) {
                    queriesList = listMapPhrase;
                    // System.err.println("00:"+queriesList.size());
                } else {
                    count++;
                    System.err.println(count);
                    System.err.println("before"+queriesList.size());
                    queriesList = list.phaseMapIntersect(queriesList, listMapPhrase, count);
                    System.err.println("after"+queriesList.size());
                }

                // listMapPhrase.clear();
            }
            for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                list.addElements(entry.getKey(), 1, 1.0);
            }
            queriesList.clear();
            list.deduplication();
            result = list;
        } else if (queryType == QueryType.RANKED_QUERY) {
            readPagerank("./PagerankScore.txt");
            PostingsList _list = new PostingsList();

            // processing Wildcard Queries
            // union the search results of each term
            HashMap<Integer, ArrayList<Integer>> queriesList = new HashMap<Integer, ArrayList<Integer>>();
            Query _query = new Query();
            for (int i = 0; i < query.size(); i++) {
                String token = query.queryterm.get(i).term;
                HashMap<Integer, ArrayList<Integer>> listMapIntersect = new HashMap<Integer, ArrayList<Integer>>();
                listMapIntersect = unionPostinglist(token);
                //System.err.println("listMapIntersect: " + listMapIntersect.size());

                if (token.contains("*")) {
                    Query queries = new Query();
                    queries = kgIndex.getWordofWildcard(token);
                    _query.addQueries(_query, queries);
                } else {
                    _query.addTerm(token);
                }

                if (queriesList.size() == 0) {
                    queriesList = listMapIntersect;
                } else {
                    //System.err.println("queriesList: " + queriesList.size());
                    queriesList = _list.union(queriesList, listMapIntersect);
                    //System.err.println("queriesList: " + queriesList.size());
                }
                //listMapIntersect.clear();
            }
            // for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
            //     _list.addElements(entry.getKey(), 1, 1.0);
            // }
            // queriesList.clear();
            query = _query;
            // _list.deduplication();
            //System.err.println(_list.size());

            // switch between different ranking types
            if (rankingType == RankingType.PAGERANK) {
                if (query.size() != 0) {
                    for (int i = 0; i < _list.size(); i++) {
                        String filename = Index.docNames.get(_list.get(i).docID);
                        filename = filename.substring(filename.lastIndexOf("/") + 1);
                        // System.err.println(filename);
                        double score = pageRank.get(filename);
                        _list.get(i).setScore(score);
                    }
                    _list.sortScore();
                }
                _list.deduplication();
                result = _list;
            } else if (rankingType == RankingType.HITS) {

                HITSRanker hr = new HITSRanker("pagerank/linksDavis.txt", "pagerank/davisTitles.txt", null);
                // hr.rank();
                _list = hr.rank(_list);
                _list.sortScore();
                result = _list;
                // result.deduplication();
            }
            // tf_idf and combination
            else {

                for(Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()){
                    HashMap<String, Integer> termFreq = new HashMap<String, Integer>();
                    termFreq = Index.termFreq.get(entry.getKey());
                    Double score = 0.0;
                    for(int i=0;i<query.size();i++){
                        String token = query.queryterm.get(i).term;
                        if(termFreq.containsKey(token)){
                            double idf = idf(token);
                            idf = idf * query.queryterm.get(i).weight;
                            double tf_idf = idf * termFreq.get(token)/Index.docLengths.get(entry.getKey());
                            score = score+tf_idf;
                        }
                    }
                    _list.addElements(entry.getKey(), 1, score);
                }


                if (rankingType == RankingType.TF_IDF) {
                    _list.deduplication();
                    result = _list;
                } else if (rankingType == RankingType.COMBINATION) {
                    if (query.size() != 0) {
                        for (int i = 0; i < _list.size(); i++) {
                            String filename = Index.docNames.get(_list.get(i).docID);
                            filename = filename.substring(filename.lastIndexOf("/") + 1);
                            // here
                            double score = w1 * pageRank.get(filename) + (1 - w1) * (_list.get(i).score);
                            _list.get(i).setScore(score);
                        }
                        _list.sortScore();
                    }
                    result = _list;
                }

            }

        }
        return result;
    }

    public double idf(String token) {
        double idf;
        int df = Index.docFreq.get(token);
        idf = Math.log10(Index.docLengths.size() / df);
        return idf;
    }

    public void tf_idf_score(Hashtable<Integer, HashMap<Integer,Double>> tf, PostingsList _list) {
        for (int i = 0; i < _list.size(); i++) {
            // System.out.println("list:"+_list.get(i).docID);
            double score = 0.0;
            HashMap<Integer,Double> _tf_idf = tf.get(_list.get(i).docID);
            for (int n = 0; n < _tf_idf.size(); n++) {
                score = score + _tf_idf.get(n) / Index.docLengths.get(_list.get(i).docID);

            }
            _list.get(i).setScore(score);
            // System.err.println(_list.get(i).score);
            //
        }

    }

    // public PostingsList unionPostinglist(Query query,QueryType queryType) {
    // PostingsList result = new PostingsList();
    // // System.err.println("query size: " + query.size());
    // if (query.size() != 0) {
    // for (int j = 0; j < query.size(); j++) {
    // String term = query.queryterm.get(j).term;
    // PostingsList listUnion = new PostingsList();
    // // System.err.println(term);
    // if (result.size() == 0) {
    // result = index.getPostings(term);
    // } else {
    // listUnion = index.getPostings(term);
    // // listIntersect.deduplication();
    // result = result.union(listUnion);
    // }
    // }
    // }

    // // result.deduplication();

    // return result;
    // }

    public HashMap unionPostinglist(String token) {
        HashMap<Integer, ArrayList<Integer>> result = new HashMap<Integer, ArrayList<Integer>>();
        PostingsList listPhrase = new PostingsList();
        if (token.contains("*")) {
            Query _query = new Query();
            _query = kgIndex.getWordofWildcard(token);
            for (int j = 0; j < _query.size(); j++) {
                String term = _query.queryterm.get(j).term;
                // System.err.println(term);
                listPhrase = index.getPostings(term);
                result = listPhrase.generateHashMap(result);
            }
        } else {
            listPhrase = index.getPostings(token);
            result = listPhrase.generateHashMap(result);
        }
        return result;
    }

    // read page rank from file to a hashtable
    Hashtable<String, Double> pageRank = new Hashtable<String, Double>();

    void readPagerank(String filename) {
        int fileIndex = 0;
        try {
            System.err.print("Reading titles file... ");
            BufferedReader in = new BufferedReader(new FileReader(filename));
            String line;
            while ((line = in.readLine()) != null && fileIndex < MAX_NUMBER_OF_DOCS) {
                int index1 = line.indexOf("=");
                int index2 = line.indexOf(";");
                String docTitle = line.substring(0, index1);
                Double pagerankValue = Double.parseDouble(line.substring(index1 + 1, index2));
                pageRank.put(docTitle, pagerankValue);
                fileIndex++;
                // System.err.println(pageRank.get(docTitle));
            }
            if (fileIndex >= MAX_NUMBER_OF_DOCS) {
                System.err.print("stopped reading since documents table is full. ");
            } else {
                System.err.print("done. ");
            }
        } catch (FileNotFoundException e) {
            System.err.println("File " + filename + " not found!");
        } catch (IOException e) {
            System.err.println("Error reading file " + filename);
        }
        System.err.println("Read " + fileIndex + " number of documents");
    }

}
