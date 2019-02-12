/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */

package ir;

import java.util.Hashtable;
import java.util.ArrayList;
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

    /**
     * Searches the index for postings matching the query.
     *
     * @return A postings list representing the result of the query.
     */
    public PostingsList search(Query query, QueryType queryType, RankingType rankingType) {

        PostingsList result = new PostingsList();

        //INTERSECTION_QUERY
        if (queryType == QueryType.INTERSECTION_QUERY) {
            PostingsList list = new PostingsList();
            if (query.size() != 0) {
                for (int i = 0; i < query.size(); i++) {
                    String token = query.queryterm.get(i).term;
                    PostingsList listIntersect = new PostingsList();
                    if (list.size() == 0) {
                        list = index.getPostings(token);
                        if(query.size()==0){
                            list = list.intersect(listIntersect);
                        }
                        //list.deduplication();
                    } else {
                        listIntersect = index.getPostings(token);
                        //listIntersect.deduplication();
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
        } else if(queryType == QueryType.RANKED_QUERY){

            Hashtable<Integer,ArrayList<Double>> tf = new Hashtable<Integer, ArrayList<Double>>();
            PostingsList _list = new PostingsList();
            
            if (query.size() != 0) {
                for (int i = 0; i < query.size(); i++) {
                    String token = query.queryterm.get(i).term;
                    PostingsList listUnion = new PostingsList();
                    if (_list.size() == 0) {
                        _list = index.getPostings(token);
                    } else {
                        listUnion = index.getPostings(token);
                        //listIntersect.deduplication();
                        _list = _list.union(listUnion);
                    }
                }
            }
            _list.deduplication();
            for(int i=0;i<_list.size();i++){
                if (!tf.containsKey(_list.get(i).docID)){
                    tf.put(_list.get(i).docID, new ArrayList<Double>());
                    for (int n = 0; n < query.size(); n++){
                        tf.get(_list.get(i).docID).add(n,0.0);
                    }
                }
            }
            if (query.size() != 0) {
                for (int i = 0; i < query.size(); i++){
                    String token = query.queryterm.get(i).term;
                    PostingsList list = new PostingsList();
                    list = index.getPostings(token);
                    double idf =0.0;
                    idf = idf(list);
                    System.err.println(idf);
                    list._tf_idf(tf,idf,_list,i);
                }
                tf_idf_score(tf,_list);
                _list.sort();
            }

            result = _list;
        }
        return result;
    }

    public double idf(PostingsList list){
        double idf;
        int df = 0;
        for(int i=0;i<list.size()-1;i++){
            if (list.get(i).docID == list.get(i + 1).docID){
                df = df + 0;
            }else{
                df++;
            }
        }
        //System.out.println(Index.docLengths.size());
        idf = Math.log10(Index.docLengths.size()/df);
        return idf;
    }

    public void tf_idf_score(Hashtable<Integer,ArrayList<Double>> tf, PostingsList _list){
        for (int i=0;i<_list.size();i++){
            //System.out.println("list:"+_list.get(i).docID);
            double score = 0.0;
            ArrayList<Double> _tf_idf = tf.get(_list.get(i).docID);
            for (int n =0;n<_tf_idf.size();n++){
                score = score + _tf_idf.get(n)/Index.docLengths.get(_list.get(i).docID);
            }
            _list.get(i).setScore(score);
            //System.err.println(_list.get(i).score);
            //
        }

    }

    

}
