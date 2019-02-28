import java.util.*;

import javax.print.attribute.standard.NumberOfDocuments;

import java.io.*;

public class PageRankSparse {

	public class Term implements Comparable<Term> {

		public double probability;
		public String docName;

		public Term(String docName, double probability) {
			this.docName = docName;
			this.probability = probability;
		}

		public int compareTo(Term other) {
			return Double.compare(other.probability, probability);
		}

		public void print() {
			//String value = String.format("%.5f", probability);
			String value = Double.toString(probability);
			System.out.println(docName + ": " + value);
		}
	}

	public ArrayList<Term> pagerankArry = new ArrayList<Term>();

	/**
	 * Maximal number of documents. We're assuming here that we don't have more docs
	 * than we can keep in main memory.
	 */
	final static int MAX_NUMBER_OF_DOCS = 2000000;
	// final static int MAX_NUMBER_OF_DOCS = 1000;

	/**
	 * Mapping from document names to document numbers.
	 */
	HashMap<String, Integer> docNumber = new HashMap<String, Integer>();

	/**
	 * Mapping from document numbers to document names
	 */
	String[] docName = new String[MAX_NUMBER_OF_DOCS];

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

	/**
	 * The probability that the surfer will be bored, stop following links, and take
	 * a random jump somewhere.
	 */
	final static double BORED = 0.15;

	/**
	 * Convergence criterion: Transition probabilities do not change more that
	 * EPSILON from one iteration to another.
	 */
	final static double EPSILON = 0.0001;

	 int N = 100000;

 

	/* --------------------------------------------- */

	public PageRankSparse(String filename) {
		int noOfDocs = readDocs(filename);
		//iterate(noOfDocs, 1000);
		// String file = "davisTitles.txt";
		// readDocName(file);
		// writePageRank(noOfDocs);
		readPageRank("result.txt");
		 //for (int i=0;i<11;i++){
			monteCarlo(1,noOfDocs,N);
			//monteCarlo(1,noOfDocs,N*noOfDocs);
			//monteCarlo(2,noOfDocs,N);
			//monteCarlo(4,noOfDocs,N);
			//monteCarlo(5,noOfDocs,N);
			//N ++;
			
		 //}
		

	}

	/* --------------------------------------------- */

	/**
	 * Reads the documents and fills the data structures.
	 *
	 * @return the number of documents read.
	 */
	int readDocs(String filename) {
		int fileIndex = 0;
		try {
			System.err.print("Reading file... ");
			BufferedReader in = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = in.readLine()) != null && fileIndex < MAX_NUMBER_OF_DOCS) {
				int index = line.indexOf(";");
				String title = line.substring(0, index);
				Integer fromdoc = docNumber.get(title);
				// Have we seen this document before?
				if (fromdoc == null) {
					// This is a previously unseen doc, so add it to the table.
					fromdoc = fileIndex++;
					docNumber.put(title, fromdoc);
					docName[fromdoc] = title;
				}
				// Check all outlinks.
				StringTokenizer tok = new StringTokenizer(line.substring(index + 1), ",");
				while (tok.hasMoreTokens() && fileIndex < MAX_NUMBER_OF_DOCS) {
					String otherTitle = tok.nextToken();
					Integer otherDoc = docNumber.get(otherTitle);
					if (otherDoc == null) {
						// This is a previousy unseen doc, so add it to the table.
						otherDoc = fileIndex++;
						docNumber.put(otherTitle, otherDoc);
						docName[otherDoc] = otherTitle;
					}
					// Set the probability to 0 for now, to indicate that there is
					// a link from fromdoc to otherDoc.
					if (link.get(fromdoc) == null) {
						link.put(fromdoc, new HashMap<Integer, Boolean>());
					}
					if (link.get(fromdoc).get(otherDoc) == null) {
						link.get(fromdoc).put(otherDoc, true);
						out[fromdoc]++;
					}
				}
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
		return fileIndex;

	}

	/* --------------------------------------------- */

