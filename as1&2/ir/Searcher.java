/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */

package ir;

import java.util.Hashtable;

//import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory.Default;

import java.util.ArrayList;
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
                    if (list.size() == 0) {
                        list = index.getPostings(token);
                        if (query.size() == 0) {
                            list = list.intersect(listIntersect);
                        }
                        // list.deduplication();
                    } else {
                        listIntersect = index.getPostings(token);
                        // listIntersect.deduplication();
                        list = list.intersect(listIntersect);
                    }
                }
            }
            list.deduplication();
            result = list;
        } else if (queryType == QueryType.PHRASE_QUERY) {
            PostingsList list = new PostingsList();
            int count = 0;
            for (int i = 0; i < query.size(); i++) {
                String token = query.queryterm.get(i).term;
                PostingsList listPhrase = new PostingsList();
                if (list.size() == 0 && count == 0) {
                    list = index.getPostings(token);
                } else {
                    count++;
                    listPhrase = index.getPostings(token);
                    list = list.phaseIntersect(listPhrase, count);
                }
            }
            list.deduplication();
            result = list;
        } else if (queryType == QueryType.RANKED_QUERY) {

            readPagerank("./PagerankScore.txt");
            PostingsList _list = new PostingsList();
            // union the search results of each term
            if (query.size() != 0) {
                for (int i = 0; i < query.size(); i++) {
                    String token = query.queryterm.get(i).term;
                    PostingsList listUnion = new PostingsList();
                    if (_list.size() == 0) {
                        _list = index.getPostings(token);
                    } else {
                        listUnion = index.getPostings(token);
                        // listIntersect.deduplication();
                        _list = _list.union(listUnion);
                    }
                }
            }
            _list.deduplication();

            // switch between different ranking types
            if (rankingType == RankingType.PAGERANK) {
                if (query.size() != 0) {
                    for (int i = 0; i < _list.size(); i++) {
                        String filename = Index.docNames.get(_list.get(i).docID);
                        filename = filename.substring(filename.lastIndexOf("/")+1);
                        //System.err.println(filename);
                        double score = pageRank.get(filename);
                        _list.get(i).setScore(score);
                    }
                    _list.sort();
                }
                result = _list;
            } else {
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
                        // System.err.println(idf);
                        list._tf_idf(tf, idf, _list, i);
                    }
                    // assign score(tf_idf) to _list and sort the result
                    tf_idf_score(tf, _list);
                    _list.sort();
                }

                if(rankingType == RankingType.TF_IDF){
                    result = _list;
                }else if(rankingType == RankingType.COMBINATION ){
                    if (query.size() != 0) {
                        for (int i = 0; i < _list.size(); i++) {
                            String filename = Index.docNames.get(_list.get(i).docID);
                            filename = filename.substring(filename.lastIndexOf("/")+1);
                            //here 
                            double score = pageRank.get(filename)*(_list.get(i).score);
                            _list.get(i).setScore(score);
                        }
                        _list.sort();
                    }
                    result = _list;
                }
                
            }

        }
        return result;
    }

    public double idf(PostingsList list) {
        double idf;
        int df = 0;
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
                //System.err.println(pageRank.get(docTitle));
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
