


import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.Cursor;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ParallelScanOptions;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import jgibblda.Estimator;
import jgibblda.Inferencer;
import jgibblda.LDACmdOption;
import jgibblda.Model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;

import static java.util.Arrays.asList;

import com.opencsv.CSVReader;


// for lda
//package jgibblda;

import org.kohsuke.args4j.*;

public class MyFirstTest {	
	// old test
	public void parseTree(Tree tree,Vector<String> vecKeys) {

		Tree[] children = tree.children();

		// Check the label of any sub tree to see whether it is what you want (a
		// phrase)
		for (Tree child : children) {
			if (child.value().equals("NP")||child.value().equals("NN")||child.value().equals("NNS")){
				String str="";
				List<Tree> leaves = child.getLeaves(); // leaves correspond to
				// the tokens
				for (Tree leaf : leaves) {
					List<Word> words = leaf.yieldWords();
					for (Word word : words)
						if(str.isEmpty()) str+=word.word();
						else str+=(" "+word.word());
				}
				
				if(str.indexOf(" and ")>0){
					String[] arr=str.split(" and ");
					for(String strSeg:arr){
						vecKeys.addElement(strSeg.trim());	
					}
					
				}
				else{
					vecKeys.addElement(str.trim());					
				}
			}
			/*
			if (child.value().equals("NP")||child.value().equals("NN")||child.value().equals("NNS")) {
				System.out.println(child.value() + "===");
				List<Tree> leaves = child.getLeaves(); // leaves correspond to
														// the tokens
				for (Tree leaf : leaves) {
					List<Word> words = leaf.yieldWords();
					for (Word word : words)
						System.out.print(String.format("(%s),", word.word()));
				}
				System.out.println();

			}
			*/
			parseTree(child,vecKeys);
		}
	}

	void tryToUpdateMongoDB(){

		
		// get collection
		DBCollection coll = db.getCollection("nodes");
		
		
		BasicDBObject newDocument = new BasicDBObject();
		newDocument.put("name", "newDD");
		newDocument.put("year", "2016");

		BasicDBObject searchQuery = new BasicDBObject().append("name", "newDD");

		coll.update(searchQuery, newDocument);
		
		
	}

	void testParseTree(){
		Vector<String> vecKeys=new Vector<String>();
		
		// 构造一个StanfordCoreNLP对象，配置NLP的功能，如lemma是词干化，ner是命名实体识别等
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

		// 待处理字符串
		//String text = "judy has been to china . she likes people there . and she went to Beijing ";
		String text = "Spatial ability and visual navigation: An empirical study";
		

		// 创造一个空的Annotation对象
		Annotation document = new Annotation(text);

		// 对文本进行分析
		pipeline.annotate(document);

		// 获取文本处理结果
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		for (CoreMap sentence : sentences) {
			Tree tree = sentence.get(TreeAnnotation.class);
			tree.pennPrint();
			parseTree(tree,vecKeys);
		}
		

		for(String str : vecKeys){
			System.out.println(str);
		}
		
	}
	// ~old test
	

	
	// the database client
	MongoClient client = new MongoClient( "localhost" , 27018 );

	// get db
	DB db = client.getDB( "surveyVis" );
	

	
	// the structure of the article in database
	// it is used to store the data, as the data should be rewrited into the database
	class Article{
		public String _name;
		public String _source;
		public String _abstract;
		public String _authors;
		public String _keywords;
		public String _notes;
		public String _parsedTitle;
		public String _parsedAbstract;
		public String _refinedAbstract;		// refined result for parsed abstract
		public int _year;
		public int _type;
		
		public int _updated;				// updated by dblp
		public String _originalName;		// name before update
		public String _originalAuthors;		// authors before update 
		public String _dblpID;				// the id of the related items in dblp
		
		public List<String> _listAuthors=new ArrayList<String>();		// the author list
	}

	// the map of the article ids to the articles
	private Hashtable<Object, Article> tbArticles=new Hashtable<Object, Article>();
	
