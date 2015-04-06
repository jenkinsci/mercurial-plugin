/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.mercurial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.model.Actionable;
import hudson.plugins.mercurial.MercurialSCM.MercurialRevision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MercurialSCMTest {
	
	private static final String COMMIT_MESSAGE_INCLUSION_REGEX = "^INCLUDE:.*";
	
	private static final List<String> CHANGED_FILES = Lists.newArrayList("A added","R removed","M modified");

    @Test public void parseStatus() throws Exception {
        assertEquals(new HashSet<String>(Arrays.asList("whatever", "added", "mo-re", "whatever-c", "initial", "more")), MercurialSCM.parseStatus(
                  "M whatever\n"
                + "A added\n"
                + "A mo-re\n"
                + "  more\n"
                + "A whatever-c\n"
                + "  whatever\n"
                + "R initial\n"
                + "R more\n"));
    }

    @Test public void buildEnvVarsSetsShortId() throws IOException {
        Map<String,String> actualEnvironment = new HashMap<String,String>();
        final String EXPECTED_SHORT_ID = "123456789012";
        new MercurialSCM("","","", "", "", null, true).buildEnvVarsFromActionable(new Actionable() {
            @Override public List<Action> getActions() {
                return Collections.<Action>singletonList(new MercurialTagAction(EXPECTED_SHORT_ID + "1627e63489b4096a8858e559a456", "rev", null));
            }
            public String getDisplayName() {return null;}
            public String getSearchUrl() {return null;}
        }, actualEnvironment);
        assertEquals(EXPECTED_SHORT_ID, actualEnvironment.get("MERCURIAL_REVISION_SHORT"));
    }

    @Test public void buildEnvVarsSetsUrl() throws IOException {
        Map<String,String> actualEnvironment = new HashMap<String,String>();
        final String EXPECTED_REPOSITORY_URL = "http://mercurialserver/testrepo";
        new MercurialSCM("",EXPECTED_REPOSITORY_URL,"", "", "", null, true).buildEnvVarsFromActionable(new Actionable() {
            @Override public List<Action> getActions() {
                return Collections.<Action>singletonList(new MercurialTagAction("1627e63489b4096a8858e559a456", "rev", null));            }
            public String getDisplayName() {return null;}
            public String getSearchUrl() {return null;}
        }, actualEnvironment);
        assertEquals(EXPECTED_REPOSITORY_URL, actualEnvironment.get("MERCURIAL_REPOSITORY_URL"));
    }
    
    @Test public void testGetChangedFileNamesFilteringByRegex_SingleIncludedFound() {
    	MercurialSCM mercurialSCM = setupNewMercurialSCM(true);
    	
    	TaskListener listener = null;
    	MercurialTagAction baseline = null;
    	FilePath repository = null;
    	HgExe hg = null;
    	String remote = null;
		
		Set<String> changedFileNames = null;
		try {
			changedFileNames = mercurialSCM
					.getChangedFileNamesFilteringByRegex(listener, baseline,
							repository, hg, remote, System.out,
							COMMIT_MESSAGE_INCLUSION_REGEX);
		} catch (InterruptedException ie) {
			Assert.fail("Unexpected InterruptedException: " + ie.getMessage());
		} catch (IOException ioe) {
			Assert.fail("Unexpected IOException: " + ioe);
    	}
		
		assertNotNull(changedFileNames);
		System.out.println("changedFileNames=" + changedFileNames);
		assertEquals("Should have 1 changedFileNames found",1,changedFileNames.size());
    }

    @Test public void testGetChangedFileNamesFilteringByRegex_SingleExcludedFound() {
    	MercurialSCM mercurialSCM = setupNewMercurialSCM(false);
    	
    	TaskListener listener = null;
    	MercurialTagAction baseline = null;
    	FilePath repository = null;
    	HgExe hg = null;
    	String remote = null;
		
		Set<String> changedFileNames = null;
		try {
			changedFileNames = mercurialSCM
					.getChangedFileNamesFilteringByRegex(listener, baseline,
							repository, hg, remote, System.out,
							COMMIT_MESSAGE_INCLUSION_REGEX);
		} catch (InterruptedException ie) {
			Assert.fail("Unexpected InterruptedException: " + ie.getMessage());
		} catch (IOException ioe) {
			Assert.fail("Unexpected IOException: " + ioe);
    	}
		
		assertNotNull(changedFileNames);
		System.out.println("changedFileNames=" + changedFileNames);
		assertEquals("Should have 0 changedFileNames found",0,changedFileNames.size());
    }

    @Test public void testGetChangedFileNamesFilteringByRegex_SixIncludedFound() {
    	MercurialSCM mercurialSCM = setupNewMercurialSCM(true, true, true, true, true, true);
    	
    	TaskListener listener = null;
    	MercurialTagAction baseline = null;
    	FilePath repository = null;
    	HgExe hg = null;
    	String remote = null;
		
		Set<String> changedFileNames = null;
		try {
			changedFileNames = mercurialSCM
					.getChangedFileNamesFilteringByRegex(listener, baseline,
							repository, hg, remote, System.out,
							COMMIT_MESSAGE_INCLUSION_REGEX);
		} catch (InterruptedException ie) {
			Assert.fail("Unexpected InterruptedException: " + ie.getMessage());
		} catch (IOException ioe) {
			Assert.fail("Unexpected IOException: " + ioe);
    	}
		
		assertNotNull(changedFileNames);
		System.out.println("changedFileNames=" + changedFileNames);
		assertEquals("Should have 3 changedFileNames found",3,changedFileNames.size());
    }

    @Test public void testGetChangedFileNamesFilteringByRegex_SixExcludedFound() {
    	MercurialSCM mercurialSCM = setupNewMercurialSCM(false, false, false, false, false, false);
    	
    	TaskListener listener = null;
    	MercurialTagAction baseline = null;
    	FilePath repository = null;
    	HgExe hg = null;
    	String remote = null;
		
		Set<String> changedFileNames = null;
		try {
			changedFileNames = mercurialSCM
					.getChangedFileNamesFilteringByRegex(listener, baseline,
							repository, hg, remote, System.out,
							COMMIT_MESSAGE_INCLUSION_REGEX);
		} catch (InterruptedException ie) {
			Assert.fail("Unexpected InterruptedException: " + ie.getMessage());
		} catch (IOException ioe) {
			Assert.fail("Unexpected IOException: " + ioe);
    	}
		
		assertNotNull(changedFileNames);
		System.out.println("changedFileNames=" + changedFileNames);
		assertEquals("Should have 0 changedFileNames found",0,changedFileNames.size());
    }

    @Test public void testGetChangedFileNamesFilteringByRegex_MixOfAlternatingSixIncludedSixExcludedFound() {
    	MercurialSCM mercurialSCM = setupNewMercurialSCM(true, false, true, false, true, false, true, false, true, false, true, false);
    	
    	TaskListener listener = null;
    	MercurialTagAction baseline = null;
    	FilePath repository = null;
    	HgExe hg = null;
    	String remote = null;
		
		Set<String> changedFileNames = null;
		try {
			changedFileNames = mercurialSCM
					.getChangedFileNamesFilteringByRegex(listener, baseline,
							repository, hg, remote, System.out,
							COMMIT_MESSAGE_INCLUSION_REGEX);
		} catch (InterruptedException ie) {
			Assert.fail("Unexpected InterruptedException: " + ie.getMessage());
		} catch (IOException ioe) {
			Assert.fail("Unexpected IOException: " + ioe);
    	}
		
		assertNotNull(changedFileNames);
		System.out.println("changedFileNames=" + changedFileNames);
		assertEquals("Should have 3 changedFileNames found",3,changedFileNames.size());
    }

	private MercurialSCM setupNewMercurialSCM(final boolean... included) {
		return new MercurialSCM("") {
			private static final long serialVersionUID = 1L;
			private Map<Integer,Set<String>> changedFiles = new HashMap<Integer,Set<String>>();
			private List<MercurialRevision> getRevisionsBetweenReturnValue = setupData(changedFiles,included);
    		@Override
    		List<MercurialRevision> getRevisionsBetween(TaskListener listener,
    				MercurialTagAction baseline, FilePath repository, HgExe hg,
    				String remote) throws IOException, InterruptedException {
    			return getRevisionsBetweenReturnValue;
    		}
    		@Override
    		Set<String> getChangedFileNamesBetweenRevisions(
    				TaskListener listener, FilePath repository, HgExe hg,
    				String endId, String startId) throws IOException,
    				InterruptedException {
    			return changedFiles.get(Integer.valueOf(endId));
    		}
    	};
	}
    
    private List<MercurialRevision> setupData(Map<Integer,Set<String>> changedFileNamesBetweenRevisions,boolean... included) {
    	List<MercurialRevision> returnData = new ArrayList<MercurialRevision>();
    	returnData.add(new MercurialRevision("baselineRev", "baselineMessage"));
    	Integer count = 0;
    	for(boolean include:included) {
    		changedFileNamesBetweenRevisions.put(count, getChangedFileNames(include, count));
    		returnData.add(new MercurialRevision(String.valueOf(count), getCommitMessage(count,include)));
    		count++;
    	}
    	System.out.println("Created revision: " + returnData);
    	return returnData;
    }

	private Set<String> getChangedFileNames(boolean include, int count) {
		Set<String> changedFileNames = null;
		if(include) {
			changedFileNames = new HashSet<String>();
			changedFileNames.add(CHANGED_FILES.get(count % 3));
		}
		return changedFileNames;
	}

	private String getCommitMessage(int count, boolean include) {
		String message = include ? "INCLUDE:" : "EXCLUDE:";
		message += "message" + count;
		return message;
	}

}
