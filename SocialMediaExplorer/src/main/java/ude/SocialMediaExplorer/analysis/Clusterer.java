package ude.SocialMediaExplorer.analysis;

import java.awt.Dimension;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JFrame;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.select;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.frequency.util.FrequencyDistribution;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import ude.SocialMediaExplorer.Config;
import ude.SocialMediaExplorer.analysis.type.SenseAnno;
import ude.SocialMediaExplorer.analysis.type.SentimentAnno;
import ude.SocialMediaExplorer.data.utils.io.CASReader;
import ude.SocialMediaExplorer.data.utils.time.TimeStamp;
import ude.SocialMediaExplorer.shared.exchangeFormat.ClusterElement;
import ude.SocialMediaExplorer.shared.exchangeFormat.Sentiment;

public class Clusterer {

	public static FrequencyDistribution<String> fq;
	public static Map<String,Set<String>> orthographyClusters;
	public int nodeCounter;
	private String hashtagToCluster="halligalli";
	/**
	 * this method calls the clustering steps:
	 * 1.orthography cleaning
	 * 2.keyphrase extraction with a co-occurence graph approach
	 * 3.adapting to the ClusterElement.class (includes some cleaning of posts etc...)
	 * 4.finally these elements are binary serialized
	 * 
	 * Uncomment the marked parts for additional visualization via JUNG or syso
	 * 
	 */
	public void cluster(String hashtagToCluser) {
		
		this.hashtagToCluster=hashtagToCluser;
		CASReader reader= new CASReader();
		List<JCas> jCases= new ArrayList<JCas>();
		
		// do not cluster if the target ClusterElement is already there
		String cePath = Config.get_location_CE()+"/"+hashtagToCluster+".ser";
		if (new File(cePath).exists()) {
			return;
		}

		try {
			jCases=reader.read("files/serializedCases/"+hashtagToCluster);
			System.out.println(hashtagToCluster);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		OrthographyCluster oCluster= new OrthographyCluster();
		//find gazeteer for orthographic lexicon on senses
		orthographyClusters= oCluster.cluster(jCases);
		System.out.println("gefundene Orthographiecluster: "+orthographyClusters);
		//find frequency distribution of clusters (uses orthography lexicon)
		fq= new FrequencyFinder(jCases).getFrequency(orthographyClusters);
		System.out.println("Most 40 frequent senses (orthographically cleaned): "+fq.getMostFrequentSamples(40));
		//annotate orthography and frequency to cases
		jCases= annotateSenseFrequency(jCases);
		//form ClusterElements.class
		ClusterElement clusterElement=createClusterElementsNaive(jCases, null, null);
		
		/**
		 * uncomment for testing
		*
		* UndirectedSparseGraph<String, String> graph=calcGraph(clusterElement);
		* visualize(graph);
		*/
		
		serialize(clusterElement);
		System.out.println("Serialization finished");
//	    printCluster(clusterElement);
	}

	/**
	 * binary serialization Java6-style
	 */
	private void serialize(ClusterElement clusterElement) {
		FileOutputStream fs = null;
		ObjectOutputStream os = null;
		try {
			
			fs = new FileOutputStream(Config.get_location_CE()+"/"+hashtagToCluster+".ser");
			os = new ObjectOutputStream(fs);
			os.writeObject(clusterElement);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (fs != null) {
				try {
					fs.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	/**
	 * in this step the orthography-clustering and the co-occurence graph is performed and merged together
	 */
	private static List<JCas> annotateSenseFrequency(List<JCas> jCases) {
		
		for(JCas jcas : jCases){
			
			try {
				AggregateBuilder builder = new AggregateBuilder();
				builder.add(createEngineDescription(CleanedSenseAnnotator.class));
				builder.add(createEngineDescription(SenseAnnotator.class));
				AnalysisEngine engine = builder.createAggregate();
				engine.process(jcas);
				/**
				 * un-comment for additional sysos
				 
//				System.out.println("Tweet: "+jcas.getDocumentText());
//				for (Sentence sentence : JCasUtil.select(jcas, Sentence.class)) {
//				for (NP nounphrase : JCasUtil.selectCovered(jcas, NP.class,sentence)) {
//					System.out.println("found namephrase: "+nounphrase.getCoveredText());
//					
//					}
//				}
//				System.out.println("Original text: "+jcas.getDocumentText());
//		        FSIterator<AnnotationFS> annotationIterator = jcas.getCas().getAnnotationIndex().iterator();
//		        while (annotationIterator.hasNext()) {
//		                AnnotationFS annotation = annotationIterator.next();
//		                System.out.println("[" + annotation.getCoveredText() + "]");
//		                System.out.println(annotation.toString());
//		        }
				*/
				
			} catch (ResourceInitializationException e) {
				e.printStackTrace();
			} catch (AnalysisEngineProcessException e) {
				e.printStackTrace();
			}
		}
		
		return jCases;
	}

	//  prepare a jung graph for visualization
	private UndirectedSparseGraph<String, String> calcGraph(ClusterElement clusterElement) {
		UndirectedSparseGraph<String, String> g = new UndirectedSparseGraph<String, String>();
		
		g=addNodes(clusterElement,g);
		g=addEdges(clusterElement,g);
		return g;
	}
	// add all edges
	private static UndirectedSparseGraph<String, String> addEdges(ClusterElement clusterElement,UndirectedSparseGraph<String, String> g) {
		if (!clusterElement.getSubcluster().isEmpty()) {
			for (ClusterElement c : clusterElement.getSubcluster()) {
				g.addEdge(clusterElement.getName()+c.getName(), clusterElement.getName(),c.getName());
				g=addEdges(c,g);
			}
		}
		return g;
	}

	//adds all nodes to the graph
	private static UndirectedSparseGraph<String, String> addNodes(ClusterElement clusterElement,UndirectedSparseGraph<String, String> g) {
		g.addVertex(clusterElement.getName());
		if (!clusterElement.getSubcluster().isEmpty()) {
			for (ClusterElement c : clusterElement.getSubcluster()) {
				g=addNodes(c,g);
			}
		}
		return g;
	}

	// show the graph with gui
	private static void visualize(UndirectedSparseGraph<String, String> g) {
		Layout<String, String> layout = new FRLayout(g);
		 layout.setSize(new Dimension(300,300));
		 VisualizationViewer<String,String> vv = new VisualizationViewer<String,String>(layout);
		 vv.setPreferredSize(new Dimension(350,350));
		 // Show vertex and edge labels
		 vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller());
		 vv.getRenderContext().setEdgeLabelTransformer(new ToStringLabeller());
		 // Create a graph mouse and add it to the visualization component
		 DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
//		 gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
		 vv.setGraphMouse(gm); 
		 JFrame frame = new JFrame("Interactive Graph View 1");
		 frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		 frame.getContentPane().add(vv);
		 frame.pack();
		 frame.setVisible(true);
	}
// print all found clusters
	private void printCluster(ClusterElement clusterElement) {
		System.out.println("Cluster:"+clusterElement.getName());
		if (!clusterElement.getSubcluster().isEmpty()) {
			for (ClusterElement c : clusterElement.getSubcluster()) {
				printCluster(c);
			}
		}
	}

	/**
	 * this method recursively creates our ClusterElement.class-format
	 * it adds associated posts with getSortedSubTweets()
	 * and by recursion the sub-clusters 
	 */
	private ClusterElement createClusterElementsNaive(List<JCas> jCases,String clusterName,List<String> headers) {
		ClusterElement c;
		FrequencyDistribution<String> frequencydistribution;
		List<ClusterElement> subClusters = new ArrayList<ClusterElement>();
		if (clusterName == null) {
			//first Cluster gets the name "TopCluster"
			c = new ClusterElement("TopCluster", new Sentiment(0,0), null);
			frequencydistribution=fq;
			headers = new ArrayList<String>();
		} else {
			c = new ClusterElement(clusterName, calcSentiment(jCases), null);
			frequencydistribution=creatfd(jCases,clusterName,headers);
			//System.out.println(clusterName+" Max Frq:"+frequencydistribution.getSampleWithMaxFreq());
		}
		//set the posts of the clusterelement
		c.setPosts(getSortedSubTweets(jCases));
		
		for (String subClusterName: frequencydistribution.getMostFrequentSamples(18)){
			headers.add(subClusterName);
			ClusterElement subCluster=createClusterElementsNaive(getSubset(jCases,subClusterName),subClusterName,headers);
			subClusters.add(subCluster);
		}
		//adds the subclusters to the parent-element
		c.setSubcluster(subClusters);
		
		return c;
	}

	/**
	 * calc the mean Sentiment over a list of cases. 
	 */
	private Sentiment calcSentiment(List<JCas> jCases) {
		
		double totalPositive = 0;
		double totalNegative = 0;
		
		//iterate over all cases and find for each a sentiment (positiv/negative) 
		for(JCas jcas: jCases){
			List<SentimentAnno> annos = new ArrayList<SentimentAnno>(select(jcas,SentimentAnno.class));
			double positive = 0;
			double negative = 0;
			//iterate over all Sentiment annos and sum positive and negative
			for(SentimentAnno anno: annos){
				double sentiment=anno.getSentimentValue();
				if(sentiment>0){
					positive+=sentiment;
				}
				else{
					negative+=sentiment;
				}
			}
			int lenght=select(jcas,Token.class).size();
			//normalize through length of all tokens
			totalPositive+=positive/lenght;
			totalNegative+=negative/lenght;
		}
		//normalize through size of all cases
		totalPositive=totalPositive/jCases.size();
		totalNegative=totalNegative/jCases.size();
		
		return new Sentiment(totalPositive,totalNegative);
	}
	/**
	 * gets the ordered List of sub-tweets 
	 */
	private HashMap<String, Double> getSortedSubTweets(List<JCas> jCases) {
		HashMap<String, Double> orderedposts= new HashMap<String, Double>();
		Map<String,Double> unOrderedPosts= new HashMap<String,Double>();
		for(JCas jcas: jCases){
			unOrderedPosts.put(jcas.getDocumentText(), getCASSentiment(jcas));
		}
		orderedposts=sortMap(unOrderedPosts);
		return orderedposts;
	}
	/**
	 * orderes the map and gives the List back
	 */
private HashMap<String, Double> sortMap(Map<String, Double> unOrderedPosts) {
		HashMap<String, Double> orderedPosts = new HashMap<String, Double>();
		List<String> keys = new ArrayList<String>(unOrderedPosts.keySet());
		List<Double> values = new ArrayList<Double>(unOrderedPosts.values());
		TreeSet<Double> sortedMap = new TreeSet<Double>(values);
		Object[] sortedSet = sortedMap.toArray();
		for (int i = 0; i < sortedSet.length; i++) {
			String candidate=keys.get(values.indexOf(sortedSet[i]));
			if(!orderedPosts.containsKey(candidate)){
				orderedPosts.put(candidate,unOrderedPosts.get(candidate));
			}
		}
		return orderedPosts;
	}
/**
 * gets the mean sentiment of a single CAS
 */
	private Double getCASSentiment(JCas jcas) {
		List<SentimentAnno> annos = new ArrayList<SentimentAnno>(select(jcas,SentimentAnno.class));
		double sentiment = 0;
		//iterate over all Sentiment annos and sum them up
		for(SentimentAnno anno: annos){
			sentiment+=anno.getSentimentValue();
		}
		int lenght=select(jcas,Token.class).size();
		//normalize through length of all tokens
		sentiment=sentiment/lenght;
		return sentiment;
	}
	/**
	 * creates a frequenzy-distribution by the frequenzy of the keyphrases contained in the cases.
	 * This method is used for sub-clusters instead the original freuqncy-distribution 
	 * (because it only uses a part of all CASes)
	 */
	private FrequencyDistribution<String> creatfd(List<JCas> jCases,
			String clusterName, List<String> headers) {
		FrequencyDistribution<String> frequencydistribution= new FrequencyDistribution<String>();
		for(JCas jcas : jCases){
			FSIndex senseIndex = jcas.getAnnotationIndex(SenseAnno.type);
			Iterator senseIterator = senseIndex.iterator();
			// gets all CleanedSenseAnnos from jcas
			while (senseIterator.hasNext()) {
				SenseAnno sense = (SenseAnno) senseIterator.next();
				String senseValue=sense.getSenseValue();
				if(!headers.contains(senseValue)){
					frequencydistribution.inc(senseValue);
				}
			}
		}
		return frequencydistribution;
	}
	/**
	 * returns the subset of CASes that belong to a specific header.
	 */
	private List<JCas> getSubset(List<JCas> jCases,String name) {
		List<JCas> subset= new ArrayList<JCas>();
		for(JCas jcas : jCases){
			FSIndex senseIndex = jcas.getAnnotationIndex(SenseAnno.type);
			Iterator senseIterator = senseIndex.iterator();
			// gets all CleanedSenseAnnos from jcas
			while (senseIterator.hasNext()) {
				SenseAnno sense = (SenseAnno) senseIterator.next();
				String senseValue=sense.getSenseValue();
				if(senseValue.equals(name)){
					subset.add(jcas);
				}
			}
		}
		return subset;
	}
}
