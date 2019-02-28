/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */

package ir;

import java.io.Serializable;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score;
    public int offset;

    /**
     * PostingsEntries are compared by their score (only relevant
     * in ranked retrieval).
     * <p>
     * The comparison is defined so that entries will be put in
     * descending order.
     */
    public int compareTo(PostingsEntry other) {
        return Double.compare(other.score, score);
    }

    

    public void setScore(double score){
        this.score = score;
    }

    public double getScore(){
        return this.score;
    }
}

