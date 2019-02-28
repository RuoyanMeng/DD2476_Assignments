/**
 *   Computes the Hubs and Authorities for an every document in a query-specific
 *   link graph, induced by the base set of pages.
 *
 *   @author Dmytro Kalpakchi
 */

package ir;

import java.util.*;
import java.io.*;

public class HITSRanker {

    /**
     * Maximal number of documents. We're assuming here that we don't have more docs
     * than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     * Max number of iterations for HITS
     */
    final static int MAX_NUMBER_OF_STEPS = 1000;

    /**
     * Convergence criterion: hub and authority scores do not change more that
     * EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.001;

    /**
     * The inverted index
     */
    Index index;

    /**
     * Mapping from the titles to internal document ids used in the links file
     */
    HashMap<String, Integer> titleToId = new HashMap<String, Integer>();

    /**
     * Sparse vector containing hub scores
     */
    HashMap<Integer, Double> hubs = new HashMap<Integer, Double>();

    /**
     * e Sparse vector containing authority scores
     */
    HashMap<Integer, Double> authorities = new HashMap<Integer, Double>();

    /**
     * A memory-efficient representation of the transition matrix. The outlinks are
     * represented as a HashMap, whose keys are the numbers of the documents linked
     * from.
     * <p>
     *
     * The value corresponding to key i is a HashMap whose keys are all the numbers
     * of documents j that i links to.
     * <p>
     *
     * If there are no outlinks from i, then the value corresponding key i is null.
     */
    HashMap<Integer, HashMap<Integer, Boolean>> link = new HashMap<Integer, HashMap<Integer, Boolean>>();

    /**
     * The number of outlinks from each node.
     */
    int[] out = new int[MAX_NUMBER_OF_DOCS];

    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph. Each page is a node in
     * graph with a distinct nodeID associated with it. There is an edge between two
     * nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     * nodeID;outNodeID1,outNodeID2,...,outNodeIDK This means that there are edges
     * between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format: nodeID;pageTitle
     * 
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the
     * same as docIDs used by search engine's Indexer
     *
     * @param linksFilename  File containing the links of the graph
     * @param titlesFilename File containing the mapping between nodeIDs and pages
     *                       titles
     * @param index          The inverted index
     */
    public HITSRanker(String linksFilename, String titlesFilename, Index index) {
        this.index = index;
        readDocs(linksFilename, titlesFilename);
    }

    /* --------------------------------------------- */

    /**
     * A utility function that gets a file name given its path. For example, given
     * the path "davisWiki/hello.f", the function will return "hello.f".
     *
     * @param path The file path
     *
     * @return The file name.
     */
    private String getFileName(String path) {
        String result = "";
        StringTokenizer tok = new StringTokenizer(path, "\\/");
        while (tok.hasMoreTokens()) {
            result = tok.nextToken();
        }
        return result;
    }

    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param linksFilename  File containing the links of the graph
     * @param titlesFilename File containing the mapping between nodeIDs and pages
     *                       titles
     */
    void readDocs(String linksFilename, String titlesFilename) {
        // read linksFile
        int fileIndex = 0;
        try {
            System.err.print("Reading file... ");
            BufferedReader in = new BufferedReader(new FileReader(linksFilename));
            String line;
            while ((line = in.readLine()) != null && fileIndex < MAX_NUMBER_OF_DOCS) {
                int index = line.indexOf(";");
                String title = line.substring(0, index);
                Integer fromDocID = Integer.valueOf(title);
                if (link.get(fromDocID) == null) {
                    link.put(fromDocID, new HashMap<Integer, Boolean>());
                }
                fileIndex++;

                // Check all outlinks.
                StringTokenizer tok = new StringTokenizer(line.substring(index + 1), ",");
                while (tok.hasMoreTokens() && fileIndex < MAX_NUMBER_OF_DOCS) {
                    String otherTitle = tok.nextToken();
                    Integer otherDocID = Integer.valueOf(otherTitle);

                    // Set the probability to 0 for now, to indicate that there is
                    // a link from fromdoc to otherDoc.
                    if (link.get(fromDocID).get(otherDocID) == null) {
                        link.get(fromDocID).put(otherDocID, true);
                        out[fromDocID]++;
                    }
                }
            }
            if (fileIndex >= MAX_NUMBER_OF_DOCS) {
                System.err.print("stopped reading since documents table is full. ");
            } else {
                System.err.print("done. ");
            }
        } catch (FileNotFoundException e) {
            System.err.println("File " + linksFilename + " not found!");
        } catch (IOException e) {
            System.err.println("Error reading file " + linksFilename);
        }
        System.err.println("Read " + fileIndex + " number of documents---links");

        // read titles file
        fileIndex = 0;
        try {
            System.err.print("Reading titles file... ");
            BufferedReader in = new BufferedReader(new FileReader(titlesFilename));
            String line;
            while ((line = in.readLine()) != null && fileIndex < MAX_NUMBER_OF_DOCS) {
                int index = line.indexOf(";");
                Integer docID = Integer.valueOf(line.substring(0, index));
                String docTitle = line.substring(index + 1);
                titleToId.put(docTitle, docID);
                fileIndex++;
                // System.err.println(docTitle);
            }
            if (fileIndex >= MAX_NUMBER_OF_DOCS) {
                System.err.print("stopped reading since documents table is full. ");
            } else {
                System.err.print("done. ");
            }
        } catch (FileNotFoundException e) {
            System.err.println("File " + titlesFilename + " not found!");
        } catch (IOException e) {
            System.err.println("Error reading file " + titlesFilename);
        }
        System.err.println("Read " + fileIndex + " number of documents");

    }

