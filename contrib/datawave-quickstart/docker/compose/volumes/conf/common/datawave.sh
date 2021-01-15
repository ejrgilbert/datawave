export ACCUMULO_CONF_DIR=/opt/datawave/accumulo/conf
export HADOOP_CONF_DIR=/opt/datawave/hadoop/conf
export ZOOCFGDIR=/opt/datawave/zookeeper/conf
export ZOO_LOG_DIR=/srv/logs/zookeeper
export HADOOP_LOG_DIR=/srv/logs/hadoop
export YARN_LOG_DIR=/srv/logs/hadoop
export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
export ACCUMULO_HOME=/opt/accumulo/current
export HADOOP_HOME=/usr/lib/hadoop
export NIFI_HOME=/opt/niagarafiles/current

export PATH=${PATH}:${JAVA_HOME}/bin:${HADOOP_HOME}/bin:${ACCUMULO_HOME}/bin
