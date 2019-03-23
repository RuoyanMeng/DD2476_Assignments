/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, 2017
 */

package ir;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.*;
import java.util.Collections;
import java.util.Comparator;
//import com.sun.tools.sjavac.comp.dependencies.PublicApiCollector;
import java.util.HashMap;

public class PostingsList {
    /**
     * The postings list
     */
    private ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();

    /**
     * Number of postings in this list.
     */
    public int size() {
        return list.size();
    }

    /**
     * Returns the ith posting.
     */
    public PostingsEntry get(int i) {
        return list.get(i);
    }

    // clear all elements
    public void clear() {
        list.clear();
    }

    // set score

    // insert elements into HashedIndex PostingsList
    public void addElements(int docID, int offset, double score) {
        PostingsEntry entry = new PostingsEntry();
        entry.docID = docID;
        entry.offset = offset;
        entry.score = score;

        list.add(entry);
    }

    // insert elements into PersistentHashedIndex PostingsList
    public void addPersistentElements(int docID, int offset, double score) {
        PostingsEntry entry = new PostingsEntry();
        entry.docID = docID;
        entry.offset = offset;
        entry.score = score;

        list.add(entry);
    }

    public String toStr() {
        StringBuilder a = new StringBuilder("");
        for (int i = 0; i < list.size(); i++) {
            // +" "+Double.toString(list.get(i).score)
            a.append(Integer.toString(list.get(i).docID));
            a.append(" ");
            a.append(Integer.toString(list.get(i).offset));
            a.append("\n");
        }
        return a.toString();
    }