	/*
	 * Chooses a probability vector a, and repeatedly computes aP, aP^2, aP^3...
	 * until aP^i = aP^(i+1).
	 */
	void iterate(int numberOfDocs, int maxIterations) {
		long startTime = System.currentTimeMillis();

		int numIter = 0;
		double[] a = new double[numberOfDocs];
		a[0] = 1.0;
		double[] old_a = Arrays.copyOf(a, a.length);
		double eps;
		while (numIter++ < maxIterations) {
			old_a = Arrays.copyOf(a, a.length);
			double J = 1.0 / numberOfDocs;
			for (int i = 0; i < numberOfDocs; ++i) {
				a[i] = 0;
				for (int j = 0; j < numberOfDocs; ++j) {
					HashMap<Integer, Boolean> outlinks = link.get(j);
					if (out[j] == 0) {
						a[i] += old_a[j] * J;
					} else {
						if (outlinks.get(i) != null) {
							a[i] += old_a[j] * ((1 - BORED) / out[j] + BORED * J);
						} else {
							a[i] += old_a[j] * BORED * J;
						}
					}
				}
			}
			eps = 0;
			for (int i = 0; i < numberOfDocs; ++i) {
				eps += Math.abs(old_a[i] - a[i]);
			}
			if (eps < EPSILON)
				break;
		}
		for (int i = 0; i < numberOfDocs; ++i) {
			pagerankArry.add(new Term(docName[i], a[i]));
		}
		Collections.sort(pagerankArry);
		for (int i = 0; i < 30; ++i) {
			pagerankArry.get(i).print();
		}
		long estimatedTime = System.currentTimeMillis() - startTime;
		System.out.println("elapsed iterate time: " + estimatedTime / 1000.0 + "s");
	}

	// read docName
	Hashtable<String, String> docTitles = new Hashtable<String, String>();