    /**
     * Perform HITS iterations until convergence
     *
     * @param titles The titles of the documents in the root set
     */
    private void iterate(HashMap<Integer, Integer> list) {
        long startTime = System.currentTimeMillis();

        int pageNum = list.size();
        // initialize authority scores and hub scores
        for (int i = 0; i < pageNum; i++) {
            authorities.put(i, 1.0);
            hubs.put(i, 1.0);
        }

        double sumHub = 0;
        double sumAuthority = 0;
        // double maxHub = 0;
        // double maxAuthority = 0;

        double error = Integer.MAX_VALUE;

        double[] newHubs = new double[pageNum];
        double[] newAuthorities = new double[pageNum];

        int num = 0;

        while (error > 0.0001 * pageNum) {
            num++;
            for (int i = 0; i < pageNum; i++) {
                newHubs[i] = 0;
                newAuthorities[i] = 0;
            }
            // if current page a node or not, (out[i]!=0), if not hub and authority should
            // be 0
            for (int i = 0; i < pageNum; i++) {
                int linkId_i = list.get(i);
                for (int j = 0; j < pageNum; j++) {
                    int linkId_j = list.get(j);
                    if (out[linkId_i] != 0) {
                        if (link.get(linkId_i).get(linkId_j) != null) {
                            newHubs[i] += authorities.get(j);
                            newAuthorities[j] += hubs.get(i);
                        }
                    }

                }
            }

            sumHub = 0;
            sumAuthority = 0;
            for (int k = 0; k < pageNum; k++) {
            sumHub += newHubs[k] * newHubs[k];
            sumAuthority += newAuthorities[k] * newAuthorities[k];

            }

            // maxHub = 0;
            // maxAuthority = 0;
            // for (int k = 0; k < pageNum; k++) {
            //     if (newHubs[k] > maxHub) {
            //         maxHub = newHubs[k];
            //     }

            //     if (newAuthorities[k] > maxAuthority) {
            //         maxAuthority = newAuthorities[k];
            //     }
            // }
            // System.err.println(sumHub);
            // System.err.println(sumAuthority);

            error = 0;
            // normalize
            for (int k = 0; k < pageNum; k++) {
                newHubs[k] /= Math.sqrt(sumHub);
                newAuthorities[k] /= Math.sqrt(sumAuthority);
                // newHubs[k] /= maxHub;
                // newAuthorities[k] /= maxAuthority;
                error += Math.abs(newHubs[k] - hubs.get(k));
                // + Math.abs(newAuthorities[k] - authorities.get(k))
                hubs.put(k, newHubs[k]);
                authorities.put(k, newAuthorities[k]);
            }
            // System.out.println(num + "--" + error + "---------");

        }
        long estimatedTime = System.currentTimeMillis() - startTime;
        System.out.println("elapsed iterate time: " + estimatedTime / 1000.0 + "s");

    }

    /**
     * Rank the documents in the subgraph induced by the documents present in the
     * postings list `post`.
     *
     * @param post The list of postings fulfilling a certain information need
     *
     * @return A list of postings ranked according to the hub and authority scores.
     */
    HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();

