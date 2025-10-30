package hudson.plugins.mercurial;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CachingCustomDirSCMTest {

	private JenkinsRule j;
	private MercurialTestUtil m;

	@TempDir
	private File tmp;
	private File repo;

	private static final String CACHING_INSTALLATION = "caching-custom-dir";

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        j = rule;
        m = new MercurialTestUtil(j);
		repo = tmp;
		j.jenkins
				.getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
				.setInstallations(new MercurialInstallation(CACHING_INSTALLATION, "", "hg",
						false, true, new File(newFolder(tmp, "junit"),"custom-cache-dir").getAbsolutePath(), false, "",
						Collections.emptyList()));
	}

    @Test
    void customCacheLocationFromSlave() throws Exception {
		FreeStyleProject p = j.createFreeStyleProject();
		p.setScm(new MercurialSCM(CACHING_INSTALLATION, repo.getPath(), null, null,
				null, null, false));
		p.setAssignedNode(j.createOnlineSlave());
		m.hg(repo, "init");
		m.touchAndCommit(repo, "a");
		String log = m.buildAndCheck(p, "a");
		Pattern pattern = Pattern.compile("hg clone .*custom-cache-dir");
		assertTrue(pattern.matcher(log).find());
	}

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