	/*
	 * read the data in the data base, parse the title, and write the result into the database.
	 */
	void parseTitle(){
		// 1.nlp initialization
		// 构造一个StanfordCoreNLP对象，配置NLP的功能，如lemma是词干化，ner是命名实体识别等
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
				
				

		
		// get collection
		DBCollection coll = db.getCollection("nodes");
		
		// read all
		DBCursor cursor = coll.find();
		System.out.println(coll.count());  
		while (cursor.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursor.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._source=obj.containsField("source")?obj.get("source").toString():"";
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
			article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
			article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
			article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
			article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
			
			// 创造一个空的Annotation对象
			Annotation document = new Annotation(article._name.toLowerCase());

			// 对文本进行分析
			pipeline.annotate(document);

			// 获取文本处理结果
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			String parsedTitle="";
			for (CoreMap sentence : sentences) {
				Tree tree = sentence.get(TreeAnnotation.class);


				Vector<String> vecKeys=new Vector<String>();
				//System.out.println(article._name+"->");
				//tree.pennPrint();
				parseTree(tree,vecKeys);
				for(String str : vecKeys){
					if(str.length()>3&&str.length()<30){
						parsedTitle+=str;
						parsedTitle+=";";
					}
				//	System.out.println(str);
				}
				
				//System.out.println();
			}
			//System.out.println(parsedTitle);
			article._parsedTitle=parsedTitle.isEmpty()?"":parsedTitle.substring(0,parsedTitle.length()-1);
			tbArticles.put(id, article);
		}  
		
		cursor.close(); 
		
		// 3.update the data		
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			

			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", a._name);
			newDocument.put("source", a._source);
			newDocument.put("abstract", a._abstract);
			newDocument.put("authors", a._authors);
			newDocument.put("keywords", a._keywords);
			newDocument.put("notes", a._notes);
			newDocument.put("year", a._year);
			newDocument.put("type", a._type);
			newDocument.put("parsedTitle", a._parsedTitle);
			newDocument.put("parsedAbstract", a._parsedAbstract);

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

			coll.update(searchQuery, newDocument);
		}
		

		
		/*
		 e = tbArticles.keys();
	      while (e.hasMoreElements()){
	    	  Object key=e.nextElement();
	    	  Article a=tbArticles.get(key);
	         System.out.println(key);
	         System.out.println(a._name);
	      }
	      */
		
	}
	
	// match the the items in nodes with the collections in dblp
	void matchCollection(){
		// 1.read the data and parse title

		
		// get collection
		DBCollection colNodes = db.getCollection("nodes");
		DBCollection colDblp = db.getCollection("dblp");
		
		// read all
		DBCursor cursorNodes = colNodes.find();

		System.out.println(colNodes.count());  

		while (cursorNodes.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursorNodes.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._source=obj.containsField("source")?obj.get("source").toString():"";
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
			article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
			article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
			article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
			article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
			
			tbArticles.put(id, article);
		}  
		
		cursorNodes.close(); 
		
		// 2.search the articles in dblp
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			boolean bFind=false;
			DBCursor cursorDblp = colDblp.find();
			while (cursorDblp.hasNext()) {  
				DBObject obj=cursorDblp.next();
				String strTitle=obj.get("title").toString().toLowerCase();
				int year=Integer.parseInt(obj.get("year").toString());
				if(year==a._year&&strTitle.indexOf(a._name.toLowerCase())>-1){
					bFind=true;
					continue;
				}
			}  
			if(!bFind){
				System.out.println(a._year);
				System.out.println(a._name);				
			}
		}
	}


	/* update the matched collection according to DBLP data
	 * 1.load all nodes
	 * 2.find the matched item in dblp for each nodes
	 * 3.update the nodes according to the matched dblp	 * 
	 * 2016/10/22
	 * Modify: just record the original name and author if it has not been matched
	 * 20161025
	 * Modify: check the length of the titles
	 * */
	void updateCollectionsAccordingToDBLP(){	
		try(  PrintWriter out = new PrintWriter( "log.txt" )){

			// 0.delcaration
			DBCollection colNodes = db.getCollection("nodes");
			DBCollection colDblp = db.getCollection("dblp");

			// 1.read all nodes into tbArticles
			DBCursor cursorNodes = colNodes.find();

			while (cursorNodes.hasNext()) {  
				Article article=new Article();
				DBObject obj=cursorNodes.next();
				Object id=obj.get("_id");
				article._name=obj.get("name").toString();
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
				article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
				
				article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
				article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
				article._originalAuthors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
				article._dblpID=obj.containsField("dblpID")?obj.get("dblpID").toString():"";
				
				tbArticles.put(id, article);
			}  
			System.out.println(tbArticles.size());
			
			cursorNodes.close(); 
			// current handled item index
			int index=0;
			// matched item count
			int count=0;
			// 2.search the articles in dblp
			Enumeration e = tbArticles.keys();
			while (e.hasMoreElements()){

				System.out.println(index++);
				Object key=e.nextElement();
				Article a=tbArticles.get(key);
				String strNodeName=a._name.toLowerCase();
				DBCursor cursorDblp = colDblp.find();
				while (cursorDblp.hasNext()) {  
					DBObject obj=cursorDblp.next();
					String strTitle=obj.get("title").toString().toLowerCase();
					int year=Integer.parseInt(obj.get("year").toString());
//					if(year==a._year&&
//							(strTitle.indexOf(strNodeName)>-1
//									||strNodeName.indexOf(strTitle)>-1)&&
//							Math.abs(strTitle.length()-strNodeName.length())<Math.min(strTitle.length(),strNodeName.length())
//							){

					if(year==a._year&&matchTitle(strTitle,strNodeName)){
						if(a._updated==0){
							a._updated=1;
							a._originalName=a._name;
							a._originalAuthors=a._authors;						
						}
						a._dblpID=obj.get("_id").toString();
						a._name=obj.get("title").toString();

//						System.out.println(a._name);
						
						BasicDBList list = (BasicDBList) obj.get("author");
						if(list!=null&&!list.isEmpty()){
							List<String> authors = new ArrayList<String>();

							for(Object el: list) {
								authors.add((String) el);
								a._listAuthors.add((String) el);
							}
							
							a._authors=String.join(";", authors);						
						}
//						System.out.println(a._originalName);
//						System.out.println(a._name);
//						System.out.println(a._originalAuthors);
//						System.out.println(a._authors);
						out.write(a._originalName+"\n");
						out.write(a._name+"\n");
						count++;
					}
				}  
			}
			out.write("matched count: "+count+"\n");
			
			// 3.update the articles
			e = tbArticles.keys();
			while (e.hasMoreElements()){
				Object key=e.nextElement();
				Article a=tbArticles.get(key);
				if(a._updated==1){

					BasicDBObject newDocument = new BasicDBObject();
					newDocument.put("name", a._name);
					newDocument.put("source", a._source);
					newDocument.put("abstract", a._abstract);
					newDocument.put("authors", a._authors);
					newDocument.put("keywords", a._keywords);
					newDocument.put("notes", a._notes);
					newDocument.put("year", a._year);
					newDocument.put("type", a._type);
					newDocument.put("parsedTitle", a._parsedTitle);
					newDocument.put("parsedAbstract", a._parsedAbstract);
					// updated Data
					newDocument.put("originalName", a._originalName);
					newDocument.put("originalAuthors", a._originalAuthors);
					newDocument.put("updated", a._updated);
					newDocument.put("dblpID", a._dblpID);

					newDocument.put("listAuthors", a._listAuthors);

					BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

					colNodes.update(searchQuery, newDocument);
				}
			}
	
		}catch (IOException ex) {
		  // report
		} finally {
		}
		
	}


	// update the authors of matched collection according to DBLP data by id
	void updateAuthorsById(){

		// get collection
		DBCollection colNodes = db.getCollection("nodes");
		DBCollection colDblp = db.getCollection("dblp");
		// read all
		DBCursor cursorNodes = colNodes.find();

		while (cursorNodes.hasNext()) {  
			DBObject obj=cursorNodes.next();
			if(obj.containsField("dblpID")){
				Article article=new Article();
				Object id=obj.get("_id");
				article._name=obj.get("name").toString();
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
				article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
				

				article._updated=Integer.parseInt(obj.get("updated").toString());
				article._originalName=obj.get("originalName").toString();
				article._originalAuthors=obj.get("originalAuthors").toString();
				article._dblpID=obj.get("dblpID").toString();
				
				tbArticles.put(id, article);				
			}
		}  
		
		cursorNodes.close(); 
		
		// 2.search the articles in dblp
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key); 
			
			BasicDBObject query = new BasicDBObject();
			query.put("_id", new ObjectId(a._dblpID));
			DBCursor cursorDblp = colDblp.find(query);
			while (cursorDblp.hasNext()) { 
				DBObject obj=cursorDblp.next();
				System.out.println(obj.get("title").toString());
				BasicDBList list = (BasicDBList) obj.get("author");
				if(list!=null&&!list.isEmpty()){
					for(Object el: list) {
						a._listAuthors.add((String) el);
					}					
				}

			}  
		}
		
		// 3.update the articles
		e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			if(a._updated==1){
				
				BasicDBObject newDocument = new BasicDBObject();
				newDocument.put("name", a._name);
				newDocument.put("source", a._source);
				newDocument.put("abstract", a._abstract);
				newDocument.put("authors", a._authors);
				newDocument.put("keywords", a._keywords);
				newDocument.put("notes", a._notes);
				newDocument.put("year", a._year);
				newDocument.put("type", a._type);
				newDocument.put("parsedTitle", a._parsedTitle);
				newDocument.put("parsedAbstract", a._parsedAbstract);
				// updated Data
				newDocument.put("originalName", a._originalName);
				newDocument.put("originalAuthors", a._originalAuthors);
				newDocument.put("updated", a._updated);
				newDocument.put("dblpID", a._dblpID);
				newDocument.put("listAuthors", a._listAuthors);

				BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

				colNodes.update(searchQuery, newDocument);
			}
		}
	}
	

	// search a name in DBLP
	void searchDBLP(String strTitle){

		
		// get collection
		DBCollection colDblp = db.getCollection("dblp");
		

		BasicDBObject query = new BasicDBObject();
		query.put("title", strTitle);
		DBCursor cursorDblp = colDblp.find(query);
		while (cursorDblp.hasNext()) { 
			DBObject obj=cursorDblp.next();
			System.out.println(obj.get("title").toString());
		}  
	}
	


	/* incrementally update the matched collection according to DBLP data
	 * ignore the already matched items
	 * 1.load all nodes
	 * 2.find the matched item in dblp for each nodes
	 * 3.update the nodes according to the matched dblp	 * 
	 * 2016/10/22
	 * */
	void updateCollectionsAccordingToDBLPIncrementally(){
		try(  PrintWriter out = new PrintWriter( "log.txt" )){
			// 0.delcaration
			DBCollection colNodes = db.getCollection("nodes");
			DBCollection colDblp = db.getCollection("dblp");

			// 1.read all nodes into tbArticles
			DBCursor cursorNodes = colNodes.find();

			while (cursorNodes.hasNext()) {  
				Article article=new Article();
				DBObject obj=cursorNodes.next();
				Object id=obj.get("_id");
				article._name=obj.get("name").toString();
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
				article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
				
				article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
				if(article._updated==0)	// only update the none updated data
					tbArticles.put(id, article);
			}  
			
			cursorNodes.close(); 

			System.out.print("Number of not matched items:"+tbArticles.size()+"\n");
			out.write("Number of not matched items:"+tbArticles.size()+"\n");
			
			// 2.search the articles in dblp
			Enumeration e = tbArticles.keys();
			int i=0;
			while (e.hasMoreElements()){
				System.out.println(i++);
				Object key=e.nextElement();
				Article a=tbArticles.get(key);
				String strNodeName=a._name.toLowerCase();
				DBCursor cursorDblp = colDblp.find();
				while (cursorDblp.hasNext()) {  
					DBObject obj=cursorDblp.next();
					String strTitle=obj.get("title").toString().toLowerCase();
					int year=Integer.parseInt(obj.get("year").toString());
//					if(year==a._year&&
//							(strTitle.indexOf(strNodeName)>-1
//									||strNodeName.indexOf(strTitle)>-1)&&
//							Math.abs(strTitle.length()-strNodeName.length())<Math.min(strTitle.length(),strNodeName.length())){
					if(year==a._year&&matchTitle(strTitle,strNodeName)){
						a._updated=1;
						a._dblpID=obj.get("_id").toString();
						a._originalName=a._name;
						a._name=obj.get("title").toString();
						a._originalAuthors=a._authors;

//						System.out.println(a._name);
						
						BasicDBList list = (BasicDBList) obj.get("author");
						if(list!=null&&!list.isEmpty()){
							List<String> authors = new ArrayList<String>();

							for(Object el: list) {
								authors.add((String) el);
								a._listAuthors.add((String) el);
							}
							
							a._authors=String.join(";", authors);						
						}
//						System.out.println(a._originalName);
//						System.out.println(a._name);
//						System.out.println(a._originalAuthors);
//						System.out.println(a._authors);
//						out.write(a._originalName+"\n"+a._name+"\n");
						continue;
					}
				}  
			}
			ArrayList<Article> updatedArticles= new ArrayList<Article>();
			out.write("============Not Matched============\n");
			// 3.update the articles
			e = tbArticles.keys();
			while (e.hasMoreElements()){
				Object key=e.nextElement();
				Article a=tbArticles.get(key);
				if(a._updated==1){

					BasicDBObject newDocument = new BasicDBObject();
					newDocument.put("name", a._name);
					newDocument.put("source", a._source);
					newDocument.put("abstract", a._abstract);
					newDocument.put("authors", a._authors);
					newDocument.put("keywords", a._keywords);
					newDocument.put("notes", a._notes);
					newDocument.put("year", a._year);
					newDocument.put("type", a._type);
					newDocument.put("parsedTitle", a._parsedTitle);
					newDocument.put("parsedAbstract", a._parsedAbstract);
					// updated Data
					newDocument.put("originalName", a._originalName);
					newDocument.put("originalAuthors", a._originalAuthors);
					newDocument.put("updated", a._updated);
					newDocument.put("dblpID", a._dblpID);

					newDocument.put("listAuthors", a._listAuthors);

					BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

					colNodes.update(searchQuery, newDocument);
					
					updatedArticles.add(a);
				}
				else{
					out.write(a._name+"\n");
				}			
			}
			out.write("============Matched============"+updatedArticles.size()+"\n");
			for(Article a:updatedArticles){
				out.write(a._originalName+"\n"+a._name+"\n");
			}
		

		
		}catch (IOException ex) {
		  // report
		} finally {
		}
	}

	//==================VIS Data============================
	
	/* store paper data from visualization publication data
	 * refined 2016/11/11
	 */	 
	class VisPaper{
		public String _conference="";
		public int _year=0;
		public String _title="";
		public String _doi="";
		public String _link="";
		public String _abstract="";
		public String _authors="";		
		public String _references="";
		public String _keywords="";
		
		public String _id="";
	}


	// the map of the vispaper titles to the vispaper
	private Hashtable<String, VisPaper> tbTitle2Paper=new Hashtable<String, VisPaper>();
	// the map of the vispaper number to the vispaper
	private Hashtable<String, VisPaper> tbDOI2Paper=new Hashtable<String, VisPaper>();
	
	/* load the visualization publication data
	 * store in the above struct
	 * refined 2016/11/11
	 */
	void loadVisualizationPublicationData(){
        String csvFile = "F:/Data/Visualization Publication Data/IEEE VIS papers 1990-2015 - Main dataset.csv";
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(csvFile));
            String[] fields = reader.readNext();	// skip the first line
            while ((fields = reader.readNext()) != null) {
            	VisPaper vp=new VisPaper();
            	vp._conference=fields[0];
            	vp._year=Integer.parseInt(fields[1]);
            	vp._title=fields[2];
            	vp._doi=fields[3];
            	vp._link=fields[4];
            	vp._abstract=fields[9];
            	vp._authors=fields[10];
            	vp._references=fields[14];
            	vp._keywords=fields[15];
            	tbTitle2Paper.put(vp._title, vp);
            	tbDOI2Paper.put(vp._doi, vp); 
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	/* read the data from database to tbPapers
	 * updated 2016/11/11
	 */
	void readDataFromDatabase(){

		// get collection
		DBCollection colNodes = db.getCollection("nodes");
		DBCollection colDblp = db.getCollection("dblp");
		
		// read all
		DBCursor cursorNodes = colNodes.find();

		while (cursorNodes.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursorNodes.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._source=obj.containsField("source")?obj.get("source").toString():"";
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
			article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
			article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
			article._parsedTitle=obj.containsField("parsedTitle")?obj.get("parsedTitle").toString():"";
			article._parsedAbstract=obj.containsField("parsedTitle")?obj.get("parsedAbstract").toString():"";
			article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
			article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
			
			article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
			article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
			article._originalAuthors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
			article._dblpID=obj.containsField("dblpID")?obj.get("dblpID").toString():"";
			
			tbArticles.put(id, article);
		}  
		
		cursorNodes.close(); 
	}
	
	/* match the vis data with the data in the database
	 * 2016/11/11
	 */
	void matchVisData(){

		Enumeration e = tbTitle2Paper.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			VisPaper vp=tbTitle2Paper.get(key);
			String strPaperTitle=vp._title.toLowerCase();
			int year=vp._year;
			Enumeration eArticles = tbArticles.keys();			
			while (eArticles.hasMoreElements()){
				Object keyArticles=eArticles.nextElement();
				Article a=tbArticles.get(keyArticles);
				String strArticleTitle=a._name.toLowerCase();
				if((strArticleTitle.indexOf(strPaperTitle)>-1||strPaperTitle.indexOf(strArticleTitle)>-1)
						&& (year==a._year)){
					vp._id=keyArticles.toString();
					break;
				}
			}
		}
	}
	
	/* update the data base by the visualization publication data
	 * separated from article adding because the data should be reloaded
	 * updated 2016/11/11
	 */
	void addVisReference(){
		
		// add reference
		DBCollection coll = db.getCollection("references");
		Enumeration e = tbTitle2Paper.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			VisPaper vp=tbTitle2Paper.get(key);
			String[] refs=vp._references.split(";");
			if(vp._id!=null){
				for(String strRef: refs){
					VisPaper vpTo=tbDOI2Paper.get(strRef);
					if(vpTo!=null&&vpTo._id!=null){
						BasicDBObject newDocument = new BasicDBObject();
						newDocument.put("from", vp._id);
						newDocument.put("to", vpTo._id);
						coll.insert(newDocument);						
					}					
				}
			}
		}
		
	}
	
	/* add the unmatched vis data
	 * 2016/11/11
	 */
	void addUnmatchedVisPublication(){
		DBCollection coll = db.getCollection("nodes");
		Enumeration e = tbTitle2Paper.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			VisPaper vp=tbTitle2Paper.get(key);
			if(vp._id==""){			

				BasicDBObject newDocument = new BasicDBObject();
				newDocument.put("name", vp._title);
				newDocument.put("source", vp._conference);
				newDocument.put("abstract", vp._abstract);
				newDocument.put("authors", vp._authors);
				newDocument.put("keywords", vp._keywords);
				newDocument.put("notes", "");
				newDocument.put("year", vp._year);

				coll.insert(newDocument);				
			}
		}
	}

	/* add visualization publication data from file to database
	 * 2016/11/11
	 */
	void addVisData(){
		// read nodes form database
		readDataFromDatabase();
		
		// load data from file
		loadVisualizationPublicationData();
		
		// match the two collection
		matchVisData();
		
		// add the papers which are not match in the database
		addUnmatchedVisPublication();
		
		tbArticles.clear();
		tbTitle2Paper.clear();
		tbDOI2Paper.clear();
		// read nodes form database
		readDataFromDatabase();
		
		// load data from file
		loadVisualizationPublicationData();
		matchVisData();
		
		addVisReference();
		
	}

