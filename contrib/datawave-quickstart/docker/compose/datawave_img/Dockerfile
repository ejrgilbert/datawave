FROM datawave_base

ARG ACCUMULO_VERSION
ARG HADOOP_VERSION
ARG ZOOKEEPER_VERSION

LABEL version.accumulo=${ACCUMULO_VERSION} \
      version.hadoop=${HADOOP_VERSION} \
      version.zookeeper=${ZOOKEEPER_VERSION}

# Can override these on the command line when running a new container if necessary
ENV DW_DATAWAVE_ACCUMULO_AUTHS="PUBLIC,PRIVATE,FOO,BAR,DEF,A,B,C,D,E,F,G,H,I,DW_USER,DW_SERV,DW_ADMIN,JBOSS_ADMIN"
ENV LIVE_INGEST_DATA_TYPES="wikipedia,mycsv,myjson"

ENV HADOOP_USER_NAME datawave
ENV DATAWAVE_HOME /opt/datawave

ENV HDFS_BASE "hdfs://namenode:8020"
ENV DW_DATAWAVE_INGEST_HDFS_BASEDIR "${HDFS_BASE}/data"
ENV DW_INSTALL_DIR /opt/datawave-ingest

ENV PATH "$PATH:$ACCUMULO_HOME/bin:$HADOOP_HOME/bin:$ZOOKEEPER_HOME/bin"

# Install datawave
ADD ./*.rpm /tmp/compose.rpm
RUN yum -y install /tmp/compose.rpm && \
    chown -R datawave:hadoop ${DW_INSTALL_DIR} && \
    mkdir -p /var/run/datawave && \
    chown -R datawave:hadoop /var/run/datawave && \
    runuser -l datawave -c "find ${DW_INSTALL_DIR} -name \"activate-install.sh\" -exec {} -noDiffPrompt \;"

ADD init.sh /init.sh
RUN chmod 777 /init.sh

# initialize container and start ingest
ENTRYPOINT ["/entrypoint.sh"]
CMD ["/init.sh"]





#ENV DATAWAVE_HOME /opt/datawave
#ENV ACCUMULO_HOME /opt/accumulo
#ENV HADOOP_HOME /usr/lib/hadoop
#ENV ZOOKEEPER_HOME /usr/lib/zookeeper
#
#RUN mkdir ${DATAWAVE_HOME}
#COPY ./configs/common /opt/datawave/
#COPY ./configs/flavor /opt/datawave/
#COPY configs/genders /etc
#
#RUN chmod 644 /etc/genders && \
#    chmod 755 /opt/datawave -R && \
#    find /opt/datawave -type d -print0 | xargs -0 chmod 750 && \
#    find /opt/datawave -type f -print0 | xargs -0 chmod 640 && \
#    chown -R datawave:hadoop /opt/datawave && \
#    for link in $(ls /opt/datawave | grep 99); do ln -sfT /opt/datawave/${link} /etc/security/limits.d/${link}; done && \
#    ln -snf /opt/datawave/datawave.sh /etc/profile.d/datawave.sh
#
#RUN update-alternatives --install /etc/hadoop/conf hadoop-conf /opt/datawave/hadoop/conf 100
#
##Configure ingest passwd file
#ADD datawave/base/ingest-passwd.sh /opt/datawave-ingest
#RUN chown datawave:datawave /opt/datawave-ingest/ingest-passwd.sh && \
#    sed -i "s/export PASSWORD=.*/export PASSWORD=datawave/" /opt/datawave-ingest/ingest-passwd.sh
#
## Fix permissions of various files
#RUN chmod 750 /opt/datawave-ingest/ingest-passwd.sh && \
#    mkdir -p /srv/logs/ingest && \
#    chmod 777 /srv/logs/ingest && \
#    mkdir -p /srv/vfs-cache && \
#    chmod 777 /srv/vfs-cache && \
#    mkdir -p /srv/run/datawave && \
#    chmod 777 /srv/run/datawave && \
#    chmod 775 /srv/data && \
#    chmod 750 /opt/accumulo && \
#    chmod 750 /opt/datawave-ingest && \
#    mkdir -p /srv/data/datawave/flags && \
#    chown -R datawave:hadoop /srv/data/datawave && \
#    chmod -R 755 /srv/data/datawave && \
#    mkdir -p /srv/data/ingest && \
#    chown datawave:hadoop /srv/data/ingest && \
#    chmod 775 /srv/data/ingest && \
#    mkdir /init
#
## Fix JAVA_HOME
#RUN rm /opt/datawave/zookeeper/conf/java.env
#ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk
#
#ADD datawave/base/init.sh /init/init.sh
#ADD datawave/init-ingest-master.sh /init/init-ingest-master.sh
#ADD datawave/accumulo-user.sh /init/accumulo-user.sh
#RUN chmod 777 /init/* && \
#    chown accumulo:accumulo /init/accumulo-user.sh && \
#    chmod 755 /init/accumulo-user.sh
#
## initialize container and start ingest
#ENTRYPOINT ["/init/init.sh"]
#CMD tail -F /dev/null