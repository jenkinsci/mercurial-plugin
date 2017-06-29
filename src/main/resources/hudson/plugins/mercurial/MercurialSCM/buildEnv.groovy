package hudson.plugins.mercurial.MercurialSCM;

def l = namespace(lib.JenkinsTagLib)

['MERCURIAL_REVISION', 'MERCURIAL_REVISION_SHORT', 'MERCURIAL_REVISION_NUMBER', 'MERCURIAL_REVISION_BRANCH', 'MERCURIAL_REPOSITORY_URL'].each {name ->
    l.buildEnvVar(name: name) {
        raw(_("${name}.blurb"))
    }
}