    PostingsList rank(PostingsList post) {
        PostingsList result = new PostingsList();
        int pageNum = titleToId.size();
        int n = 0;

        // System.out.println(post.size());
        for (int i = 0; i < post.size(); i++) {
            //System.err.println(i);
            String filename = Index.docNames.get(post.get(i).docID);
            filename = getFileName(filename);
            //System.err.println(filename);
            int prID = 0;
            if (titleToId.containsKey(filename)) {
                prID = titleToId.get(filename);
                if (out[prID] != 0) {
                    for (Map.Entry<Integer, Boolean> a : link.get(prID).entrySet()) {
                        if (!list.containsValue(a.getKey())) {
                            list.put(n, a.getKey());
                            n++;
                        }

                    }
                }
            } 

            for (int m = 0; m < pageNum; m++) {
                if (out[m] != 0 ) {
                    if(link.get(m).containsKey(prID) && !list.containsValue(m)){
                        list.put(n, m);
                        n++;
                    }
                    
                }
            }

            if (!list.containsValue(prID)) {
                list.put(n, prID);
                n++;
            }

        }
        System.err.println(n+"-"+list.size());

        iterate(list);
        for (int x = 0; x < list.size(); x++) {
            if (Index.linkToDocID.containsKey(list.get(x))) {
                double score = calScore(x);
                int docID = Index.linkToDocID.get(list.get(x));
                result.addElements(docID, 0, score);
            }
        }
        

        return result;
    }

    public Double calScore(int prID) {
        double _hub = hubs.get(prID);
        double _authority = authorities.get(prID);
        double score = 0.5 * _hub + 0.5 * _authority;
        return score;
    }

    /**
     * Sort a hash map by values in the descending order
     *
     * @param map A hash map to sorted
     *
     * @return A hash map sorted by values
     */
    private HashMap<Integer, Double> sortHashMapByValue(HashMap<Integer, Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer, Double>> list = new ArrayList<Map.Entry<Integer, Double>>(map.entrySet());

            Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
                public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                    return (o2.getValue()).compareTo(o1.getValue());
                }
            });

            HashMap<Integer, Double> res = new LinkedHashMap<Integer, Double>();
            for (Map.Entry<Integer, Double> el : list) {
                res.put(el.getKey(), el.getValue());
            }
            return res;
        }
    }

    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param map   A hash map
     * @param fname The filename
     * @param k     A number of entries to write
     */
    void writeToFile(HashMap<Integer, Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));

            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer, Double> e : map.entrySet()) {
                    i++;
                    writer.write(e.getKey() + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k)
                        break;
                }
            }
            writer.close();
        } catch (IOException e) {
        }
    }

    /**
     * Rank all the documents in the links file. Produces two files: hubs_top_30.txt
     * with documents containing top 30 hub scores authorities_top_30.txt with
     * documents containing top 30 authority scores
     */
    void rank() {
        linkToDoc();
        // iterate(titleToId.keySet().toArray(new String[0]));
        // HashMap<Integer, Double> sortedHubs = sortHashMapByValue(hubs);
        // HashMap<Integer, Double> sortedAuthorities = sortHashMapByValue(authorities);
        // writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        // writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }

    // creat a file which include the info of maping linkID to docID
    private void linkToDoc() {
        BufferedWriter bufw = null;
        try {
            FileWriter fw = new FileWriter("linkIdtoDocId.txt");
            bufw = new BufferedWriter(fw);

            for (Map.Entry<Integer, String> ss : Index.docNames.entrySet()) {
                String filename = ss.getValue();
                filename = getFileName(filename);
                if(!titleToId.containsKey(filename)){
                    System.err.println(filename +";"+ ss.getKey()+"=");
                }
                if (titleToId.containsKey(filename)) {
                    bufw.write(titleToId.get(filename) + ";" + ss.getKey());
                    bufw.newLine();
                    bufw.flush();
                }
            }

            System.err.println("done link to doc");

        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            if (bufw != null) {
                try {
                    bufw.close();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    /* --------------------------------------------- */

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Please give the names of the link and title files");
        } else {
            // HITSRanker hr = new HITSRanker("pagerank/linksDavis.txt",
            // "pagerank/davisTitles.txt", null);
            HITSRanker hr = new HITSRanker(args[0], args[1], null);
            hr.rank();
        }
    }
}