// ==================~VIS Data============================
	
	// check too short titles in DBLP data
	void checkDBLPData(){
		// 0.delcaration
		DBCollection colDblp = db.getCollection("dblp");

		DBCursor cursorDblp = colDblp.find();
		while (cursorDblp.hasNext()) {  
			DBObject obj=cursorDblp.next();
			String strTitle=obj.get("title").toString().toLowerCase();
			if(strTitle.length()<4) System.out.println(strTitle);
		}  
	}

	/* for papers with too short title, recover them to thieir original name
	 * 2016/10/25
	 * */
	void recoverToOriginalNameForTooShortTitles(){
		// 0.delcaration
		DBCollection colNodes = db.getCollection("nodes");
		DBCollection colDblp = db.getCollection("dblp");

		// 1.read all nodes into tbArticles
		DBCursor cursorNodes = colNodes.find();

		while (cursorNodes.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursorNodes.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
			article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
			
			if(article._name.equals("SoRec: social recommendation using probabilistic matrix factorization.")){
				System.out.println("Find");
			}

			
			if(article._updated==1){
				article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
				if(!matchTitle(article._name.toLowerCase(),article._originalName.toLowerCase())){

					tbArticles.put(id, article);
					article._source=obj.containsField("source")?obj.get("source").toString():"";
					article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
					article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
					article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
					article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
					article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
					article._updated=0;

					article._name=obj.containsField("originalName")?obj.get("originalName").toString():"";
					article._authors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
					
					System.out.println(article._name);
					System.out.println(article._originalName);
				}
			}
		}  
		
		cursorNodes.close(); 
		System.out.println("Number of not sick items:"+tbArticles.size());
		
		// 3.update the articles
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			
			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", a._name);
			newDocument.put("source", a._source);
			newDocument.put("abstract", a._abstract);
			newDocument.put("authors", a._authors);
			newDocument.put("keywords", a._keywords);
			newDocument.put("notes", a._notes);
			newDocument.put("year", a._year);
			newDocument.put("type", a._type);
			newDocument.put("parsedTitle", a._parsedTitle);
			newDocument.put("parsedAbstract", a._parsedAbstract);

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

			colNodes.update(searchQuery, newDocument);			
		}
	}

	/* test the matching state for a given article title
	 * */
	void testMatchingForNodes(String title,int givenYear){
		// 0.delcaration
		DBCollection colDblp = db.getCollection("dblp");
		
		String strNodeName=title.toLowerCase();
		
		DBCursor cursorDblp = colDblp.find();
		while (cursorDblp.hasNext()) {  
			DBObject obj=cursorDblp.next();
			String strTitle=obj.get("title").toString().toLowerCase();
			int year=Integer.parseInt(obj.get("year").toString());
			if(year==givenYear&&(strTitle.contains(strNodeName))){
				System.out.print(strTitle);
			}
		}  
	
	}
	
	boolean matchTitle(String t1,String t2){
		return t1.contains(t2)&&t2.length()>t1.length()*0.8;
	}

	/*
	 * replace short lines for the nodes
	 */
	void replaceShortLine(){
		// 0.delcaration
		DBCollection colNodes = db.getCollection("nodes");

		// 1.read all nodes into tbArticles
		DBCursor cursorNodes = colNodes.find();

		while (cursorNodes.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursorNodes.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			if(article._name.contains("—")){
				article._name=article._name.replaceAll("—", " - ");
				article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
				article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
				article._updated=0;

				tbArticles.put(id, article);
				
				System.out.println(article._name);
//				System.out.println(article._originalName);
				
			}
		}  
		
		cursorNodes.close(); 
		System.out.println("Number of not sick items:"+tbArticles.size());
		
		// 3.update the articles
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			
			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", a._name);
			newDocument.put("source", a._source);
			newDocument.put("abstract", a._abstract);
			newDocument.put("authors", a._authors);
			newDocument.put("keywords", a._keywords);
			newDocument.put("notes", a._notes);
			newDocument.put("year", a._year);
			newDocument.put("type", a._type);
			newDocument.put("parsedTitle", a._parsedTitle);
			newDocument.put("parsedAbstract", a._parsedAbstract);

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

			colNodes.update(searchQuery, newDocument);			
		}
	}
	
	/*
	 * basic calculation
	 */
	void basicCalculation(){
		// 0.delcaration
		DBCollection colNodes = db.getCollection("nodes");

		// 1.read all nodes into tbArticles
		DBCursor cursorNodes = colNodes.find();

		while (cursorNodes.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursorNodes.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			if(article._abstract.length()>0){
				article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
				article._updated=0;

				tbArticles.put(id, article);
				
				System.out.println(article._abstract);
				System.out.println();
//				System.out.println(article._originalName);
				
			}
		}  
		
		cursorNodes.close(); 
		System.out.println("Number of items with abstract:"+tbArticles.size());
		
	}
	
	/*
	 * parse abstract
	 * first step for LDA
	 * the different between the former approach used for title is that, the results is all the words not phrase
	 * 2016/11/05
	 */
	void parseAbstract(){
		// 1.nlp initialization
		// 构造一个StanfordCoreNLP对象，配置NLP的功能，如lemma是词干化，ner是命名实体识别等
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);	
		
		// get collection
		DBCollection coll = db.getCollection("nodes");

		int index=0;
		// read all
		DBCursor cursor = coll.find();
		System.out.println(coll.count());  
		while (cursor.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursor.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._source=obj.containsField("source")?obj.get("source").toString():"";
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
			article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
			article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
			article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
			article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;

			article._parsedTitle=obj.containsField("parsedTitle")?obj.get("parsedTitle").toString():"";
			
			article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
			article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
			article._originalAuthors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
			article._dblpID=obj.containsField("dblpID")?obj.get("dblpID").toString():"";
			
			index++;
//			System.out.println(article._name);
			if(article._abstract.isEmpty()
//					||obj.containsField("parsedAbstract")
					||index<1680
					) continue;
			
			// 创造一个空的Annotation对象
			String strAbstract=article._abstract.replaceAll("-", " ");
			Annotation document = new Annotation(strAbstract);

			// 对文本进行分析
			pipeline.annotate(document);

			// 获取文本处理结果
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			String pasedAbstract="";
	        for(CoreMap sentence: sentences) {
	             // traversing the words in the current sentence
	             // a CoreLabel is a CoreMap with additional token-specific methods
	            for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
	                // this is the text of the token
	                String word = token.get(TextAnnotation.class);
	                // this is the POS tag of the token
	                String pos = token.get(PartOfSpeechAnnotation.class);
	                // this is the NER label of the token
	                String ne = token.get(NamedEntityTagAnnotation.class);
	                String lemma = token.get(LemmaAnnotation.class);
//	                if(checkLemma(lemma)){
		            	pasedAbstract+=lemma;
		            	pasedAbstract+=" ";
//	                }
	                
	            }	            
	        }
	        
	        article._parsedAbstract=pasedAbstract;
	        System.out.println(article._name);
			System.out.println(index);
