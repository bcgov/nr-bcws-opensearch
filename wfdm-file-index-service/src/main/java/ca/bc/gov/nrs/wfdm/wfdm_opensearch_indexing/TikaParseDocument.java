package ca.bc.gov.nrs.wfdm.wfdm_opensearch_indexing;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.SAXException;
import org.json.simple.JSONObject;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TikaParseDocument {

	Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public void ParseDocument(BufferedInputStream bufferedInputStream, String fileName) throws SAXException, TransformerConfigurationException  {
		System.out.println("IN Tika Parse Document");
		try {
			String extractedContent = ParseUsingTika(bufferedInputStream);
			OpenSearchRESTClient restClinet = new OpenSearchRESTClient();
			restClinet.addIndexToOpenSearch(extractedContent, fileName);
			
		} catch (IOException e) {
			e.printStackTrace();
		} 

	}

	private String ParseUsingTika(InputStream objectData)
			throws IOException, TransformerConfigurationException, SAXException {

		String extractedText = "";
		SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
		TransformerHandler handler = factory.newTransformerHandler();
		handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "text");
		handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
		StringWriter sw = new StringWriter();
		handler.setResult(new StreamResult(sw));
		AutoDetectParser parser = new AutoDetectParser();
		ParseContext parseContext = new ParseContext();
		parseContext.set(Parser.class, parser);
		System.out.println("");
		Tika tika = new Tika();
		Metadata tikaMetadata = new Metadata();
		try {
			parser.parse(objectData, handler, tikaMetadata, parseContext);
			// #TODO:Replace regular expression
			extractedText = removeStopWords(sw.toString().replace("\n", "").replace("\r", ""));
		} catch (TikaException e) {
			System.out.println("TikaException thrown while parsing: " + e.getLocalizedMessage());
			return assembleExceptionResult(e);
		}
		
		System.out.println("\n\n EVENT: " + gson.toJson(extractedText + tikaMetadata));
		return assembleExtractionResult(extractedText, tikaMetadata);
	}

	@SuppressWarnings("unchecked")
	private String assembleExtractionResult(String extractedText, Metadata tikaMetadata) {

		System.out.println("assembleExtractionResult for " + extractedText + tikaMetadata);
		JSONObject extractJson = new JSONObject();

		String contentType = tikaMetadata.get("Content-Type");
		contentType = contentType != null ? contentType : "content/unknown";

		String contentLength = tikaMetadata.get("Content-Length");
		contentLength = contentLength != null ? contentLength : "0";

		
		extractJson.put("Text", extractedText);
		extractJson.put("ContentType", contentType);
		extractJson.put("ContentLength", contentLength);

		JSONObject metadataJson = new JSONObject();

		for (String name : tikaMetadata.names()) {
			String[] elements = tikaMetadata.getValues(name);
			String joined = String.join(", ", elements);
			metadataJson.put(name, joined);
		}
		System.out.println("\n\n EVENT: " + gson.toJson(metadataJson));
		extractJson.put("Metadata", metadataJson);

		return extractJson.toJSONString();
	}

	@SuppressWarnings("unchecked")
	private String assembleExceptionResult(Exception e) {
		JSONObject exceptionJson = new JSONObject();
		exceptionJson.put("Exception", e.getLocalizedMessage());
		exceptionJson.put("ContentType", "unknown");
		exceptionJson.put("ContentLength", "0");
		exceptionJson.put("Text", "");

		JSONObject metadataJson = new JSONObject();

		exceptionJson.put("Metadata", metadataJson);

		return exceptionJson.toJSONString();
	}

	private static String removeStopWords(String extractedText) {
		System.out.println("remove stop words");

		String finalString = "";
		try {
			String file = "";
			URL url = new java.net.URL(
					"https://gist.githubusercontent.com/rg089/35e00abf8941d72d419224cfd5b5925d/raw/12d899b70156fd0041fa9778d657330b024b959c/stopwords.txt");
			URLConnection connection = url.openConnection();
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null)
				file = file + line + "\n";
			List<String> stopWords = new ArrayList<String>(Arrays.asList(file.split("\n")));
			List<String> allWords = Stream.of(extractedText.toLowerCase().split(" "))
					.collect(Collectors.toCollection(ArrayList<String>::new));
			allWords.removeAll(stopWords);
			finalString = allWords.stream().collect(Collectors.joining(" "));

		} catch (IOException e) {
			e.printStackTrace();

		}
		return finalString;
	}

}
