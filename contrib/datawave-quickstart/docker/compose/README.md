Pulls together Docker images from the following repositories:
- https://github.com/ejrgilbert/hadoop-cdh-docker
- https://github.com/apache/accumulo-docker
- https://github.com/ejrgilbert/zookeeper-cdh-docker

To start up full cluster:
```bash
export RPM_DIR=/srv/data1/datawave/rpms/
export CERT_DIR=/srv/data1/datawave/certs/
export HADOOP_CONFIG_DIR=/srv/data1/datawave/compose-hadoop-conf

export DATAWAVE_BASE_VERSION=1.0.1
export HADOOP_VERSION="2.6.0-cdh5.9.1"
export ZOOKEEPER_VERSION="3.4.5-cdh5.9.1"
export ACCUMULO_VERSION="1.9.2"
docker-compose -f docker-compose.zookeeper.yml -f docker-compose.hadoop.yml -f docker-compose.accumulo.yml -f docker-compose.ingest.yml up
```


To start up full cluster (detached from terminal):
```bash

docker-compose -f docker-compose.zookeeper.yml -f docker-compose.hadoop.yml -f docker-compose.accumulo.yml -f docker-compose.ingest.yml up -d
```