//			tbArticles.put(id, article);
			{
				BasicDBObject newDocument = new BasicDBObject();
				newDocument.put("name", article._name);
				newDocument.put("source", article._source);
				newDocument.put("abstract", article._abstract);
				newDocument.put("authors", article._authors);
				newDocument.put("keywords", article._keywords);
				newDocument.put("notes", article._notes);
				newDocument.put("year", article._year);
				newDocument.put("type", article._type);
				newDocument.put("parsedTitle", article._parsedTitle);
				newDocument.put("parsedAbstract", article._parsedAbstract);
				// updated Data
				newDocument.put("originalName", article._originalName);
				newDocument.put("originalAuthors", article._originalAuthors);
				newDocument.put("updated", article._updated);
				newDocument.put("dblpID", article._dblpID);
	
				newDocument.put("listAuthors", article._listAuthors);
	
				BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(id.toString()));
				coll.update(searchQuery, newDocument);
			}
		}  
		
		cursor.close(); 
//		index=0;
//		// 3.update the data		
//		Enumeration e = tbArticles.keys();
//		while (e.hasMoreElements()){
//			Object key=e.nextElement();
//			Article a=tbArticles.get(key);
//			
//
//			BasicDBObject newDocument = new BasicDBObject();
//			newDocument.put("name", a._name);
//			newDocument.put("source", a._source);
//			newDocument.put("abstract", a._abstract);
//			newDocument.put("authors", a._authors);
//			newDocument.put("keywords", a._keywords);
//			newDocument.put("notes", a._notes);
//			newDocument.put("year", a._year);
//			newDocument.put("type", a._type);
//			newDocument.put("parsedTitle", a._parsedTitle);
//			newDocument.put("parsedAbstract", a._parsedAbstract);
//			// updated Data
//			newDocument.put("originalName", a._originalName);
//			newDocument.put("originalAuthors", a._originalAuthors);
//			newDocument.put("updated", a._updated);
//			newDocument.put("dblpID", a._dblpID);
//
//			newDocument.put("listAuthors", a._listAuthors);
//
//			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));
//
//			coll.update(searchQuery, newDocument);
//			System.out.println(index++);
//		}
		

		
		/*
		 e = tbArticles.keys();
	      while (e.hasMoreElements()){
	    	  Object key=e.nextElement();
	    	  Article a=tbArticles.get(key);
	         System.out.println(key);
	         System.out.println(a._name);
	      }
	      */
		
	}
	
	/*
	 * repair the author field
	 */
	void tempRepair(){
		// 0.delcaration
		DBCollection colNodes = db.getCollection("nodes");

		// 1.read all nodes into tbArticles
		DBCursor cursorNodes = colNodes.find();

		while (cursorNodes.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursorNodes.next();
			Object id=obj.get("_id");
			article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
			if(obj.containsField("authors")&&obj.get("authors")==null){
				article._name=obj.get("name").toString();
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
				article._authors="";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;
				
				article._updated=0;

				tbArticles.put(id, article);
				
				System.out.println(article._name);
//				System.out.println(article._originalName);
				
			}
		}  
		
		cursorNodes.close(); 
		System.out.println("Number of not sick items:"+tbArticles.size());
		
		// 3.update the articles
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			
			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", a._name);
			newDocument.put("source", a._source);
			newDocument.put("abstract", a._abstract);
			newDocument.put("authors", a._authors);
			newDocument.put("keywords", a._keywords);
			newDocument.put("notes", a._notes);
			newDocument.put("year", a._year);
			newDocument.put("type", a._type);

			newDocument.put("updated", a._updated);

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

			colNodes.update(searchQuery, newDocument);			
		}
	}
	
	static String[] stopWords=new String[]{
			"as"
			,"at"
			,"be"
			,"by"
			,"in"
			,"it"
			,"of"
			,"on"
			,"or"
			,"to"
			,"we"

			,"and"
			,"but"
			,"can"
			,"for"
			,"how"
			,"its"
			,"not"
			,"the"
			,"use"

			,"also"
			,"both"
			,"case"
			,"from"
			,"have"
			,"into"
			,"make"
			,"more"
			,"some"
			,"such"
			,"than"
			,"that"
			,"they"
			,"then"
			,"this"
			,"when"
			,"with"

			,"these"
			,"while"

			,"between"
			
			// number
			,"one"
			,"two"
			,"three"
			,"four"
			,"five"
			,"six"
			,"seven"
			,"eight"
			,"night"
			,"ten"
			
			// 2016/11/12
			// remove words from checking the result of 3 classes
			,"which"
			,"-rrb-"
			,"-lrb-"
			,"visualization"
			,"datum"
			
			// 2016//11/13
//			,"method"
//			,"algorithm"
			,"present"
			,"base"
//			,"approach"
			
			//2016/11/15
			,"''"
			,"``"
			,"'s"
			
			,"method"
			,"technique"
			,"approach"
			,"result"
			,"high"
			,"new"
			,"provide"
			,"paper"
			,"support"
			,"large"
			,"different"
			
//			,"allow"

			// 2016/11/20
			,"-rsb-"
			,"-lsb-"
			,"each"
			,"do"
			,"over"
			,"show"		
			,"only"
			,"other"
			,"many"
			
			// 2016/12/06
			,"most"
			,"may"
			,"however"
			,"often"
			,"even"
			,"e.g."
			,"through"
			,"will"
			,"take"
			,"well"
			
			
	};
	/*
	 * check whether this lemma is useful
	 * used when parsing the abstract
	 * 2016/11/05
	 */
	boolean checkLemma(String lemma){
		if(lemma.length()<2) return false;
		if(lemma.matches("[-+]?\\d*\\.?\\d+")) return false;
		
		for(String w:stopWords){
			if(lemma.matches(w)) return false;			
		}		
		
		return true;
	}
	
	/*
	 * lda test
	 */
	void testLDA(){
		LDACmdOption option = new LDACmdOption();
		CmdLineParser parser = new CmdLineParser(option);
		
		try {
			String dir=new java.io.File( "." ).getCanonicalPath()+"/models/casestudy/";
			System.out.println(dir);
			String[] args=new String[]{
					 "-est"
					, "-dir", dir
					, "-alpha", "0.5"
					, "-beta", "0.1"
					, "-ntopics", "30"
					, "-niters", "100000"
					, "-savestep", "10000"
					, "-twords", "20"
					, "-dfile", "RefinedAbstract.txt"
//					, "-dfile", "ParsedAbstract.txt"
					};
			
//			String[] args=new String[]{
//					"-inf"
//					, "-dir"
//					, dir
//					, "-model"
//					, "model-final"
//					, "-niters"
//					, "30"
//					, "-twords"
//					, "20"
//					, "-dfile"
//					, "newdocs4.dat"
//					};
			
			if (args.length == 0){
				showHelp(parser);
				return;
			}
			
			parser.parseArgument(args);
			
			if (option.est || option.estc){
				Estimator estimator = new Estimator();
				estimator.init(option);
				estimator.estimate();
			}
			else if (option.inf){
				Inferencer inferencer = new Inferencer();
				inferencer.init(option);
				
				String [] test = {"politics bill clinton", "law court", "football match"};
				Model newModel = inferencer.inference();
			
				for (int i = 0; i < newModel.phi.length; ++i){
					//phi: K * V
					System.out.println("-----------------------\ntopic" + i  + " : ");
					for (int j = 0; j < 10; ++j){
						System.out.println(inferencer.globalDict.id2word.get(j) + "\t" + newModel.phi[i][j]);
					}
				}
			}
		}
		catch (CmdLineException cle){
			System.out.println("Command line error: " + cle.getMessage());
			showHelp(parser);
			return;
		}
		catch (Exception e){
			System.out.println("Error in main: " + e.getMessage());
			e.printStackTrace();
			return;
		}
	}
	
	void showHelp(CmdLineParser parser){
		System.out.println("LDA [options ...] [arguments...]");
		parser.printUsage(System.out);
	}
	
	/* save the parsed abstract to files
	 * 2016/11/05
	 */
	void saveParsedAbstractToFile(String field){
		
		try(  PrintWriter out = new PrintWriter( field+".txt" )){
		
			// get collection
			DBCollection coll = db.getCollection("nodes");

			int index=0;
			// read all
			DBCursor cursor = coll.find();
			System.out.println(coll.count());  
			while (cursor.hasNext()) {  
				Article article=new Article();
				DBObject obj=cursor.next();
				Object id=obj.get("_id");
				article._name=obj.get("name").toString();
				article._source=obj.containsField("source")?obj.get("source").toString():"";
				article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
				article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
				article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
				article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
				article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
				article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;

				article._parsedTitle=obj.containsField("parsedTitle")?obj.get("keywords").toString():"";
				
				article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
				article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
				article._originalAuthors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
				article._dblpID=obj.containsField("dblpID")?obj.get("dblpID").toString():"";
				

//				System.out.println(article._name);
				if(obj.containsField("abstract")&&!obj.get("abstract").toString().isEmpty()){
					index++;
					out.write(obj.get(field).toString());
					out.write("\n");
				}

			}  
			System.out.println(index);
			cursor.close(); 
		

		
		}catch (IOException ex) {
		  // report
		} finally {
		}
	}
	
	/* 
	 * map of _id and theta
	 */
	private Hashtable<String, double[]> mapId2Theta =new Hashtable<String, double[]>();
	
	private String[] arrIds=new String[3000];
	
	// number of articles
	private int g_nArticles=0;
	
	// number of topics
	private int g_nTopics=0;
	
	
	/* read the ids of the articles with parsed abstract
	 * 2016/11/11
	 */
	void readIdList(){
       String fineName = "_id.txt";       
       try {
    	    BufferedReader in = new BufferedReader(new FileReader(fineName));
    	    String str;
    	    g_nArticles=0;
    	    while ((str = in.readLine()) != null){
    	    	mapId2Theta.put(str,new double[100]);
    	    	arrIds[g_nArticles++]=str;
//    	    	System.out.println(i+"\t"+str);
    	    }
    	    in.close();
    	} catch (IOException e) {
    		e.printStackTrace();
    	}       
	}

	/* read the theta generated by LDA
	 * 2016/11/11
	 */
	void readTheta(){		
       String fineName = "models/casestudy/model-final.theta";       
       try {
    	    BufferedReader in = new BufferedReader(new FileReader(fineName));
    	    String str;
    	    int i=0;    	    
    	    while ((str = in.readLine()) != null){
	    	    String[] thetas=str.split(" ");
	    	    g_nTopics=thetas.length;
	    	    int j=0;
	    	    double[] dbThetas=new double[g_nTopics];
	    	    for(String strTheta:thetas){
	    	    	dbThetas[j++]=Double.parseDouble(strTheta);
	    	    }
//	    	    System.out.println(i);
	    	    mapId2Theta.put(arrIds[i++], dbThetas);		
    	    }
    	    in.close();
    	} catch (IOException e) {
    		e.printStackTrace();
    	} 
	}

	/* merge the theta into the nodes data and write into database
	 * 2016/11/11
	 */
	void writeThetaIntoDatabase(){
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			DBCollection coll = db.getCollection("nodes");
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			

			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", a._name);
			newDocument.put("source", a._source);
			newDocument.put("abstract", a._abstract);
			newDocument.put("authors", a._authors);
			newDocument.put("keywords", a._keywords);
			newDocument.put("notes", a._notes);
			newDocument.put("year", a._year);
			newDocument.put("type", a._type);
			newDocument.put("parsedTitle", a._parsedTitle);
			newDocument.put("parsedAbstract", a._parsedAbstract);

			newDocument.put("theta", mapId2Theta.get(key.toString()));

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

			coll.update(searchQuery, newDocument);
		}

	}
	
	/* read the topic information generated by LDA to the nodes
	 * according to _id.txt and theta.txt
	 * 2016/11/11
	 */
	void readTopicForDoc(){
		// 1.read data from database
		readDataFromDatabase();
		
		// 2.read id list
		readIdList();		
		
		// 3.read theta
		readTheta();
		
		// 3.5.calculate distance
//		calculateSimilarity();
		
		// 4.write data into database
		writeThetaIntoDatabase();
		
	}
	
	/*
	 * refine the parsed abstract according to stop words and remove words less than 5
	 * 
	 */
	void refineParsedAbstract(){
		// get collection
		DBCollection coll = db.getCollection("nodes");

		int index=0;
		// read all
		DBCursor cursor = coll.find();
		System.out.println(coll.count());  
		while (cursor.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursor.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._source=obj.containsField("source")?obj.get("source").toString():"";
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
			article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
			article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
			article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
			article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;

			article._parsedTitle=obj.containsField("parsedTitle")?obj.get("parsedTitle").toString():"";
			article._parsedAbstract=obj.containsField("parsedAbstract")?obj.get("parsedAbstract").toString():"";
			
			article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
			article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
			article._originalAuthors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
			article._dblpID=obj.containsField("dblpID")?obj.get("dblpID").toString():"";
			
			index++;
//			System.out.println(article._name);
			if(!article._parsedAbstract.isEmpty())
				tbArticles.put(id, article);
		}  
		System.out.println(index);
		index=0;
		// 3.update the data		

		// record word Frequency
		Hashtable<String, Integer> wordFreqs=new Hashtable<String, Integer>();
		
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			String[] words=a._parsedAbstract.split(" ");
			for(String w: words){
				if(wordFreqs.containsKey(w)){
					wordFreqs.replace(w, wordFreqs.get(w)+1);
				}
				else
				{
					wordFreqs.put(w, 1);
				}
				
			}

		}

		e = tbArticles.keys();
		
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article article=tbArticles.get(key);
			String[] words=article._parsedAbstract.split(" ");
			String refinedAbstract="";
			for(String w: words){
				if(
						checkLemma(w) 
						&& 
						wordFreqs.containsKey(w)&&wordFreqs.get(w)>=5
						){
					refinedAbstract+=w;
					refinedAbstract+=" ";
				}				
			}
			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", article._name);
			newDocument.put("source", article._source);
			newDocument.put("abstract", article._abstract);
			newDocument.put("authors", article._authors);
			newDocument.put("keywords", article._keywords);
			newDocument.put("notes", article._notes);
			newDocument.put("year", article._year);
			newDocument.put("type", article._type);
			newDocument.put("parsedTitle", article._parsedTitle);
			newDocument.put("parsedAbstract", article._parsedAbstract);
			newDocument.put("refinedAbstract", refinedAbstract);
			// updated Data
			newDocument.put("originalName", article._originalName);
			newDocument.put("originalAuthors", article._originalAuthors);
			newDocument.put("updated", article._updated);
			newDocument.put("dblpID", article._dblpID);

			newDocument.put("listAuthors", article._listAuthors);

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));
			coll.update(searchQuery, newDocument);
			index++;
		}

		System.out.println(index);
		
		cursor.close(); 
	}
	
	/*
	 * calculate similarity between the two theta
	 */
	double calcuSimilarity(double[] theta1,double[] theta2){
		// calculate Euclidean distance
//		double denominator=Math.sqrt(g_nTopics);
//		double dis=0;
//		for(int i=0;i<g_nTopics;i++){
//			double d=theta1[i]-theta2[i];			
//			dis+=d*d;
//		}
//		return 1-Math.sqrt(dis)/denominator;
		
		double similarity=0;
		for(int i=0;i<g_nTopics;i++){
			similarity+=Math.min(theta1[i], theta2[i]);
		}
		return similarity;
	}
	
	/*
	 * Calculate the calculateSimilarity between the articles according to the topics
	 * 
	 */
	void calculateSimilarity(){

		DBCollection coll = db.getCollection("similarities");

//        DBCollection coll = db.createCollection("similarity", new BasicDBObject());
		// 0.initialize the matrix
		double[][] similarityMatrix=new double[g_nArticles][];
		for(int i=0;i<g_nArticles;i++){
			similarityMatrix[i]=new double[g_nArticles];
			for(int j=0;j<g_nArticles;j++){
				similarityMatrix[i][j]=0;
			}
		}
		
		// 1.Calculate the matrix
		for(int i=0;i<g_nArticles;i++){
			for(int j=0;j<g_nArticles;j++){
				similarityMatrix[i][j]=calcuSimilarity(mapId2Theta.get(arrIds[i]),mapId2Theta.get(arrIds[j]));
//				System.out.println(similarityMatrix[i][j]);
				
				if(similarityMatrix[i][j]>0.6 && i!=j){

					BasicDBObject newDocument = new BasicDBObject();
					newDocument.put("id1", arrIds[i]);
					newDocument.put("id2", arrIds[j]);
					newDocument.put("similarity", similarityMatrix[i][j]);
					

					coll.insert(newDocument);
				}
			}
		}
		
		// 2.write to database
//		for(int i=0;i<g_nArticles;i++){
//			for(int j=0;j<g_nArticles;j++){
//				BasicDBObject newDocument = new BasicDBObject();
//				newDocument.put("id1", arrIds[i]);
//				newDocument.put("id2", arrIds[j]);
//				newDocument.put("similarity", similarityMatrix[i][j]);
//				
//
//				coll.insert(newDocument);
//			}
//		}	
		


		
	}

	
	/*
	 * words statistic
	 */
	void wordsStatistic(){
		// get collection
		DBCollection coll = db.getCollection("nodes");

		int index=0;
		// read all
		DBCursor cursor = coll.find();
		System.out.println(coll.count());  
		while (cursor.hasNext()) {  
			Article article=new Article();
			DBObject obj=cursor.next();
			Object id=obj.get("_id");
			article._name=obj.get("name").toString();
			article._source=obj.containsField("source")?obj.get("source").toString():"";
			article._abstract=obj.containsField("abstract")?obj.get("abstract").toString():"";
			article._authors=obj.containsField("authors")?obj.get("authors").toString():"";
			article._keywords=obj.containsField("keywords")?obj.get("keywords").toString():"";
			article._notes=obj.containsField("notes")?obj.get("notes").toString():"";
			article._year=obj.containsField("year")?Integer.parseInt(obj.get("year").toString()):0;
			article._type=obj.containsField("type")?Integer.parseInt(obj.get("type").toString()):0;

			article._parsedTitle=obj.containsField("parsedTitle")?obj.get("parsedTitle").toString():"";
			article._parsedAbstract=obj.containsField("parsedAbstract")?obj.get("parsedAbstract").toString():"";
			
			article._updated=obj.containsField("updated")?Integer.parseInt(obj.get("updated").toString()):0;
			article._originalName=obj.containsField("originalName")?obj.get("originalName").toString():"";
			article._originalAuthors=obj.containsField("originalAuthors")?obj.get("originalAuthors").toString():"";
			article._dblpID=obj.containsField("dblpID")?obj.get("dblpID").toString():"";
			
			index++;
//			System.out.println(article._name);
			if(!article._parsedAbstract.isEmpty())
				tbArticles.put(id, article);
		}  
		System.out.println(index);
		index=0;
		// 3.update the data		

		// record word Frequency
		Hashtable<String, Integer> wordFreqs=new Hashtable<String, Integer>();
		
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			String[] words=a._parsedAbstract.split(" ");
			for(String w: words){
				if(wordFreqs.containsKey(w)){
					wordFreqs.replace(w, wordFreqs.get(w)+1);
				}
				else
				{
					wordFreqs.put(w, 1);
				}
				
			}

		}

	   //Transfer as List and sort it
	   ArrayList<Map.Entry<String, Integer>> l = new ArrayList(wordFreqs.entrySet());
	   Collections.sort(l, new Comparator<Map.Entry<?, Integer>>(){
	     public int compare(Map.Entry<?, Integer> o1, Map.Entry<?, Integer> o2) {
	        return o1.getValue().compareTo(o2.getValue());
	    }});
	       
	   System.out.println(l);
	   
		cursor.close(); 
	}
	


	/* merge the theta into the nodes data and write into database
	 * 2016/11/11
	 */
	void writeThetaIntoDataase(){
		Enumeration e = tbArticles.keys();
		while (e.hasMoreElements()){
			DBCollection coll = db.getCollection("nodes");
			Object key=e.nextElement();
			Article a=tbArticles.get(key);
			

			BasicDBObject newDocument = new BasicDBObject();
			newDocument.put("name", a._name);
			newDocument.put("source", a._source);
			newDocument.put("abstract", a._abstract);
			newDocument.put("authors", a._authors);
			newDocument.put("keywords", a._keywords);
			newDocument.put("notes", a._notes);
			newDocument.put("year", a._year);
			newDocument.put("type", a._type);
			newDocument.put("parsedTitle", a._parsedTitle);
			newDocument.put("parsedAbstract", a._parsedAbstract);

			newDocument.put("theta", mapId2Theta.get(key.toString()));

			BasicDBObject searchQuery = new BasicDBObject().append("_id", new ObjectId(key.toString()));

			coll.update(searchQuery, newDocument);
		}

	}
	
	/* read the topic information 
	 * 2016/11/20
	 */
	void readTopicInfo(){
	     String fineName = "models/casestudy/model-final.twords";       
	       try {
	    	    BufferedReader in = new BufferedReader(new FileReader(fineName));
	    	    String[] twords=new String[20];
	    	    double[] freqs=new double[20];
	    	    int topicIndex=0;
	    	    int index=0;
	    	    String str;    
	    	    while ((str = in.readLine()) != null){
		    	    String[] arrs=str.split("\\s+");
		    	    if(!arrs[0].equals("Topic")){
//		    	    	System.out.println(arrs[0]);
//		    	    	System.out.println(arrs[1]);
		    	    	twords[index]=arrs[1];
		    	    	freqs[index]=Double.parseDouble(arrs[2]);
		    	    	index++;
		    	    	if(index==20){
		    	    		DBCollection coll = db.getCollection("ldatopics");
		    				

		    				BasicDBObject newDocument = new BasicDBObject();
		    				newDocument.put("index", topicIndex);
		    				newDocument.put("twords", twords);
		    				newDocument.put("freqs", freqs);
		    				
		    				coll.insert(newDocument);
		    				
		    	    		index=0;
		    	    		topicIndex++;
		    	    	}
		    	    }
	    	    }
	    	    in.close();
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    	} 
		
	}
	
	
