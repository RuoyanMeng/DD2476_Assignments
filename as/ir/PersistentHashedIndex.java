/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, KTH, 2018
 */

package ir;


import java.util.*;
import java.io.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks.
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /**
     * The directory where the persistent index files are stored.
     */
    public static final String INDEXDIR = "./index";

    /**
     * The dictionary file name
     */
    public static final String DICTIONARY_FNAME = "dictionary";

    /**
     * The dictionary file name
     */
    public static final String DATA_FNAME = "data";

    /**
     * The terms file name
     */
    public static final String TERMS_FNAME = "terms";

    /**
     * The doc info file name
     */
    public static final String DOCINFO_FNAME = "docInfo";

    /**
     * The dictionary hash table on disk can fit this many entries.
     */
    public static final long TABLESIZE = 611953L;
    public static final int ENTRYSIZE = 20;
    /**
     * The dictionary hash table is stored in this file.
     */
    RandomAccessFile dictionaryFile;

    /**
     * The data (the PostingsLists) are stored in this file.
     */
    RandomAccessFile dataFile;

    /**
     * Pointer to the first free memory cell in the data file.
     */
    long free = 0L;

    /**
     * The cache as a main-memory hash map.
     */
    HashMap<String, PostingsList> index = new HashMap<String, PostingsList>();


    // ===================================================================

    /**
     * A helper class representing one entry in the dictionary hashtable.
     */
    public class Entry {
        //
        //  YOUR CODE HERE
        //
        public int size;
        public long ptr;
        public long hashSec;
        //public String token;

        public Entry(long ptr, int size, long hashSec) {
            this.size = size;
            this.ptr = ptr;
            this.hashSec = hashSec;
            // this.token = token;
        }

    }


    // ==================================================================


    /**
     * Constructor. Opens the dictionary file and the data file.
     * If these files don't exist, they will be created.
     */
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME, "rw");
            dictionaryFile.seek(TABLESIZE*ENTRYSIZE+1000);
            dictionaryFile.writeInt(1);
            dataFile = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME, "rw");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes data to the data file at a specified place.
     *
     * @return The number of bytes written.
     */
    int writeData(String dataString, long ptr) {
        try {
            dataFile.seek(ptr);
          // byte[] token = tokenString.getBytes();
            byte[] data = dataString.getBytes() ;
           // dataFile.write(token);
            dataFile.write(data);
            return data.length;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     * Reads data from the data file
     */
    String readData(long ptr, int size) {
        try {
            dataFile.seek(ptr);
            byte[] data = new byte[size];
            dataFile.readFully(data);
            return new String(data);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file.
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry(Entry entry, long ptr) {
        
        try {
            dictionaryFile.seek(ptr);
            //dictionaryFile.writeBytes(entry.token);
            dictionaryFile.writeLong(entry.hashSec);
            dictionaryFile.writeLong(entry.ptr);
            dictionaryFile.writeInt(entry.size);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Reads an entry from the dictionary file.
     *
     * @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry(long ptr) {
        
        try {
            dictionaryFile.seek(ptr);

            long hashSec = dictionaryFile.readLong();
            dictionaryFile.seek(ptr+8);
            long pointer = dictionaryFile.readLong();
            dictionaryFile.seek(ptr+16);
            int size = dictionaryFile.readInt();
            Entry entry = new Entry(pointer, size, hashSec);
            return entry;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================

    /**
     * Writes the document names and document lengths to file.
     *
     * @throws IOException { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream(INDEXDIR + "/docInfo");
        for (Map.Entry<Integer, String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
    }


    /**
     * Reads the document names and document lengths from file, and
     * put them in the appropriate data structures.
     *
     * @throws IOException { exception_description }
     */
    private void readDocInfo() throws IOException {
        File file = new File(INDEXDIR + "/docInfo");
        FileReader freader = new FileReader(file);
        try (BufferedReader br = new BufferedReader(freader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put(Integer.valueOf(data[0]), data[1]);
                docLengths.put(Integer.valueOf(data[0]), Integer.valueOf(data[2]));
            }
        }
        freader.close();
    }


    /**
     * Write the index to files.
     */
    
    public void writeIndex() {
        int collisions = 0;
        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();

            // Write the dictionary and the postings list
            long max_val =0;
            for (Map.Entry<String, PostingsList> entry : index.entrySet()) {
                
                long hash = stringHash(entry.getKey());
                long hashSec = stringHash2(entry.getKey());
                long h2=0;
                int sq = 1;
                
                if (hash >= TABLESIZE|hash <0) {
                        hash = 0L;
                }
                while (!iCollision(hash,h2)) {
                    if (hash >= TABLESIZE|hash<0) {
                        hash = 0L;
                    }
                    hash = hash+ (sq^2);
                    sq++;
                    collisions++;
                }
                if(sq > max_val){
                    max_val = sq;
                  }
                

                int pl_size = writeData(entry.getValue().toStr(), free);          
                Entry entry_ = new Entry(free, pl_size,hashSec);
                writeEntry(entry_, hash*ENTRYSIZE);
                free = free + pl_size ;

                //test 
                // if(entry.getKey().equals("they")){
                //     System.err.println(entry.getKey());
                //     System.err.println(entry.getValue().toStr()); 
                // }     
            }
            System.err.println(max_val);   
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.err.println(collisions + " collisions.");
    }

    //Hash Function 1
    public static long stringHash(String term) {
        long hash = 7;
        for (int i = 0; i < term.length(); i++) {
            hash = (33 * hash + term.charAt(i)) % TABLESIZE ;
        }
        return hash;
        
    }

    //Hash Function 2
    public static long stringHash2(String term){
        int hash, i;
        for (hash=term.length(), i=0; i<term.length(); ++i)
            hash = (hash<<4)^(hash>>28)^term.charAt(i);
        return (hash % TABLESIZE);
        
    }

    // if has hash collisions
    boolean iCollision(long hash1,long hash2){
        boolean result = false;
        try {
            if (hash1 >= TABLESIZE| hash1<0) {
                result = false;
            }
            else{
                dictionaryFile.seek(hash1*ENTRYSIZE);
                if(dictionaryFile.readLong() == hash2){
                result = true;
                }
            }            
         }catch (IOException e) {
             e.printStackTrace();
        }
        return result;
    }

    // ==================================================================


    /**
     * Returns the postings for a specific term, or null
     * if the term is not in the index.
     */
    public PostingsList getPostings(String token) {

        
        long hash1 = stringHash(token);
        long hash2 = stringHash2(token);
        int sq = 1;

        if (hash1 >= TABLESIZE | hash1<0) {
            hash1 = 0L;
        }
        while (!iCollision(hash1, hash2) && sq<100) {
            if (hash1 >= TABLESIZE|hash1<0) {
                hash1 = 0l;
            }
            hash1 = hash1+(sq^2);
            sq++;
        }

        
        Entry entry = readEntry(hash1*ENTRYSIZE);
        //System.out.println(entry.ptr+","+entry.size);
        String a = readData(entry.ptr, entry.size);
        //System.out.println(a);
        PostingsList pl = toPostingsList(a);
        return pl;

    }

    public PostingsList toPostingsList(String a){
        PostingsList result = new PostingsList();
        String[] postingsList = a.split("\n");
      
        for (int i=0;i<postingsList.length;i++){
            String[] arry = postingsList[i].split(" ");
            //System.out.println(arry[0]+","+arry[1]);
            result.addElements(Integer.parseInt(arry[0]),Integer.parseInt(arry[1]),0.0);
        }
        return result;
    }


    /**
     * Inserts this token in the main-memory hashtable.
     */
    public void insert(String token, int docID, int offset) {
        if (!index.containsKey(token)) {
            //if token didn't exit, insert new one
            index.put(token, new PostingsList());

        }
        index.get(token).addPersistentElements(docID, offset, 0.0);
    }


    /**
     * Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println(index.keySet().size() + " unique words");
        System.err.print("Writing index to disk...");
        writeIndex();
        System.err.println("done!");
    }
}
