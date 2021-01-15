#!/bin/bash

THIS_DIR=$(dirname "$(realpath "$0")")
DATAWAVE_DIR=$(realpath "${THIS_DIR}/../../../..")

# NOTE: REGISTRY_URL is used to specify where to push the built images...must have trailing '/'
# shellcheck disable=SC1090
source "${THIS_DIR}/../util/logging.sh"

DATAWAVE="datawave"
HELP="help"

header "Docker Build Utility"

function usage() {
    echo "Usage: $0 [-v|--version ver1 ver2 ...] [-p|--push] -- [${HELP}]"
    echo -e "\t${HELP} - to see this message"
    echo "Options:"
    echo -e "\t-v - What versions to tag the images as"
    echo -e "\t-p - Pass this option if you want to push to the REGISTRY"
    echo -e "\t-s - If you want to skip the build RPM step"
    exit 1
}

function build_failed() {
    error_exit "Build of $1 failed"
}

function build_rpm() {
    # shellcheck disable=SC2086
    ${THIS_DIR}/../build.sh
}

function stage_rpm() {
    RPM=$(find "${DATAWAVE_DIR}" -name "*.rpm" 2>/dev/null | grep datawave-dw-compose | head -n 1)
    [[ -z ${RPM} ]] && error_exit "Could not find build RPM...exiting..."
    echo "Using RPM at: $RPM"
    cp "${RPM}" "${THIS_DIR}"
    trap 'rm ${THIS_DIR}/*.rpm' EXIT
}

function get_stack_versions() {
    pushd "${DATAWAVE_DIR}" >/dev/null || error_exit "Could not change dirs to ${DATAWAVE_DIR}"
    export IMAGE_VERSION=$(mvn help:evaluate -Dexpression=project.version | grep -v '^\[' | grep -v "Downloading" 2>/dev/null)
    export HADOOP_VERSION=$(mvn help:evaluate -Dexpression=version.hadoop | grep -v '^\[' | grep -v "Downloading" 2>/dev/null)
    export ZOOKEEPER_VERSION=$(mvn help:evaluate -Dexpression=version.zookeeper | grep -v '^\[' | grep -v "Downloading" 2>/dev/null)
    export ACCUMULO_VERSION=$(mvn help:evaluate -Dexpression=version.accumulo | grep -v '^\[' | grep -v "Downloading" 2>/dev/null)
    popd >/dev/null || error_exit "Could not change dirs"
}

function build_datawave_img() {
    info "Building ${DATAWAVE} docker image"
    [[ $SKIP_BUILD_RPM != "true" ]] && build_rpm
    stage_rpm

    get_stack_versions
    if ! docker build -t ${DATAWAVE} \
                --build-arg HADOOP_VERSION="${HADOOP_VERSION}" \
                --build-arg ZOOKEEPER_VERSION="${ZOOKEEPER_VERSION}" \
                --build-arg ACCUMULO_VERSION="${ACCUMULO_VERSION}" .; then
        build_failed ${DATAWAVE}
    fi
    success "Completed building ${DATAWAVE} docker image"
}

function tag_images() {
    info "Tagging docker images as: [ ${IMAGE_VERSIONS[*]} ]"
    for v in "${IMAGE_VERSIONS[@]}"; do
        docker tag "${DATAWAVE}" "${REGISTRY_URL}${DATAWAVE}:$v" || error_exit "Failed to tag ${REGISTRY_URL}${DATAWAVE}:$v"
    done
    success "Completed tagging docker images"
}

function push_images() {
    info "Pushing docker images"
    for v in "${IMAGE_VERSIONS[@]}"; do
        docker push "${REGISTRY_URL}${DATAWAVE}:$v" || error_exit "Failed to push ${REGISTRY_URL}${DATAWAVE}:$v"
    done
    success "Completed pushing docker images"
}

if [[ $* =~ ${HELP} ]]; then
    usage
fi

# From: https://medium.com/@Drew_Stokes/bash-argument-parsing-54f3b81a6a8f
while (( "$#" )); do
    case "$1" in
        -s|--skip-build )
            SKIP_BUILD_RPM="true"
            ;;
        -v|--version )
            if [ -z "$2" ]; then
                error_exit "Argument required for $1"
            fi
            shift 1

            while [ -n "$1" ] && [ "${1:0:1}" != "-" ]; do
                IMAGE_VERSIONS+=("$1")
                shift 1
            done
            ;;
        -p|--push )
            PUSH="true"
            ;;
        -? )
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

build_datawave_img

# Set one image version as the current version of Datawave in this git repo
IMAGE_VERSIONS=( "${IMAGE_VERSION}" )

if [[ -n ${IMAGE_VERSIONS[*]} ]]; then
    tag_images
fi

if [[ "${PUSH}" == "true" ]]; then
    push_images
fi
