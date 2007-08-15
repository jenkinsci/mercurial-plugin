#!/bin/sh -ex
# import ./ini4j.jar and ./init4j-sources.jar to the repository
repo=/kohsuke/Sun/java.net/m2-repo
mvn deploy:deploy-file -Dfile=ini4j.jar -Durl=file://$(cygpath -wa $repo) -DpomFile=ini4j.pom
mvn deploy:deploy-file -Dfile=ini4j-sources.jar -Durl=file://$(cygpath -wa $repo) -DpomFile=ini4j.pom -Dclassifier=sources
