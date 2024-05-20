# curl -s https://raw.githubusercontent.com/jenkinsci/docker-fixtures/master/src/main/resources/org/jenkinsci/test/acceptance/docker/fixtures/JavaContainer/Dockerfile | sha1sum | cut -c 1-12
FROM jenkins/java:978f1af53461
RUN apt-get update -y && \
    apt-get install --no-install-recommends -y \
        python3 \
        python3-setuptools \
        libpython3-dev \
        make \
        gcc \
        gettext
RUN set -e; \
    for v in 6.6.3; do \
        wget -nv -O /tmp/mercurial.tar.gz https://www.mercurial-scm.org/release/mercurial-$v.tar.gz; \
        cd /opt; \
        tar xfz /tmp/mercurial.tar.gz; \
        rm /tmp/mercurial.tar.gz; \
        cd mercurial-$v; \
        make local; \
        ./hg --version; \
    done
