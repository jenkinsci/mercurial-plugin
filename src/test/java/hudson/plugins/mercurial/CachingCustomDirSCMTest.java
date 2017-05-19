package hudson.plugins.mercurial;

import java.io.File;
import java.util.Collections;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;
import hudson.tools.ToolProperty;

public class CachingCustomDirSCMTest {

	@Rule
	public JenkinsRule j = new JenkinsRule();
	@Rule
	public MercurialRule m = new MercurialRule(j);
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();
	private File repo;

	private static final String CACHING_INSTALLATION = "caching-custom-dir";

	@Before
	public void setUp() throws Exception {
		repo = tmp.getRoot();
		j.jenkins
				.getDescriptorByType(MercurialInstallation.DescriptorImpl.class)
				.setInstallations(new MercurialInstallation(CACHING_INSTALLATION, "", "hg",
						false, true, new File(tmp.newFolder(),"custom-cache-dir").getAbsolutePath().toString(), false, "",
						Collections.<ToolProperty<?>> emptyList()));
	}

	@Test
	public void customCacheLocationFromSlave() throws Exception {
		FreeStyleProject p = j.createFreeStyleProject();
		p.setScm(new MercurialSCM(CACHING_INSTALLATION, repo.getPath(), null, null,
				null, null, false));
		p.setAssignedNode(j.createOnlineSlave());
		m.hg(repo, "init");
		m.touchAndCommit(repo, "a");
		String log = m.buildAndCheck(p, "a");
		Pattern pattern = Pattern.compile("hg clone .*custom-cache-dir");
		Assert.assertTrue(pattern.matcher(log).find());
	}

}
