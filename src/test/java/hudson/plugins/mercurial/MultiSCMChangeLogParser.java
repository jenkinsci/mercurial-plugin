package hudson.plugins.mercurial;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MultiSCMChangeLogParser extends ChangeLogParser {

	public static final String ROOT_XML_TAG = "multi-scm-log";
	public static final String SUB_LOG_TAG = "sub-log";
	
	private final Map<String, ChangeLogParser> scmLogParsers;	
	private final Map<String, String> scmDisplayNames;	
	
	public MultiSCMChangeLogParser(List<SCM> scms) {
		scmLogParsers = new HashMap<String, ChangeLogParser>();
		scmDisplayNames = new HashMap<String, String>();
		for(SCM scm : scms) {
			if(!scmLogParsers.containsKey(scm.getClass().getName())) {
				scmLogParsers.put(scm.getClass().getName(), scm.createChangeLogParser());
				scmDisplayNames.put(scm.getClass().getName(), scm.getDescriptor().getDisplayName());
			}
		}
	}
	
	private class LogSplitter extends DefaultHandler {

		private final MultiSCMChangeLogSet changeLogs;
		private final AbstractBuild build;
		private final File tempFile;
		private OutputStreamWriter outputStream;
		private String scmClass;
		
		public LogSplitter(AbstractBuild build, String tempFilePath) {
			changeLogs = new MultiSCMChangeLogSet(build);
			this.tempFile= new File(tempFilePath);
			this.build = build;
		}
		
		@Override
		public void characters(char[] data, int startIndex, int length)
				throws SAXException {
			
			try {
				if(outputStream != null) {
					outputStream.write(data, startIndex, length);
				}
			} catch (IOException e) {
				throw new SAXException("Could not write temp changelog file", e);
			}
		}		

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attrs) throws SAXException {
			if(qName.compareTo(SUB_LOG_TAG) == 0) {
				FileOutputStream fos;
				try {
					scmClass = attrs.getValue("scm");
					fos = new FileOutputStream(tempFile);
				} catch (FileNotFoundException e) {
					throw new SAXException("could not create temp changelog file", e);
				}
				outputStream = new OutputStreamWriter(fos);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {

			if(qName.compareTo(SUB_LOG_TAG) == 0) {
				try {
					outputStream.close();
					outputStream = null;
					ChangeLogParser parser = scmLogParsers.get(scmClass);
					if(parser != null) {
						ChangeLogSet<? extends ChangeLogSet.Entry> cls = parser.parse(build, tempFile);
						changeLogs.add(scmClass, scmDisplayNames.get(scmClass), cls);
						
					}
				} catch (IOException e) {
					throw new SAXException("could not close temp changelog file", e);
				}
				
			}
		}

		public ChangeLogSet<? extends Entry> getChangeLogSets() {
			return changeLogs;
		}		
	}
	
	@Override
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build, File changelogFile)
		throws IOException, SAXException {
		
	      SAXParserFactory factory = SAXParserFactory.newInstance();
	      factory.setValidating(true);
	      SAXParser parser;
		try {
			parser = factory.newSAXParser();
		} catch (ParserConfigurationException e) {
			throw new SAXException("Could not create parser", e);
		}

		LogSplitter splitter = new LogSplitter(build, changelogFile.getPath() + ".temp2");
	    parser.parse(changelogFile, splitter);	    
	    return splitter.getChangeLogSets();
	}

}
