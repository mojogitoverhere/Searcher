import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
//import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


public class Searcher {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		String indexDir = "C:/Users/ASUS/Desktop/lucene-test";
		
		Searcher searcher = new Searcher(indexDir);
		try {
			searcher.search("los angeles", 10);
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("done!");
	}
	
	// Data Members
	private static IndexSearcher searcher;
	private static IndexReader reader;
	private static Version luceneVersion = Version.LUCENE_5_0_0;
	
	Searcher(String indexDir)
	{
		try {
			// Create an IndexReader to read from indexDir directory
			File indexDirFile = new File(indexDir);
			reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDirFile.toURI())));
		
			// Create an IndexSearcher to search the directory using the IndexReader
			searcher = new IndexSearcher(reader);
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public void search(String queryText, int numResults) throws ParseException, IOException
	{
		// Setup analyzer for the QueryParser to use
		StandardAnalyzer analyzer = new StandardAnalyzer();
		analyzer.setVersion(luceneVersion);
		
		// Create a QueryParser to parse query
		QueryParser parser = new QueryParser("body", analyzer);
		Query query = parser.parse(queryText); //throws ParseException
		
		// Search and store the top results in TopDocs
		TopDocs results = searcher.search(query, numResults); //throws IOException
		
		// Save the results as an XML doc
		String xml = getResultsInXML(results, query);
		System.out.println(xml);
		/*
		int i = 1;
		for(ScoreDoc scoreDoc : results.scoreDocs)
		{
			//Term vector test
			Terms termVector = reader.getTermVector(scoreDoc.doc, "body");
			if(termVector == null)
			{
				System.out.println("We have no terms!");
			}
			else
			{
				System.out.println("We have terms !!!");
			}
			
			
			org.apache.lucene.document.Document doc = searcher.doc(scoreDoc.doc);
			System.out.println(doc.get("title"));
			System.out.println(getSnippet(scoreDoc, query));
			System.out.println(doc.get("url"));
			System.out.println(doc.get("fullpath"));
			System.out.println(".............");
			
			i++;
			
		}
		*/
	}
	
	String getSnippet(ScoreDoc scoreDoc, Query query)
	{
		String snippet = null;
		
		// Highlighter finds the best snippet and adds <b> tags to found query terms
		FastVectorHighlighter highlighter = new FastVectorHighlighter(true, false);
		try {
			FieldQuery fieldQuery = highlighter.getFieldQuery(query);
			snippet = highlighter.getBestFragment(fieldQuery,
													reader,	
													scoreDoc.doc,
													"body",
													100);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return snippet;
	}
	
	String getResultsInXML(TopDocs results, Query query)
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try{
			//Create a new document
			docBuilder = factory.newDocumentBuilder();
			org.w3c.dom.Document xmlDoc = docBuilder.newDocument();
			Element root = xmlDoc.createElementNS
					("http://thisdoesntreallymatter/butweneedit", "Results");
			xmlDoc.appendChild(root);
			
			//Add each result as a child Node of the root
			Integer i = 1;
			for(ScoreDoc scoreDoc : results.scoreDocs)
			{
				root.appendChild(getResultNode(xmlDoc, scoreDoc, Integer.toString(i), query));
				i++;
			}
			
			//Return DOM structure as a String
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(xmlDoc);
			StringWriter stringWriter = new StringWriter(1000);
			StreamResult xmlString = new StreamResult(stringWriter);
			transformer.transform(source, xmlString);
			return stringWriter.toString();
			
 		} catch(Exception e) {
 			e.printStackTrace();
 		}
		return "";
	}
		
	Node getResultNode(org.w3c.dom.Document xmlDoc, ScoreDoc scoreDoc, String id, Query query)
	{
		Element result = null;
		try {
			// Get the Lucene Document from the searcher
			org.apache.lucene.document.Document doc = searcher.doc(scoreDoc.doc);
			
			// Create a node for the next result
			result = xmlDoc.createElement("Result");
			result.setAttribute("id", id);
			
			// Create three child nodes for the title, body, and url
			result.appendChild(getNode(xmlDoc, "Title", doc.get("title")));
			result.appendChild(getNode(xmlDoc, "Body", getSnippet(scoreDoc, query)));
			result.appendChild(getNode(xmlDoc, "Url", doc.get("url")));
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	
		return result;
	}
	
	Node getNode(org.w3c.dom.Document xmlDoc, String name, String value)
	{
		Element node = xmlDoc.createElement(name);
		if(name.equals("Body") || name.equals("Url") || name.equals("Title"))
			// Use CDATA so that <b>..</b> tags are not encoded
			node.appendChild(xmlDoc.createCDATASection(value));
		else
			node.appendChild(xmlDoc.createTextNode(value));
		return node;
	}
}
