FROM jenkins/java:eed7706a10fb
RUN apt-get update -y && apt-get install --no-install-recommends -y python2.7 python libpython2.7-dev make gcc gettext
RUN set -e; \
    for v in 1.9.3 2.9.2 3.9.2 4.0.2; do \
        wget -nv -O /tmp/mercurial.tar.gz https://www.mercurial-scm.org/release/mercurial-$v.tar.gz; \
        cd /opt; \
        tar xfz /tmp/mercurial.tar.gz; \
        rm /tmp/mercurial.tar.gz; \
        cd mercurial-$v; \
        make local; \
        ./hg --version; \
    done