//	public static void main(String[] args){
//		MyFirstTest test=new MyFirstTest();
//
//		//test.tryToUpdateMongoDB();
//		
//		//test.testParseTree();
//		
//		// parse title
//		//test.parseTitle();
//		
//		// match the two collections
////		test.matchCollection();
//		
//		
//		// update authors by id
////		test.updateAuthorsById();
//		
//		//test.searchDBLP("Participatory Visualization with Wordle.");
//	
//		// my parser, cannot handle the <i> in the title
////		XMLReader reader=new XMLReader();
////		reader.loadDBLPToDatabase();
//
//		
//		// provided parser, has error
////		String dblpFileName="F:\\Data\\DBLP\\dblp.xml";
////		Parser p = new Parser(dblpFileName);
//		
//
//		// update collections according to dblp
////		test.updateCollectionsAccordingToDBLP();
//		// incrementally update the database according to dblp
////		test.updateCollectionsAccordingToDBLPIncrementally();
//
//	
//		// load visualization publication data
////		test.addVisData();
//		
////		test.checkDBLPData();
//		
////		test.testMatchingForNodes("Graph drawing aesthetics", 2012);
//		
////		test.recoverToOriginalNameForTooShortTitles();
//		
////		{
////			String str1="A tutorial on spectral clustering.";
////			String str2="A tutorial on spectral clustering.";
////			System.out.println(str1.toLowerCase().contains(str2.toLowerCase()));
////		}
//		
////		test.replaceShortLine();
//		
////		test.basicCalculation();
//		
////		test.parseAbstract();
////		test.tempRepair();
//		
//		
////		test.saveParsedAbstractToFile("_id");
////		test.saveParsedAbstractToFile("parsedAbstract");
//		
//
////		test.testLDA();
//		test.readTopicForDoc();
//		test.readTopicInfo();
//		
////		test.refineParsedAbstract();
////		test.saveParsedAbstractToFile("refinedAbstract");
//		
////		test.wordsStatistic();
//		
//
//		System.out.println("finished");
//
//
//	}
}
