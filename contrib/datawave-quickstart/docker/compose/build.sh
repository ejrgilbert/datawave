#!/bin/bash

THIS_DIR=$(dirname "$(realpath "$0")")
DATAWAVE_DIR=$(realpath "${THIS_DIR}/../../../..")
DEPLOY_ENV="${THIS_DIR}/.env"

# shellcheck disable=SC1090
source "${THIS_DIR}/util/logging.sh"

# Define some variables for docker-compose deployment use
DATAWAVE_BASE_IMG="datawave_base"
DATAWAVE_BASE_VERSION="1.0.0"

# Ports to expose in the deployment
ZOO_PORT=2181
HADOOP_NAMENODE_PORT=50070
ACCUMULO_MASTER_PORT=9999
ACCUMULO_MONITOR_PORT=50095
HADOOP_DATANODE_PORT=50075

# Mounted files/directories locations
CERT_DIR=${THIS_DIR}/certs
CONF_DIR=${THIS_DIR}/conf
YUM_FILE=${THIS_DIR}/yum/custom.repo
YUM_REPO=${THIS_DIR}/yum/repo

# Define variables in `.env` file
rm "${DEPLOY_ENV}"
{
    echo "export DATAWAVE_BASE_VERSION=${DATAWAVE_BASE_VERSION}"

    echo "export ZOO_PORT=${ZOO_PORT}"
    echo "export HADOOP_NAMENODE_PORT=${HADOOP_NAMENODE_PORT}"
    echo "export ACCUMULO_MASTER_PORT=${ACCUMULO_MASTER_PORT}"
    echo "export ACCUMULO_MONITOR_PORT=${ACCUMULO_MONITOR_PORT}"
    echo "export HADOOP_DATANODE_PORT=${HADOOP_DATANODE_PORT}"

    echo "export CERT_DIR=${CERT_DIR}"
    echo "export CONF_DIR=${CONF_DIR}"
    echo "export YUM_FILE=${YUM_FILE}"
    echo "export YUM_REPO=${YUM_REPO}"
} >>"${DEPLOY_ENV}"

header "Datawave Build/Deploy Script"

function usage() {
    echo "Usage: $0 [ -s|--skip-build ] [ -d|--deploy ] [ -v|--version <datawave_tag> ] [ -p|--persist ]"
    echo -e "\tBy default, this script will build a new RPM from the datawave mvn project located at ${DATAWAVE_DIR}"
    echo -e "\tOptions:"
    echo -e "\t\t--skip-build - If you would like to launch a docker-compose cluster for a specific Datawave tag version."
    echo -e "\t\t             NOTE: This will NOT build and deploy a new RPM from the local Datawave repo"
    echo -e "\t\t--deploy - If you want to launch a new docker-compose cluster that deploys the built Datawave RPM"
    echo -e "\t\t--version - If you want to launch a specific datawave tagged release in the docker compose environment."
    echo -e "\t\t            The docker image with that release installed will be run in a complete compose cluster."
    echo -e "\t\t--persist - to retain old accumulo data from a previous deploy"
    exit 1
}

function build_rpm() {
    pushd "${DATAWAVE_DIR}" >/dev/null || error_exit "Could not change dirs to ${DATAWAVE_DIR}"
    mvn -Pcompose,assemble,rpm -Ddeploy -Dtar -Ddist -DskipTests clean install || \
        error_exit "Build failed..."
    popd >/dev/null || error_exit "Could not change dirs"
}

