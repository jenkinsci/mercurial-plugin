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

import hudson.model.Action;
import hudson.model.Actionable;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MercurialSCMTest {

    @Test
    void parseStatus() {
        assertEquals(new HashSet<>(Arrays.asList("whatever", "added", "mo-re", "whatever-c", "initial", "more")), MercurialSCM.parseStatus(
                """
                        M whatever
                        A added
                        A mo-re
                          more
                        A whatever-c
                          whatever
                        R initial
                        R more
                        """));
    }

    @Test
    void buildEnvVarsSetsShortId() {
        Map<String,String> actualEnvironment = new HashMap<>();
        final String EXPECTED_SHORT_ID = "123456789012";
        new MercurialSCM("","","", "", "", null, true).buildEnvVarsFromActionable(new Actionable() {
            @Override public List<Action> getActions() {
                return Collections.singletonList(new MercurialTagAction(EXPECTED_SHORT_ID + "1627e63489b4096a8858e559a456", "rev", null, null));
            }
            @Override public String getDisplayName() {return null;}
            @Override public String getSearchUrl() {return null;}
        }, actualEnvironment);
        assertEquals(EXPECTED_SHORT_ID, actualEnvironment.get("MERCURIAL_REVISION_SHORT"));
    }

    @Test
    void buildEnvVarsSetsUrl() {
        Map<String,String> actualEnvironment = new HashMap<>();
        final String EXPECTED_REPOSITORY_URL = "http://mercurialserver/testrepo";
        new MercurialSCM("",EXPECTED_REPOSITORY_URL,"", "", "", null, true).buildEnvVarsFromActionable(new Actionable() {
            @Override public List<Action> getActions() {
                return Collections.singletonList(new MercurialTagAction("1627e63489b4096a8858e559a456", "rev", null, null));            }
            @Override public String getDisplayName() {return null;}
            @Override public String getSearchUrl() {return null;}
        }, actualEnvironment);
        assertEquals(EXPECTED_REPOSITORY_URL, actualEnvironment.get("MERCURIAL_REPOSITORY_URL"));
    }

    @Issue("JENKINS-68562")
    @Test
    void abortIfSourceIsLocal() {
        assertDoesNotThrow(() -> new MercurialSCM("", "https://mercurialserver/testrepo", "", "", "", null, true).abortIfSourceLocal(), "https source URLs should always be valid");
    }

}
