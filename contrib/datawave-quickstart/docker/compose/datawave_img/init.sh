#!/bin/bash

ACCUMULO_USER=datawave
ACCUMULO_PASS=datawave

function error() {
    echo "[ERROR] $1"
    exit 1
}

function configs() {
    rm -rf "${ACCUMULO_HOME}/conf"
    ln -s "${DATAWAVE_HOME}/accumulo/conf" "${ACCUMULO_HOME}"
    chown accumulo:hadoop "${ACCUMULO_HOME}/conf"

    # Setup hadoop config
    update-alternatives --install /etc/hadoop/conf hadoop-conf "${DATAWAVE_HOME}/hadoop/conf" 100
}

function user_profiles() {
    for link in "${DATAWAVE_HOME}"/*; do
        if [[ ${link} =~ "99" ]]; then
            ln -sfT "${DATAWAVE_HOME}/${link}" "/etc/security/limits.d/${link}"
        fi
    done
    ln -snf "${DATAWAVE_HOME}/datawave.sh" /etc/profile.d/datawave.sh
}

function ingest_passwd() {
    local _passwd_file="${DW_INSTALL_DIR}/ingest-passwd.sh"
    {
        echo "export PASSWORD=\"${ACCUMULO_PASS}\""
        echo "export TRUSTSTORE_PASSWORD=\"TODO\""
        echo "export KEYSTORE_PASSWORD=\"TODO\""
    } >"${_passwd_file}"

    chown datawave:hadoop "${_passwd_file}"
}

function hadoop_dirs() {

    # Create any Hadoop directories related to Datawave Ingest
    if [[ -n "${LIVE_INGEST_DATA_TYPES}" ]] ; then
       IFS=',' read -r -a HDFS_RAW_INPUT_DIRS <<< "${LIVE_INGEST_DATA_TYPES}"

       for dir in "${HDFS_RAW_INPUT_DIRS[@]}" ; do
          hdfs dfs -mkdir -p "${DW_DATAWAVE_INGEST_HDFS_BASEDIR}/${dir}" \
              || fatal "Failed to create HDFS directory: ${dir}"
       done
    fi

    hdfs dfs -chown -R datawave:hadoop "${HDFS_BASE}/tmp" \
        || error "Failed to change ownership of ${HDFS_BASE}/tmp"
}

function configure_accumulo() {
    # upload datawave libs to accumulo classpath
    local _dwv_classpath
    _dwv_classpath=$(xmllint --xpath 'string(//configuration/property[name="general.vfs.context.classpath.datawave"]/value)' "${ACCUMULO_HOME}/conf/accumulo-site.xml")
    _dwv_classpath=${_dwv_classpath%/*}
    echo "Uploading datawave libs to ${_dwv_classpath}...patience young padawan..."

    runuser -l datawave -c "hdfs dfs -put -f ${DW_INSTALL_DIR}/current/lib/* ${_dwv_classpath}" \
        || error "Failed to upload datawave libs to ${_dwv_classpath}"

    local _cfg_file="/tmp/accumulo.cfg"
    {
        echo "createnamespace datawave"
        echo "createuser ${ACCUMULO_USER}"
        echo "setauths -u ${ACCUMULO_USER} -s ${DW_DATAWAVE_ACCUMULO_AUTHS}"
        echo "config -s table.classpath.context=datawave"
        echo "grant -u ${ACCUMULO_USER} -s System.CREATE_TABLE"
        echo "grant -u ${ACCUMULO_USER} -s System.DROP_TABLE"
        echo "grant -u ${ACCUMULO_USER} -s System.ALTER_TABLE"
        echo "grant -u ${ACCUMULO_USER} -s System.CREATE_NAMESPACE"
        echo "grant -u ${ACCUMULO_USER} -s System.DROP_NAMESPACE"
        echo "grant -u ${ACCUMULO_USER} -s System.ALTER_NAMESPACE"
        echo "exit"
    } >${_cfg_file}
    chmod 666 ${_cfg_file}

    echo -e "${ACCUMULO_PASS}\n${ACCUMULO_PASS}\n" | /opt/accumulo/current/bin/accumulo shell -u root -p accumulo -f ${_cfg_file} \
        | sed '/.*WARN : Found no client \.conf in default paths.*/d'
}

function start_ingest() {
    runuser -l datawave -c "${DW_INSTALL_DIR}/current/bin/system/start-all.sh -force -noverify" \
        || error "start-all.sh failed"
}

configs
user_profiles
ingest_passwd

if nodeattr -v "$(hostname -s)" ingestmaster; then
    hadoop_dirs
    configure_accumulo
    start_ingest
fi

echo "Finished starting up datawave..."
