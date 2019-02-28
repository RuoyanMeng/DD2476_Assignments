/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */


package ir;


import java.util.HashMap;


/**
 * Implements an inverted index as a Hashtable from words to PostingsLists.
 */
public class HashedIndex implements Index {


    /**
     * The index as a hashtable.
     */
    private HashMap<String, PostingsList> index = new HashMap<String, PostingsList>();


    /**
     * Inserts this token in the hashtable.
     * if token alreday exits, compare docID, if have same DocID, offset++
     */
    public void insert(String token, int docID, int offset) {

        if (!index.containsKey(token)) {
            //if token didn't exit, insert new one
            index.put(token, new PostingsList());

        }
        index.get(token).addElements(docID, offset, 0);

    }


    /**
     * Returns the postings for a specific term, or null
     * if the term is not in the index.
     */
    public PostingsList getPostings(String token) {
        //

        if (index.containsKey(token)) {
            return index.get(token);
        } else {
            return null;
        }
    }

    //

    /**
     * No need for cleanup in a HashedIndex.
     */
    public void cleanup() {
    }
}
