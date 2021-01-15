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
