#!/bin/bash

THIS_DIR=$(dirname "$(realpath "$0")")

source "${THIS_DIR}/util/deploy.env"
source "${THIS_DIR}/util/logging.sh"

ZOO="zoo"
HADOOP="hadoop"
ACCUMULO="accumulo"
INGEST="ingest"
STACK="stack"

UP="up"
DOWN="down"
STATUS="status"
HELP="help"

PERSIST="--persist"

BASE_FILE="base_services.yml"
EXTRAS_FILE="docker-compose.extras.yml"

ZOO_FILE="docker-compose.zookeeper.yml"
HADOOP_FILE="docker-compose.hadoop.yml"
ACCUMULO_FILE="docker-compose.accumulo.yml"
PERSIST_FILE="docker-compose.persist.yml"
INGEST_FILE="docker-compose.ingest.yml"

# List of compose files that this control script will always start with
COMPOSE_FILES="-f ${BASE_FILE} -f ${EXTRAS_FILE}"

header "Docker Compose Control Script"

function usage() {
    echo "Usage: $0 (<item>) (<action>) [${PERSIST}]"
    echo -e "\t<item>:"
    echo -e "\t\t${ZOO} - perform action on the ${ZOO} services"
    echo -e "\t\t${HADOOP} - perform action on the ${HADOOP} services"
    echo -e "\t\t${ACCUMULO} - perform action on the ${ACCUMULO} services"
    echo -e "\t\t${INGEST} - perform action on the ${INGEST} services"
    echo -e "\t\t${STACK} - perform action on all services"
    echo -e "\t<action>:"
    echo -e "\t\t${UP} - start up the specified service(s)"
    echo -e "\t\t${DOWN} - tear down the specified service(s)"
    echo -e "\t\t${STATUS} - get the status of the specified service(s)"
    echo -e "\t\t${HELP} - to see this message"
    echo -e "\tOptions:"
    echo -e "\t\t${PERSIST} - USE WITH ACCUMULO OR STACK ITEM, to retain old accumulo data from a previous deploy"
    exit 1
}

function up() {
    pushd "${THIS_DIR}" >/dev/null || error_exit "Could not change dirs to ${THIS_DIR}"

    # If restarting the stack, remove all orphaned containers first
    if [[ ${FULL_STACK} == "true" ]]; then
        # shellcheck disable=SC2086
        docker-compose ${COMPOSE_FILES} down --remove-orphans
    fi
    # TODO uncomment after images have been pushed to registry
    # docker-compose "${COMPOSE_ARCHITECTURE}" pull --ignore-pull-failures
    # shellcheck disable=SC2086
    docker-compose ${COMPOSE_FILES} up -d

    popd >/dev/null || error_exit "Could not change dirs"
}

function down() {
    pushd "${THIS_DIR}" >/dev/null || error_exit "Could not change dirs to ${THIS_DIR}"
    # shellcheck disable=SC2086
    docker-compose ${COMPOSE_FILES} down
    popd >/dev/null || error_exit "Could not change dirs"
}

function status() {
    pushd "${THIS_DIR}" >/dev/null || error_exit "Could not change dirs to ${THIS_DIR}"
    # shellcheck disable=SC2086
    docker-compose ${COMPOSE_FILES} ps
    popd >/dev/null || error_exit "Could not change dirs"
}

function add_zoo() {
    COMPOSE_FILES="${COMPOSE_FILES} -f ${ZOO_FILE}"
}

function add_hadoop() {
    COMPOSE_FILES="${COMPOSE_FILES} -f ${HADOOP_FILE}"
}

function add_accumulo() {
    COMPOSE_FILES="${COMPOSE_FILES} -f ${ACCUMULO_FILE}"
}

function add_ingest() {
    COMPOSE_FILES="${COMPOSE_FILES} -f ${INGEST_FILE}"
}

function add_persist() {
    COMPOSE_FILES="${COMPOSE_FILES} -f ${PERSIST_FILE}"
}

if [[ $* =~ ${PERSIST} || $* =~ -p ]]; then
    add_persist
fi

case $1 in
    ${ZOO} )
        add_zoo
        ;;
    ${HADOOP} )
        add_hadoop
        ;;
    ${ACCUMULO} )
        add_accumulo "$*"
        ;;
    ${INGEST} )
        add_ingest
        ;;
    ${STACK} )
        add_zoo
        add_hadoop
        add_accumulo "$*"
        add_ingest
        FULL_STACK="true"
        ;;
    *)
        error "Invalid argument: $1"
        if [[ $1 == "${PERSIST}" || $1 == "-p" ]]; then
            echo "Please put $1 at the end of the passed args"
        fi
        usage
        ;;
esac
shift 1

case $1 in
    ${UP} )
        up
        ;;
    ${DOWN} )
        down
        ;;
    ${STATUS} )
        status
        ;;
    ${HELP} )
        usage
        ;;
    * )
        error "Invalid argument: $1"
        if [[ $1 == "${PERSIST}" || $1 == "-p" ]]; then
            echo "Please put $1 at the end of the passed args"
        fi
        usage
esac

exit 0
