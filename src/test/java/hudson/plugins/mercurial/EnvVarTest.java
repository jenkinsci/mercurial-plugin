/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import java.io.File;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

public class EnvVarTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public MercurialRule m = new MercurialRule(r);
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void customConfiguration() throws Exception {
        // Define key/value that will be passed to job.
        final String key = "ENVVARTESTVAR";
        final String val = "EnvVarTestDir";
        
        // Create environment variables from key/value.
        EnvVars env = new EnvVars( key, val );
        
        // 'repo' and 'repoExpanded' should be the same; 'repo' will contain a non expanded environment variable.
        // TODO switch to MercurialContainer
        File repo, repoExpanded;
        repo = new File( tmp.getRoot() + "/$" + key );
        repoExpanded = new File( tmp.getRoot() + "/" + val );
        
        // Ensure our subdirectory exists.
        repoExpanded.mkdir( );
        
        // Initialise repository.
        m.hg(repo, env, "init");
        
        // We should now have a .hg directory inside our expanded path.
        assertTrue( new File( repoExpanded + "/.hg" ).getPath( ) + " does not exist", new File( repoExpanded + "/.hg" ).isDirectory( ) );
        
        // Touch and commit file.
        m.touchAndCommit(repoExpanded, "f");
        
        // Set up installation.
        r.jenkins.getDescriptorByType(MercurialInstallation.DescriptorImpl.class).setInstallations(new MercurialInstallation("test", "", "hg", false, false, false, "[format]\nusestore = false", null));
 
        // Create free style project.
        FreeStyleProject project = r.createFreeStyleProject();
        
        // Create key/value property and add it to the project.
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
            new StringParameterDefinition(key, val, "")
        );
        project.addProperty(pdb);
        
        // Set up SCM.
        project.setScm(new MercurialSCM("test", repo.getPath(), MercurialSCM.RevisionType.BRANCH, null, null, null, null, false, null, false));
        
        // Ensure project builds correctly (again ensures path expansion works).
        FreeStyleBuild b = r.assertBuildStatusSuccess(project.scheduleBuild2(0));
        
        // Catch case where code may see that '/path/$NON_EXPANDED_VAR' doesn't exist, so requests a clone, which succeeds (with the clone being
        // performed by the hg executable, which will expand the environment correctly. On a second build we'll check if workspace already exists,
        // if we check '/path/$NON_EXPANDED_VAR' the answer will be no, so a new clone will be triggered, this will fail as an existing repository
        // will already be checked out there.
        b = r.assertBuildStatusSuccess(project.scheduleBuild2(0));
        
        // Make sure we can get the workspace.
        FilePath ws = b.getWorkspace();
        assertNotNull(ws);
        
        // Make sure workspace iso kay.
        String requires = ws.child(".hg/requires").readToString();
        assertFalse(requires, requires.contains("store"));
    }

}