    public void deduplication() {

        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i).docID == list.get(i + 1).docID) {
                list.remove(i);
                i--;
            }
        }
    }

    // public PostingsList intersect(PostingsList listIntersect) {

    // int m = 0;
    // int n = 0;
    // PostingsList _list = new PostingsList();
    // if (listIntersect==null){
    // for (int i=0;i<list.size();i++){
    // _list.addElements(list.get(i).docID, list.get(i).offset, list.get(i).score);
    // }
    // }else{
    // while (m < list.size() && n < listIntersect.size()) {
    // if (list.get(m).docID < listIntersect.get(n).docID) {
    // m++;
    // } else if (list.get(m).docID == listIntersect.get(n).docID) {
    // _list.addElements(list.get(m).docID, list.get(m).offset, list.get(m).score);
    // m++;
    // n++;
    // } else {
    // n++;
    // }
    // }
    // }

    // return _list;
    // }

    public HashMap intersect(HashMap<Integer, ArrayList<Integer>> queriesList,
            HashMap<Integer, ArrayList<Integer>> listIntersect) {

        HashMap<Integer, ArrayList<Integer>> result = new HashMap<Integer, ArrayList<Integer>>();
        if (listIntersect == null) {
            for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
        } else {
            if (queriesList.size() > listIntersect.size()) {
                for (Map.Entry<Integer, ArrayList<Integer>> entry : listIntersect.entrySet()) {
                    if (queriesList.containsKey(entry.getKey())) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                    if (listIntersect.containsKey(entry.getKey())) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return result;
    }
    

    // public PostingsList phaseIntersect(PostingsList listPhrase, int count) {

    //     PostingsList result = new PostingsList();
    //     Hashtable<Integer, ArrayList<Integer>> table = new Hashtable<Integer, ArrayList<Integer>>();

    //     for (int i = 0; i < listPhrase.size(); i++) {
    //         if (!table.containsKey(listPhrase.get(i).docID)) {
    //             table.put(listPhrase.get(i).docID, new ArrayList<Integer>());
    //         }
    //         table.get(listPhrase.get(i).docID).add(listPhrase.get(i).offset);
    //     }

    //     for (int i = 0; i < list.size(); i++) {
    //         if (list.get(i) == null) {
    //             break;
    //         }
    //         if (table.containsKey(list.get(i).docID)) {

    //             ArrayList<Integer> _offset = table.get(list.get(i).docID);
    //             for (int n = 0; n < _offset.size(); n++) {
    //                 if (_offset.get(n) == list.get(i).offset + count) {
    //                     result.addElements(list.get(i).docID, list.get(i).offset, list.get(i).score);
    //                 }
    //             }
    //         }
    //     }
    //     table.clear();
    //     return result;
    // }

    // phaseMapIntersect
    public HashMap phaseMapIntersect(HashMap<Integer, ArrayList<Integer>> queriesList,
            HashMap<Integer, ArrayList<Integer>> listMapPhrase, int count) {
        HashMap<Integer, ArrayList<Integer>> result = new HashMap<Integer, ArrayList<Integer>>();

        if (queriesList.size() < listMapPhrase.size()) {
            for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                if (listMapPhrase.containsKey(entry.getKey())) {
                    ArrayList<Integer> _offset = listMapPhrase.get(entry.getKey());
                    for (int n = 0; n < _offset.size(); n++) {
                        if (entry.getValue().contains(_offset.get(n) - count)) {
                            result.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } else {
            for (Map.Entry<Integer, ArrayList<Integer>> entry : listMapPhrase.entrySet()) {
                if (queriesList.containsKey(entry.getKey())) {
                    ArrayList<Integer> _offset = queriesList.get(entry.getKey());
                    for (int n = 0; n < _offset.size(); n++) {
                        if (entry.getValue().contains(_offset.get(n) + count)) {
                            result.put(entry.getKey(), _offset);
                        }
                    }
                }
            }
        }
        return result;
    }


    // public PostingsList union(PostingsList listUnion) {
    //     PostingsList result = new PostingsList();
    //     Hashtable<Integer, ArrayList<Integer>> table = new Hashtable<Integer, ArrayList<Integer>>();
    //     for (int i = 0; i < list.size(); i++) {
    //         if (!table.containsKey(list.get(i).docID)) {
    //             table.put(list.get(i).docID, new ArrayList<Integer>());
    //             result.addElements(list.get(i).docID, list.get(i).offset, list.get(i).score);
    //         }
    //         table.get(list.get(i).docID).add(list.get(i).offset);
    //     }
    //     for (int i = 0; i < listUnion.size(); i++) {
    //         if (!table.containsKey(listUnion.get(i).docID)) {
    //             table.put(listUnion.get(i).docID, new ArrayList<Integer>());
    //             result.addElements(listUnion.get(i).docID, listUnion.get(i).offset, listUnion.get(i).score);
    //         }
    //         table.get(listUnion.get(i).docID).add(listUnion.get(i).offset);
    //     }
    //     table.clear();
    //     return result;
    // }

    public HashMap union(HashMap<Integer, ArrayList<Integer>> queriesList,HashMap<Integer, ArrayList<Integer>> listMapIntersect){
        HashMap<Integer, ArrayList<Integer>> result = new HashMap<Integer, ArrayList<Integer>>();
        if (queriesList.size() > listMapIntersect.size()) {
            for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, ArrayList<Integer>> entry : listMapIntersect.entrySet()) {
                if (!result.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            for (Map.Entry<Integer, ArrayList<Integer>> entry : listMapIntersect.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, ArrayList<Integer>> entry : queriesList.entrySet()) {
                if (!result.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    // term freq and tf_idf value
    public void _tf_idf(Hashtable<Integer, ArrayList<Double>> tf, double idf, PostingsList _list, int count) {
        Hashtable<Integer, ArrayList<Integer>> table = new Hashtable<Integer, ArrayList<Integer>>();
        for (int i = 0; i < list.size(); i++) {
            if (!table.containsKey(list.get(i).docID)) {
                table.put(list.get(i).docID, new ArrayList<Integer>());
            }
            table.get(list.get(i).docID).add(list.get(i).offset);
        }

        for (int i = 0; i < _list.size(); i++) {
            if (table.containsKey(_list.get(i).docID)) {
                double tf_idf = 0.0;
                tf_idf = idf * (table.get(_list.get(i).docID).size());
                tf.get(_list.get(i).docID).add(count, tf_idf);
            }
        }
    }

    public HashMap generateHashMap(HashMap<Integer, ArrayList<Integer>> listMapPhrase) {
        // HashMap<Integer, ArrayList<Integer>> listmap = new HashMap<Integer,
        // ArrayList<Integer>>();
        for (int i = 0; i < list.size(); i++) {
            if (!listMapPhrase.containsKey(list.get(i).docID)) {
                listMapPhrase.put(list.get(i).docID, new ArrayList<Integer>());
            }
            listMapPhrase.get(list.get(i).docID).add(list.get(i).offset);
        }
        return listMapPhrase;
    }

    public void sortScore() {
        Collections.sort(list);
    }

    public void sortDocId() {
        Collections.sort(list, new Comparator<PostingsEntry>() {
            @Override
            public int compare(PostingsEntry o1, PostingsEntry o2) {
                return o1.docID - o2.docID;
            }
        });
    }

}
