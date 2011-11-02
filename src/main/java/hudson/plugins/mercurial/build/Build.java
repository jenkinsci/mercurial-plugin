package hudson.plugins.mercurial.build;

/**
 * Capture the status of a Jenkins build vs a Mercurial commit ID
 * @author <a href="mailto:nicolas.deloof@cloudbees.com">Nicolas De loof</a>
 */
public class Build {

    public int buildNumber;

    public String changesetID;

    public String branch;

    public Build(int buildNumber, String branch, String sha) {
        this.buildNumber = buildNumber;
        this.branch = branch;
        this.changesetID = sha;
    }

    @Override
    public String toString() {
        return "Build #" + buildNumber + " of " + changesetID + " on " + branch;
    }
}
