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
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class MercurialSCMTest {

    public static final String EXPECTED_SHORT_ID = "123456789012";

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
        Map < String, String > actualEnvironment = new HashMap<String, String>();

        createMercurialSCM().buildEnvVarsFromActionable(createActionable(), actualEnvironment);

        assertEquals(EXPECTED_SHORT_ID, actualEnvironment.get("MERCURIAL_REVISION_SHORT"));
    }

    private MercurialSCM createMercurialSCM() {
        return new MercurialSCM("","","", "", "", null, true);
    }

    private Actionable createActionable() {
        List<Action> expectedActions = Arrays.asList((Action) new MercurialTagAction(EXPECTED_SHORT_ID + "1627e63489b4096a8858e559a456", "rev", null));
        return new ActionableTestDouble(expectedActions);
    }

    private class ActionableTestDouble extends Actionable {
        private List<Action> expectedActions = new ArrayList<Action>();

        public ActionableTestDouble(List<Action> expectedActions) {
            this.expectedActions = expectedActions;
        }

        public String getDisplayName() {
            throw new RuntimeException();
        }

        public String getSearchUrl() {
            throw new RuntimeException();
        }

        @Override
        public synchronized List<Action> getActions() {
            return expectedActions;
        }
    }
}
