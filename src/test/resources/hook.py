import urllib.request, urllib.parse
def commit(ui, repo, node, **kwargs):
    data = {
        'url': '@REPO_URL@',
        'branch': repo[node].branch(),
        'changesetId': node,
    }
    req = urllib.request.Request('@JENKINS_URL@mercurial/notifyCommit')
    rsp = urllib.request.urlopen(req, urllib.parse.urlencode(data).encode("utf-8"))
    # TODO gives some error about bytes vs. str: ui.warn('Notify Commit hook response: %s\n' % rsp.read())
    pass
