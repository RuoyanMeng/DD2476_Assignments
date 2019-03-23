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

        // INTERSECTION_QUERY
        if (queryType == QueryType.INTERSECTION_QUERY) {
            PostingsList list = new PostingsList();
            if (query.size() != 0) {
                for (int i = 0; i < query.size(); i++) {
                    String token = query.queryterm.get(i).term;
                    PostingsList listIntersect = new PostingsList();

                    // Processing Wildcard Queries
                    PostingsList listWildcard = new PostingsList();
                    if (token.contains("*")) {
                        Query _query = new Query();
                        _query = kgIndex.getWordofWildcard(token);
                        listWildcard = unionPostinglist(_query,queryType);
                        listWildcard.deduplication();
                        listWildcard.sortDocId();
                    } else {
                        listWildcard = index.getPostings(token);
                    }

                    if (list.size() == 0) {
                        list = listWildcard;
                        if (query.size() == 0) {
                            list = list.intersect(listIntersect);
                        }
                    } else {
                        listIntersect = listWildcard;
                        list = list.intersect(listIntersect);
                    }
                }
            }
            list.deduplication();
            result = list;

        } else if (queryType == QueryType.PHRASE_QUERY) {
            PostingsList list = new PostingsList();
            int count = 0;

            //rewrite phase_query
            HashMap<Integer, ArrayList<Integer>> queriesList = new HashMap<Integer, ArrayList<Integer>>();
            for (int i=0;i<query.size();i++){
                String token = query.queryterm.get(i).term;
                PostingsList listPhrase = new PostingsList();
                HashMap<Integer, ArrayList<Integer>> listMapPhrase = new HashMap<Integer, ArrayList<Integer>>();
                if (token.contains("*")) {
                    Query _query = new Query();
                    _query = kgIndex.getWordofWildcard(token);
                    for (int j = 0; j < _query.size(); j++) {
                        String term = _query.queryterm.get(j).term;
                        // System.err.println(term);
                        listPhrase = index.getPostings(term);
                        listMapPhrase = listPhrase.generateHashMap(listMapPhrase);
                        
                    }
                } else {
                    listPhrase = index.getPostings(token);
                    listMapPhrase = listPhrase.generateHashMap(listMapPhrase);
                }

                if (queriesList.size() == 0 && count == 0) {
                    queriesList=listMapPhrase;
                    //System.err.println("00:"+queriesList.size());
                } else {
                    count++;
                    //System.err.println("before"+queriesList.size());
                    queriesList = phaseMapIntersect(queriesList,listMapPhrase,count);
                    //System.err.println("after"+queriesList.size());
                }

               
                //listMapPhrase.clear();
            }
            for(Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()){
                list.addElements(entry.getKey(), 1, 1.0);
            }

            //
            // for (int i = 0; i < query.size(); i++) {
            //     String token = query.queryterm.get(i).term;
            //     PostingsList listPhrase = new PostingsList();

            //     // Processing Wildcard Queries
            //     PostingsList listWildcard = new PostingsList();
            //     if (token.contains("*")) {
            //         Query _query = new Query();
            //         _query = kgIndex.getWordofWildcard(token);
            //         listWildcard = unionPostinglist(_query,queryType);
            //     } else {
            //         listWildcard = index.getPostings(token);
            //     }

            //     if (list.size() == 0 && count == 0) {
            //         list = listWildcard;
            //     } else {
            //         count++;
            //         listPhrase = listWildcard;
            //         list = list.phaseIntersect(listPhrase, count);
            //     }
            // }
            list.deduplication();
            result = list;
        } else if (queryType == QueryType.RANKED_QUERY) {
            readPagerank("./PagerankScore.txt");
            PostingsList _list = new PostingsList();

            // processing Wildcard Queries
            // union the search results of each term
            PostingsList listWildcard = new PostingsList();
            Query _query = new Query();
            for (int i = 0; i < query.size(); i++) {
                String token = query.queryterm.get(i).term;
                PostingsList listPhrase = new PostingsList();

                if (token.contains("*")) {
                    Query queries = new Query();
                    queries = kgIndex.getWordofWildcard(token);
                    _query.addQueries(_query, queries);
                    listWildcard = unionPostinglist(queries,queryType);
                    listWildcard.deduplication();
                } else {
                    listWildcard = index.getPostings(token);
                    _query.addTerm(token);
                }

                if (_list.size() == 0 ) {
                    _list = listWildcard;
                } else {
                    listPhrase = listWildcard;
                    _list = _list.union(listPhrase);
                }
            }
            query = _query;
           // _list.deduplication();
            System.err.println(_list.size());

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
                Hashtable<Integer, ArrayList<Double>> tf = new Hashtable<Integer, ArrayList<Double>>();

                // initialize hashtable of term freq - tf
                for (int i = 0; i < _list.size(); i++) {
                    if (!tf.containsKey(_list.get(i).docID)) {
                        tf.put(_list.get(i).docID, new ArrayList<Double>());
                        for (int n = 0; n < query.size(); n++) {
                            tf.get(_list.get(i).docID).add(n, 0.0);
                        }
                    }
                }
                // calculate tf_idf
                if (query.size() != 0) {
                    for (int i = 0; i < query.size(); i++) {
                        String token = query.queryterm.get(i).term;
                        PostingsList list = new PostingsList();
                        list = index.getPostings(token);
                        double idf = 0.0;
                        idf = idf(list);
                        // System.err.println("-"+idf);
                        idf = idf * query.queryterm.get(i).weight;
                        // System.err.println(idf);
                        list._tf_idf(tf, idf, _list, i);
                    }
                    // assign score(tf_idf) to _list and sort the result
                    tf_idf_score(tf, _list);
                    _list.sortScore();
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

    public double idf(PostingsList list) {
        double idf;
        int df = 1;
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i).docID == list.get(i + 1).docID) {
                df = df + 0;
            } else {
                df++;
            }
        }
        // System.out.println(Index.docLengths.size());
        idf = Math.log10(Index.docLengths.size() / df);
        return idf;
    }

    public void tf_idf_score(Hashtable<Integer, ArrayList<Double>> tf, PostingsList _list) {
        for (int i = 0; i < _list.size(); i++) {
            // System.out.println("list:"+_list.get(i).docID);
            double score = 0.0;
            ArrayList<Double> _tf_idf = tf.get(_list.get(i).docID);
            for (int n = 0; n < _tf_idf.size(); n++) {
                score = score + _tf_idf.get(n) / Index.docLengths.get(_list.get(i).docID);

            }
            _list.get(i).setScore(score);
            // System.err.println(_list.get(i).score);
            //
        }

    }

    public PostingsList unionPostinglist(Query query,QueryType queryType) {
        PostingsList result = new PostingsList();
        // System.err.println("query size: " + query.size());
        if (query.size() != 0) {
            for (int j = 0; j < query.size(); j++) {
                String term = query.queryterm.get(j).term;
                PostingsList listUnion = new PostingsList();
                // System.err.println(term);
                if (result.size() == 0) {
                    result = index.getPostings(term);
                } else {
                    listUnion = index.getPostings(term);
                    // listIntersect.deduplication();
                    if(queryType == QueryType.PHRASE_QUERY){
                        
                        result = result.unionForPhasequery(listUnion);
                    }else{
                        result = result.union(listUnion);
                    }
                    
                }
            }
        }

        // result.deduplication();

        return result;
    }

    //phaseMapIntersect
    public HashMap phaseMapIntersect(HashMap<Integer, ArrayList<Integer>> queriesList,HashMap<Integer, ArrayList<Integer>> listMapPhrase,int count ){
        HashMap<Integer, ArrayList<Integer>> result = new HashMap<Integer, ArrayList<Integer>>();
        
        if(queriesList.size()<listMapPhrase.size()){
            for(Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()){
                if(listMapPhrase.containsKey(entry.getKey())){
                    ArrayList<Integer> _offset = listMapPhrase.get(entry.getKey());
                    for (int n = 0; n < _offset.size(); n++) {
                        if (entry.getValue().contains(_offset.get(n)-count) ) {
                            result.put(entry.getKey(),entry.getValue());
                        }
                    }
                }
            }
        }else{
            for(Map.Entry<Integer, ArrayList<Integer>> entry : listMapPhrase.entrySet()){
                if(queriesList.containsKey(entry.getKey())){
                    ArrayList<Integer> _offset = queriesList.get(entry.getKey());
                    for (int n = 0; n < _offset.size(); n++) {
                        if (entry.getValue().contains(_offset.get(n)+count) ) {
                            result.put(entry.getKey(),_offset);
                        }
                    }
                }
            }
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