	void readDocName(String filename) {
		int fileIndex = 0;
		try {
			System.err.print("Reading titles file... ");
			BufferedReader in = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = in.readLine()) != null && fileIndex < MAX_NUMBER_OF_DOCS) {
				int index = line.indexOf(";");
				String docID = line.substring(0, index);
				String docTitle = line.substring(index + 1);
				docTitles.put(docID, docTitle);
				fileIndex++;
				// System.err.println(docTitle);
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

	// write pagerank to a txt file
	void writePageRank(int numberOfDocs) {
		BufferedWriter bufw = null;
		try {
			FileWriter fw = new FileWriter("PagerankScore.txt");
			bufw = new BufferedWriter(fw);
			for (int i = 0; i < numberOfDocs; i++) {
				String value = Double.toString(pagerankArry.get(i).probability);
				String docTitle = docTitles.get(pagerankArry.get(i).docName);
				bufw.write(docTitle + "=" + value + ";" + pagerankArry.get(i).docName);
				bufw.newLine();
				bufw.flush();
			}
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
/* -------------------MonteCarlo method-------------------------- */

	public void monteCarlo(int method, int numberOfDocs, int numberOfWalks) {
		double[] pr = new double[numberOfDocs];
		long begin = new Date().getTime();

		// MC end-point with random start
		if (method == 1) {
			Random randomPage = new Random();
			int id;

			for (int n = 0; n < numberOfWalks; n++) {
				// Run random walk n
				id = randomPage.nextInt(numberOfDocs);
				while (randomPage.nextDouble() > BORED) {
					if (out[id] == 0) {
						id = randomPage.nextInt(numberOfDocs);
					} else {
						Integer[] outlinks = link.get(id).keySet().toArray(new Integer[0]);
						id = outlinks[randomPage.nextInt(outlinks.length)];
					}
				}
				pr[id]++;
			}
			 
			//numberOfWalks = numberOfWalks/numberOfDocs;
			for (int n = 0; n < numberOfDocs; n++) {

				pr[n] /= Double.valueOf(numberOfWalks);
				pagerankArry.add(new Term(docName[n], pr[n]));
			}

			
		
		}
		// MC end-point with cyclic start
		else if (method == 2) {
			Random randomPage = new Random();
			int id;

			for (int n = 0; n < numberOfWalks; n++) {
				for (int d = 0; d < numberOfDocs; d++) {
					id = d;
					while (randomPage.nextDouble() > BORED) {
						if (link.get(id) == null)
							id = randomPage.nextInt(numberOfDocs);
						else {
							Integer[] outlinks = link.get(id).keySet().toArray(new Integer[0]);
							id = outlinks[randomPage.nextInt(out[id])];
						}
					}
					pr[id]++;
				}
			}

			for (int n = 0; n < numberOfDocs; n++) {
				pr[n] /= Double.valueOf(numberOfWalks * numberOfDocs);
				pagerankArry.add(new Term(docName[n], pr[n]));
			}
			
		}
		// MC complete path stopping at dangling nodes
		else if (method == 4) {
			Random randomPage = new Random();
			int id;
			int visits = 0;

			for (int n = 0; n < numberOfWalks; n++) {
				for (int d = 0; d < numberOfDocs; d++) {
					id = d;
					while (randomPage.nextDouble() > BORED) {
						pr[id]++;
						visits++;
						if (link.get(id) == null)
							break;
						else {
							Integer[] outlinks = link.get(id).keySet().toArray(new Integer[0]);
							id = outlinks[randomPage.nextInt(out[id])];
						}
					}
				}
			}

			for (int n = 0; n < numberOfDocs; n++) {
				pr[n] /= Double.valueOf(visits);
				pagerankArry.add(new Term(docName[n], pr[n]));
			}
			
		}
		// MC complete path with random start
		else if (method == 5) {
			Random randomPage = new Random();
			int id;
			int visits = 0;

			for (int n = 0; n < numberOfWalks; n++) {
				// Initiate at random page
				id = randomPage.nextInt(numberOfDocs);
				while (randomPage.nextDouble() > BORED) {
					pr[id]++;
					visits++;
					if (link.get(id) == null)
						break;
					else {
						Integer[] outlinks = link.get(id).keySet().toArray(new Integer[0]);
						id = outlinks[randomPage.nextInt(out[id])];
					}
				}
			}

			for (int n = 0; n < numberOfDocs; n++) {
				pr[n] /= Double.valueOf(visits);
				pagerankArry.add(new Term(docName[n], pr[n]));
			}
		}

		Collections.sort(pagerankArry);
		for (int i = 0; i < 30; ++i) {
			pagerankArry.get(i).print();
		}

		long end = new Date().getTime();
		System.err.println("time MC elapsed: " + (end - begin) + " ms");
		
		calculateDifference(method);
		pagerankArry.clear();
	}

	//measurement
	Hashtable<String, Double> pageRank = new Hashtable<String, Double>();
	public void readPageRank(String filename) {
		int fileIndex = 0;
		try {
			System.err.print("Reading titles file... ");
			BufferedReader in = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = in.readLine()) != null && fileIndex < MAX_NUMBER_OF_DOCS) {
				int index = line.indexOf(":");
				String docID = line.substring(0, index);
				Double pagerank = Double.valueOf(line.substring(index + 1));
				pageRank.put(docID, pagerank);
				fileIndex++;
				// System.err.println(docTitle);
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

	public void calculateDifference(int method){
		int numOfFiles = 30;
		double sum = 0;
		Hashtable<String, Double> mcR = new Hashtable<String, Double>();
		for (int i = 0; i < numOfFiles; i++){
			mcR.put(pagerankArry.get(i).docName, pagerankArry.get(i).probability);
		}
        for (Map.Entry<String, Double> entry : pageRank.entrySet()) {
			if(mcR.containsKey(entry.getKey())){
				sum += Math.pow(entry.getValue() - mcR.get(entry.getKey()), 2);
			}else{
				sum += Math.pow(entry.getValue() - 0, 2);
				
			}
            
		}
		writeSum(sum,method);
	}

	public void writeSum(Double sum, int Method){
		BufferedWriter out = null; 
		try {
			String file = "measureMC"+Method+".txt"; 
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)));
			out.write(N + ";" + sum);
			out.newLine();
			
		} catch (IOException e) {
			System.out.println(e.getMessage());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
	}



	/* --------------------------------------------- */

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Please give the name of the link file");
		} else {
			new PageRankSparse(args[0]);

		}
	}
}