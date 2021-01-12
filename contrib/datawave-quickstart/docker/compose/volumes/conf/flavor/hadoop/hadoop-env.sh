# Mounting these log4j configs break non-hadoop
# nodes since log4j tries to write to a file
# at ${HADOOP_LOG_DIR}/${HADOOP_LOGFILE}...
# to avoid, only mount this file to hadoop nodes!

export HADOOP_HEAPSIZE=1024
export HADOOP_ROOT_LOGGER="INFO,console,RFA"
export HADOOP_LOG_DIR="/srv/logs/hadoop"
export HADOOP_LOGFILE="$(hostname).log"
export JAVA_HOME
