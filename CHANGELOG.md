# Changelog
Versions 2.7 and later are listed in [GitHub releases](https://github.com/jenkinsci/mercurial-plugin/releases).

## Version 2.6 (2019-04-10)

-   [ JENKINS-55319](https://issues.jenkins-ci.org/browse/JENKINS-55319)
    - Getting issue details... STATUS

## Version 2.5 (2019-01-24)

-   Using the repository cache without sharing failed on Windows.

## Version 2.4 (Jun 26, 2018)

-   [ JENKINS-51530](https://issues.jenkins-ci.org/browse/JENKINS-51530)
    - Getting issue details... STATUS

## Version 2.3 (Feb 26, 2018)

-   [Fix security
    issue](https://jenkins.io/security/advisory/2018-02-26/)

## Version 2.2 (Oct 12, 2017)

-   Metadata fixes useful for downstream plugins.
-   JSch update.

## Version 2.1 (Aug 24, 2017)

-   [JENKINS-42278](https://issues.jenkins-ci.org/browse/JENKINS-42278) Branch
    scanning failed if some branches lacked the marker file such
    as `Jenkinsfile`.

-   [JENKINS-45806](https://issues.jenkins-ci.org/browse/JENKINS-45806) Branch
    scanning failed to pass credentials.

## Version 2.0 (Jul 17, 2017)

-   [JENKINS-43507](https://issues.jenkins-ci.org/browse/JENKINS-43507) Allow
    SCMSource and SCMNavigator subtypes to share common traits 

## Version 1.61 (Jun 16, 2017)

-   [JENKINS-26100](https://issues.jenkins-ci.org/browse/JENKINS-26100) Support
    exporting environment variables to Pipeline scripts, when on Jenkins
    2.60 and suitably new plugins.

-   [JENKINS-41657](https://issues.jenkins-ci.org/browse/JENKINS-41657) Better
    support Mercurial for Pipeline library configuration.

## Version 1.60 (Apr 26, 2017)

-   [JENKINS-26762](https://issues.jenkins-ci.org/browse/JENKINS-26762) Ignore
    trailing slashes when comparing URLs for `/mercurial/notifyCommit`.

## Version 1.59 (Feb 9, 2017)

-   [JENKINS-41814](https://issues.jenkins-ci.org/browse/JENKINS-41814) Expose
    event origin to listeners using the new SCM API event system.

## Version 1.58 (Jan 16, 2017)

-   ⚠️ Please read [this Blog
    Post](https://jenkins.io/blog/2017/01/17/scm-api-2/) before
    upgrading
-   other changes [Unknown User
    (stephenconnolly)](https://wiki.jenkins.io/display/~stephenconnolly) forgot
    to list

## Version 1.58-beta-1 (Jan 13, 2017)

-   [JENKINS-39355](https://issues.jenkins-ci.org/browse/JENKINS-39355)
    Using new SCM APIs, in particular to better support webhook events
    in multibranch projects.
-   [JENKINS-40836](https://issues.jenkins-ci.org/browse/JENKINS-40836)
    Report the primary branch (`default`) to multibranch UIs.
-   [JENKINS-23571](https://issues.jenkins-ci.org/browse/JENKINS-23571)
    Configurable master cache directory location.

## Version 1.57 (Oct 12, 2016)

-   Added an option to check out a revset rather than a branch.
-   [JENKINS-30295](https://issues.jenkins-ci.org/browse/JENKINS-30295)
    Implemented APIs used by the [Email-ext
    plugin](https://wiki.jenkins.io/display/JENKINS/Email-ext+plugin).
-   [JENKINS-37274](https://issues.jenkins-ci.org/browse/JENKINS-37274)
    Suppressed some output in the build log that seems to have misled
    users.

## Version 1.56 (Jul 13, 2016)

-   [JENKINS-28121](https://issues.jenkins-ci.org/browse/JENKINS-28121)
    Pipeline checkouts could fail if the workspace directory did not yet
    exist.
-   [JENKINS-36219](https://issues.jenkins-ci.org/browse/JENKINS-36219)
    Changelogs were not displayed for multibranch (e.g., Pipeline)
    projects.

## Version 1.55 (Jun 17, 2016)

-   [JENKINS-30120](https://issues.jenkins-ci.org/browse/JENKINS-30120)
    As of Mercurial 3.4.2, polling was broken when using spaces in a
    branch name.
-   Excessive numbers of changesets were being considered by polling
    under some circumstances.
-   Allow credentials pulldown to work in *Snippet Generator* from a
    Pipeline branch project.
-   [JENKINS-29311](https://issues.jenkins-ci.org/browse/JENKINS-29311)
    Deprecated method printed message to log.
-   [JENKINS-27316](https://issues.jenkins-ci.org/browse/JENKINS-27316)
    Ugly stack traces in log file.

## Version 1.54 (Jun 11, 2015)

-   API incompatibility in 1.53.

## Version 1.53 (Jun 02, 2015)

-   [JENKINS-10706](https://issues.jenkins-ci.org/browse/JENKINS-10706)
    Expose new environment variable `MERCURIAL_REVISION_BRANCH`.
-   Add support for [Kallithea](https://kallithea-scm.org/).

## Version 1.52 (Mar 16, 2015)

-   Expose new environment variable `MERCURIAL_REPOSITORY_URL`.

## Version 1.51 (Nov 06, 2014)

No code change from beta 3.

### Version 1.51-beta-3 (Oct 07, 2014)

-   SECURITY-158 fix.

## Version 1.51-beta-2 (Aug 05, 2014)

-   (pull \#60) Expand environment variables in various fields.

## Version 1.51-beta-1 (Jun 16, 2014)

-   Adapted to enhanced SCM API in Jenkins 1.568+.
-   (pull \#57) Ignore scheme & port in clone URLs when matching commit
    notifications.

## Version 1.50.1 (Oct 07, 2014)

-   SECURITY-158 fix.

## Version 1.50 (Feb 28, 2014)

All changes in beta 1 & 2 plus:

-   [JENKINS-15806](https://issues.jenkins-ci.org/browse/JENKINS-15806)
    Fail the build if `hg pull` fails.

## Version 1.50 beta 2 (Feb 19, 2014) (experimental update center only)

-   (pull \#49) Added branch column header.
-   [JENKINS-15829](https://issues.jenkins-ci.org/browse/JENKINS-15829)
    Do not do a fresh clone for every build when using repository
    sharing on a slave.
-   [JENKINS-16654](https://issues.jenkins-ci.org/browse/JENKINS-16654)
    Option to disable changelog calculation, which can be expensive in
    some cases.
-   [JENKINS-18237](https://issues.jenkins-ci.org/browse/JENKINS-18237)
    Fix use of Multiple SCMs plugin with matrix builds.
-   [JENKINS-5723](https://issues.jenkins-ci.org/browse/JENKINS-5723)
    Permit arbitrary configuration options to be set on a Mercurial
    installation.

## Version 1.50 beta 1 (Jan 08, 2014) (experimental update center only)

-   1.509.4 baseline.
-   Require credentials 1.9.4 for an important bugfix.
-   (pull \#47) New extension point for overriding polling comparisons.
-   [JENKINS-5396](https://issues.jenkins-ci.org/browse/JENKINS-5396)
    Supported option to update to a tag rather than a branch.
-   [JENKINS-5452](https://issues.jenkins-ci.org/browse/JENKINS-5452)
    Properly escape user names in changelog.
-   (pull \#48) Added SSH private key credentials support. (Still no SSL
    client certificate support.)

## Version 1.49 (Oct 22, 2013)

-   [JENKINS-20186](https://issues.jenkins-ci.org/browse/JENKINS-20186)
    Jenkins 1.536+ would throw errors when saving jobs with a Mercurial
    browser set; fixing plugin to not use unnecessary code.

## Version 1.48 (Oct 08, 2013)

-   Same as 1.48 beta 1 except tested against a 1.509.3 baseline.

## Version 1.48 beta 1 (Sep 20, 2013) (experimental update center only)

-   Improved Credentials integration by using different command-line
    options that should work with the largefiles extension and otherwise
    be more reliable.
-   Added integration with the [SCM API
    Plugin](https://wiki.jenkins.io/display/JENKINS/SCM+API+Plugin).

## Version 1.47 (Sep 10, 2013)

-   [JENKINS-19493](https://issues.jenkins-ci.org/browse/JENKINS-19493)
    Use form validation to alert users of invalid repository browser
    URLs before saving.
-   [JENKINS-7351](https://issues.jenkins-ci.org/browse/JENKINS-7351)
    Add support for HTTP(S) username/password credentials. (Not yet
    implemented: SSL client certificates, SSH private keys.)
-   [JENKINS-18807](https://issues.jenkins-ci.org/browse/JENKINS-18807)
    Ignore SCM triggers which ask to suppress post-commit hooks. (Plugin
    now requires 1.509.2 or newer.)
-   [JENKINS-18252](https://issues.jenkins-ci.org/browse/JENKINS-18252)
    Added ability to recognize `/var/hg/stuff` in push polling.
    Previously, it caused an error because of the lack of a URL
    protocol.
-   (pull \#42) Added `MERCURIAL_REVISION_SHORT` environment variable.

## Version 1.46 (May 14, 2013)

-   [JENKINS-9686](https://issues.jenkins-ci.org/browse/JENKINS-9686)
    Expand default values of string parameters when polling.

## Version 1.45 (April 21, 2013)

-   [JENKINS-3907](https://issues.jenkins-ci.org/browse/JENKINS-3907)
    Let all runs in a matrix build update to the same Mercurial
    revision.
-   [JENKINS-13669](https://issues.jenkins-ci.org/browse/JENKINS-13669)
    Replaced NullPointerException with a more informative IOException
    caching fails during polling.
-   [JENKINS-17353](https://issues.jenkins-ci.org/browse/JENKINS-17353)
    Assume UTF-8 encoding for metadata in changelog.xml
-   don't relink when sharing repositories, as that makes mercurial time
    out.

## Version 1.44 (Feb 26, 2013)

-   (pull \#33) Ignore authentication section in URL for purposes of
    matching push notifications.

## Version 1.43 (Feb 05, 2013)

-   (pull \#32) Fix push notification when anonymous users lack read
    access.

## Version 1.42 (Nov 06, 2012)

-   [JENKINS-12763](https://issues.jenkins-ci.org/browse/JENKINS-12763)
    Excessive lock contention when using mercurial cache with multiple
    repos and slaves.

## Version 1.41 (Jun 05, 2012)

-   [JENKINS-13174](https://issues.jenkins-ci.org/browse/JENKINS-13174)
    (continued) Do not ignore .hgsubstate changes when polling.

## Version 1.40 (May 22, 2012)

-   [JENKINS-12829](https://issues.jenkins-ci.org/browse/JENKINS-12829)
    A failed update sets revision of build to 000000+
-   [JENKINS-13624](https://issues.jenkins-ci.org/browse/JENKINS-13624)
    BitBucket URL not validated for format.
-   [JENKINS-13329](https://issues.jenkins-ci.org/browse/JENKINS-13329)
    --debug triggered fresh clones rather than updates.
-   [JENKINS-12544](https://issues.jenkins-ci.org/browse/JENKINS-12544)
    Illegal directory name on Windows when port number used in URL.
-   [JENKINS-13174](https://issues.jenkins-ci.org/browse/JENKINS-13174)
    Ignore .hgtags changes when polling.
-   [JENKINS-11549](https://issues.jenkins-ci.org/browse/JENKINS-11549)
    Include tip revision number in build metadata, not just changeset
    ID.
-   [JENKINS-13400](https://issues.jenkins-ci.org/browse/JENKINS-13400)
    Handle <file:///path> URLs.

## Version 1.39 (Apr 27, 2012)

-   [JENKINS-11976](https://issues.jenkins-ci.org/browse/JENKINS-11976)
    NonExistentFieldException warnings after upgrading mercurial plugin
    to 1.38
-   [JENKINS-11877](https://issues.jenkins-ci.org/browse/JENKINS-11877)
    Jenkins fails to run "hg" command even though the path to it is
    specified correctly
-   [JENKINS-2252](https://issues.jenkins-ci.org/browse/JENKINS-2252)
    Mention SCM changeset ID in email
-   [JENKINS-7594](https://issues.jenkins-ci.org/browse/JENKINS-7594)
    Merges across named branches should not be ignored.
-   [JENKINS-11809](https://issues.jenkins-ci.org/browse/JENKINS-11809)
    Time out on pull operations.
-   Restore 'hg relink' usage accidentally removed earlier.
-   [JENKINS-12162](https://issues.jenkins-ci.org/browse/JENKINS-12162)
    Pay attention to subdirectory, needed for use in Multi-SCM Plugin
    (recommended replacement for Forest).
-   [JENKINS-12361](https://issues.jenkins-ci.org/browse/JENKINS-12361)
    Directory separator '/' for modules supported on Windows.
-   [JENKINS-12404](https://issues.jenkins-ci.org/browse/JENKINS-12404)
    Enable polling without a workspace when using caches.

## Version 1.38 (Dec 2, 2011)

-   [JENKINS-11360](https://issues.jenkins-ci.org/browse/JENKINS-11360)
    Add support for RhodeCode as a Mercurial Repository Browser (patches
    by marc-guenther and marcsanfacon).
-   [JENKINS-10255](https://issues.jenkins-ci.org/browse/JENKINS-10255)
    Mercurial Changelog should compare with previous build (patches by
    willemv and davidmc24).
-   [JENKINS-11363](https://issues.jenkins-ci.org/browse/JENKINS-11363)
    Add support for Mercurial's ShareExtension to reduce disk usage
    (patches by willemv).
-   Dropping support for the Forest extension.
-   [JENKINS-11460](https://issues.jenkins-ci.org/browse/JENKINS-11460)
    "Repository URL" field in mercurial plugin should trim input.
-   Added push notification mechanism.

## Version 1.37 (Jun 13 2011)

-   [JENKINS-9964](https://issues.jenkins-ci.org/browse/JENKINS-9964)
    Expose the node name via the API and the GUI.
-   [JENKINS-7878](https://issues.jenkins-ci.org/browse/JENKINS-7878)
    MercurialSCM.update(...) should respect slave node default encoding.

## Version 1.35 (Jan 19 2011)

-   [JENKINS-7723](https://issues.jenkins-ci.org/browse/JENKINS-7723)
    Attempted fix for problem calculating changeset ID of workspace.

## Version 1.34 (Nov 15 2010)

-   [JENKINS-6126](https://issues.jenkins-ci.org/browse/JENKINS-6126)
    Fixed NPE in polling.

## Version 1.33 (Aug 13 2010)

-   [JENKINS-7194](https://issues.jenkins-ci.org/browse/JENKINS-7194)
    FishEye support.

## Version 1.32 (Aug 12 2010)

-   [JENKINS-3602](https://issues.jenkins-ci.org/browse/JENKINS-3602)
    Ability to specify a subdirectory of the workspace for the Mercurial
    repository.
-   [JENKINS-6548](https://issues.jenkins-ci.org/browse/JENKINS-6548)
    NPE when cache was out of commission.

## Version 1.31 (Jun 10 2010)

-   [JENKINS-6337](https://issues.jenkins-ci.org/browse/JENKINS-6337)
    Polling broken when module list specified.

## Version 1.30 (May 17 2010)

-   [JENKINS-6549](https://issues.jenkins-ci.org/browse/JENKINS-6549)
    Mercurial caches for slaves was broken in 1.29.

## Version 1.29 (May 12 2010)

-   [JENKINS-6517](https://issues.jenkins-ci.org/browse/JENKINS-6517)
    Reduce memory consumption representing merges in large repositories.

## Version 1.28 (Mar 29 2010)

-   [JENKINS-5835](https://issues.jenkins-ci.org/browse/JENKINS-5835)
    Include repository browsing support for Kiln (patch by
    timmytonyboots).

## Version 1.27 (Mar 19 2010)

-   [JENKINS-4794](https://issues.jenkins-ci.org/browse/JENKINS-4794)
    Option to maintain local caches of Mercurial repositories.

## Version 1.26 (Mar 09 2010)

-   [JENKINS-4271](https://issues.jenkins-ci.org/browse/JENKINS-4271)
    Support parameter expansion for branch (or tag) field.
-   [JENKINS-2180](https://issues.jenkins-ci.org/browse/JENKINS-2180)
    Polling period can be set shorter than the quiet period now.

## Version 1.25 (Nov 30 2009)

-   [JENKINS-4672](https://issues.jenkins-ci.org/browse/JENKINS-4672)
    Option to run Mercurial with `--debug`.
-   Dropping support for Mercurial 0.9.x. Use 1.0 at least.
-   [JENKINS-4972](https://issues.jenkins-ci.org/browse/JENKINS-4972) Do
    not consider merge changesets for purposes of polling.
-   [JENKINS-4846](https://issues.jenkins-ci.org/browse/JENKINS-4846)
    Option to download Forest extension on demand. Useful for
    hard-to-administer slaves.
-   Restoring ability to specify Mercurial executable name other than
    `INSTALLATION/bin/hg` (lost in 1.17 with move to tool installation
    system).
-   [JENKINS-1099](https://issues.jenkins-ci.org/browse/JENKINS-1099)
    Make "modules" list work even after restart.

## Version 1.24 (Nov 13 2009)

-   [JENKINS-1143](https://issues.jenkins-ci.org/browse/JENKINS-1143)
    Add support for the Forest extension.
-   [JENKINS-4840](https://issues.jenkins-ci.org/browse/JENKINS-4840)
    Support for clean builds when using Forest.

## Version 1.23 (Oct 23 2009)

-   Module list should filter the changelog as well as polling.
    ([JENKINS-4702](https://issues.jenkins-ci.org/browse/JENKINS-4702))
-   Implement getAffectedFiles in MercurialChangeSet
    [r22903](http://fisheye4.atlassian.com/changelog/hudson?cs=22903).

## Version 1.22 (Sep 23 2009)

-   [JENKINS-4461](https://issues.jenkins-ci.org/browse/JENKINS-4461)
    fix used a JDK 6ism:
    [JENKINS-4528](https://issues.jenkins-ci.org/browse/JENKINS-4528).

## Version 1.21 (Sep 22 2009)

-   [JENKINS-4461](https://issues.jenkins-ci.org/browse/JENKINS-4461)
    fix was leaking file handles:
    [JENKINS-4513](https://issues.jenkins-ci.org/browse/JENKINS-4513).

## Version 1.20 (Sep 21 2009)

-   [JENKINS-4514](https://issues.jenkins-ci.org/browse/JENKINS-4514)
    alternate browsers do not show up in dropdown after updating the
    plugin. This is an intermediate  
    quick fix until version 1.325 of the core is released.

## Version 1.19 (Sep 20 2009)

-   [JENKINS-4461](https://issues.jenkins-ci.org/browse/JENKINS-4461)
    fix was leaking threads.
-   Mercurial changelog now links to diffs and specific revisions of
    files
    ([JENKINS-4493](https://issues.jenkins-ci.org/browse/JENKINS-4493))

## Version 1.18 (Sep 18 2009)

-   1.17 release was botched (Maven issue), rereleasing as 1.18.

## Version 1.17 (Sep 18 2009)

-   Fixed various issues with named branches.
    ([JENKINS-4281](https://issues.jenkins-ci.org/browse/JENKINS-4281))
-   If switching to clone due to path mismatch, at least explain what is
    happening in the build log.
    ([JENKINS-1420](https://issues.jenkins-ci.org/browse/JENKINS-1420))
-   Kill Hg polling process after one hour, assuming it is stuck on a
    bad network connection.
    ([JENKINS-4461](https://issues.jenkins-ci.org/browse/JENKINS-4461))
-   Multiple Mercurial installations may now be configured as tools. See
    [Tool
    Auto-Installation](https://wiki.jenkins.io/display/JENKINS/Tool+Auto-Installation)
    for background.
-   Environment variable "MERCURIAL\_REVISION" that contains the node ID
    like "272a7f93d92d..." is now exposed to builds. (Also retain ID of
    tip revision for each build; not yet exposed via XML API or GUI but
    could be useful later.)
-   Google Code and BitKeeper can be now specified (in addition to
    hgweb) as a repository browser
    ([JENKINS-4426](https://issues.jenkins-ci.org/browse/JENKINS-4426))

## Version 1.16 (May 27 2009)

-   The plugin was failing to clean up tmp\*style file if the check out
    failed.
    ([JENKINS-3266](https://issues.jenkins-ci.org/browse/JENKINS-3266))
-   Fixed a file descriptor leak
    ([JENKINS-2420](https://issues.jenkins-ci.org/browse/JENKINS-2420))

## Version 1.15

-   Fixed implementation of clean update.
    ([JENKINS-2666](https://issues.jenkins-ci.org/browse/JENKINS-2666))
-   Choose the hgweb source browser automatically.
    ([JENKINS-2406](https://issues.jenkins-ci.org/browse/JENKINS-2406))

## Version 1.14

-   Hudson clones (never updates) when repo path ends with
    ([JENKINS-2718](https://issues.jenkins-ci.org/browse/JENKINS-2718))
-   Fixed a bug in the polling and branch handling
    ([report](http://www.nabble.com/Patch-to-fix-mercurial-branch-polling.-td21847046.html))

## Version 1.13

-   Exposed the details of the changelog to the remote API.

## Version 1.12

-   Fixed a polling bug in the distributed Hudson
    ([report](http://www.nabble.com/Distributed-builds-w--mercurial-td19707703.html))
-   Added an option to perform clean update.

## Version 1.11

-   Handle hg snapshot versions gracefully
    ([JENKINS-1683](https://issues.jenkins-ci.org/browse/JENKINS-1683))

## Version 1.9

-   Supported "modules" so that Hudson won't start builds for changes
    outside your module in hg
    ([discussion](http://www.nabble.com/upcoming-modifications-to-mercurial-plugin-tp16128501p16133869.html))
-   The plugin now correctly handles special XML meta-characters (such
    as ampersands) in filenames.
-   Correcting hgrc parser to not print warnings about valid config
    files.
-   Missing help file added.

## Version 1.8

-   Polling is made more robust so that warning messages from Mercurial
    won't confuse Hudson
-   Do not show the list of files "changed" in a Mercurial merge
    changeset, as this list is often long and usually misleading and
    useless anyway. In the unusual case that you really wanted to see
    the details, you can always refer to hgwebdir or the command-line
    client.

## Version 1.7

-   Fixed a bug in hgweb support URL computation
    ([JENKINS-1038](https://issues.jenkins-ci.org/browse/JENKINS-1038))

## Version 1.6

-   Fixed a MalformedByteSequenceException
    ([report](http://www.nabble.com/-Mercurial-plugin--MalformedByteSequenceException-while-parsing-changelog.xml-to14435125.html))

## Version 1.5

-   Perform URL normalization on hgweb browser URL
    ([JENKINS-1038](https://issues.jenkins-ci.org/browse/JENKINS-1038))

## Version 1.4

-   Fixed a bug in escaping e-mail address
    ([report](http://www.nabble.com/Mercurial-changeset-parse-error-%28and-fix%29-tf4615936.html#a13182691))

## Version 1.3

-   Improved error diagnostics when 'hg id' command fails.
-   Added branch support
    ([JENKINS-815](https://issues.jenkins-ci.org/browse/JENKINS-815))
-   Help text was missing
-   Added version check to the form validation.

## Version 1.2

-   Updated to work with behavior changes in hg 0.9.4 (this plugin can
    still work with 0.9.3, too)
-   Plugin now works with slaves.

## Version 1.1

-   "hg incoming" now runs with the --quiet option to avoid status
    messages from going into changelog.xml
-   fixed crucial bug where "hg pull" was run even if "hg incoming"
    didn't find any changes.