function deploy_rpm() {
    echo "Deploying RPM..."
    # shellcheck disable=SC2086
    rm -f ${YUM_REPO}/*.rpm
    RPM=$(find "${DATAWAVE_DIR}" -name "*.rpm" 2>/dev/null | grep datawave-dw-compose)
    [[ -z ${RPM} ]] && error_exit "Could not find build RPM...exiting..."
    echo "Using RPM at: $RPM"
    cp "${RPM}" "${YUM_REPO}" || \
        error_exit "Could not stage RPM in docker-compose yum repo"

    # Define variables needed in deployment
    DATAWAVE_IMG="${DATAWAVE_BASE_IMG}:${DATAWAVE_BASE_VERSION}"

    pushd "${DATAWAVE_DIR}" >/dev/null || error_exit "Could not change dirs to ${DATAWAVE_DIR}"
    # shellcheck disable=SC2155
    HADOOP_VERSION=$(mvn help:evaluate -Dexpression=version.hadoop | grep -v '^\[' | grep -v "Downloading" 2>/dev/null)
    # shellcheck disable=SC2155
    ZOOKEEPER_VERSION=$(mvn help:evaluate -Dexpression=version.zookeeper | grep -v '^\[' | grep -v "Downloading"  2>/dev/null)
    # shellcheck disable=SC2155
    ACCUMULO_VERSION=$(mvn help:evaluate -Dexpression=version.accumulo | grep -v '^\[' | grep -v "Downloading"  2>/dev/null)
    popd >/dev/null || error_exit "Could not change dirs"

    {
        echo "export DATAWAVE_IMG=${DATAWAVE_IMG}"
        echo "export HADOOP_VERSION=${HADOOP_VERSION}"
        echo "export ZOOKEEPER_VERSION=${ZOOKEEPER_VERSION}"
        echo "export ACCUMULO_VERSION=${ACCUMULO_VERSION}"
    } >>"${DEPLOY_ENV}"

    echo "Running compose cluster with the following stack:"
    echo -e "\t- DATAWAVE_IMG: ${DATAWAVE_IMG}"
    echo -e "\t- ACCUMULO_VERSION: ${ACCUMULO_VERSION}"
    echo -e "\t- HADOOP_VERSION: ${HADOOP_VERSION}"
    echo -e "\t- ZOOKEEPER_VERSION: ${ZOOKEEPER_VERSION}"
    # shellcheck disable=SC2086
    ${THIS_DIR}/compose-ctl.sh stack up ${EXTRA_ARGS} || \
        error_exit "Could not bring up docker-compose stack"
}

function run_tag_deployment() {
    # Define variables needed in deployment
    DATAWAVE_IMG="datawave:${TAG}"

    # Pull out stack versions from datawave image labels
    # shellcheck disable=SC2155
    ACCUMULO_VERSION=$(docker inspect --format '{{ index .Config.Labels "version.accumulo"}}' "${DATAWAVE_IMG}")
    [[ $ACCUMULO_VERSION == "" ]] && error_exit "Unable to pull 'version.accumulo' from labels on '${DATAWAVE_IMG}' image"

    # shellcheck disable=SC2155
    HADOOP_VERSION=$(docker inspect --format '{{ index .Config.Labels "version.hadoop"}}' "${DATAWAVE_IMG}")
    [[ $HADOOP_VERSION == "" ]] && error_exit "Unable to pull 'version.hadoop' from labels on '${DATAWAVE_IMG}' image"

    # shellcheck disable=SC2155
    ZOOKEEPER_VERSION=$(docker inspect --format '{{ index .Config.Labels "version.zookeeper"}}' "${DATAWAVE_IMG}")
    [[ $ZOOKEEPER_VERSION == "" ]] && error_exit "Unable to pull 'version.zookeeper' from labels on '${DATAWAVE_IMG}' image"

    {
        echo "export DATAWAVE_IMG=${DATAWAVE_IMG}"
        echo "export HADOOP_VERSION=${HADOOP_VERSION}"
        echo "export ZOOKEEPER_VERSION=${ZOOKEEPER_VERSION}"
        echo "export ACCUMULO_VERSION=${ACCUMULO_VERSION}"
    } >>"${DEPLOY_ENV}"

    echo "Running compose cluster with the following stack:"
    echo -e "\t- DATAWAVE_IMG: ${DATAWAVE_IMG}"
    echo -e "\t- ACCUMULO_VERSION: ${ACCUMULO_VERSION}"
    echo -e "\t- HADOOP_VERSION: ${HADOOP_VERSION}"
    echo -e "\t- ZOOKEEPER_VERSION: ${ZOOKEEPER_VERSION}"
    # shellcheck disable=SC2086
    ${THIS_DIR}/compose-ctl.sh stack up ${EXTRA_ARGS} || \
        error_exit "Could not bring up docker-compose stack"
}

# From: https://medium.com/@Drew_Stokes/bash-argument-parsing-54f3b81a6a8f
BUILD_RPM="true"
while (( "$#" )); do
    case "$1" in
        -s|--skip-build)
            BUILD_RPM="false"
            shift
            ;;
        -d|--deploy)
            DEPLOY_RPM="true"
            shift
            ;;
        -v|--version )
            if [ -n "$2" ] && [ "${2:0:1}" != "-" ]; then
                TAG=$2
                BUILD_RPM="false"
                shift 2
            else
                error_exit "Argument required for $1"
            fi
            ;;
        -p|--persist)
            EXTRA_ARGS="--persist"
            shift
            ;;
        -?|--help|help )
            usage
            ;;
        -*|--*=) # unsupported flags
            error_exit "Unsupported flag $1"
            ;;
        *)
            error_exit "Unsupported positional argument: $1"
            ;;
    esac
done

if [[ -n ${TAG} ]]; then
    run_tag_deployment
else
    if [[ ${BUILD_RPM} == "true" ]]; then
        build_rpm
    fi

    if [[ ${DEPLOY_RPM} == "true" ]]; then
        deploy_rpm
    fi
fi

exit 0


# TODO In order to get to where I can start up the full cluster, I'll need to get several more things working:
    # - To use the Ansible scripts:
    #   - clone repository if it doesn't exist locally
    #     - if it does print out the branch you're using...if not master check if you want to continue
    # - I'd also like to add a datawave CI job to create a new datawave docker image for each tagged datawave release
