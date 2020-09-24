#!/bin/bash

# COLORS
reset="\033[0m"

purple="\033[0;35m"
red="\033[0;31m"
green="\033[0;32m"
tan="\033[0;33m"
blue_bold="\033[1;34m"
blue="\033[0;34m"
cyan="\033[1;36m"

# CHARS
check_mark="\xE2\x9C\x94"
cross_mark="\u2718"
arrow="\u2192"

# LOGGING FUNCTIONS

function header() {
    if [[ $# -lt 1 ]]; then
        error_exit "Usage: header <content>"
    fi

    printf "\n${purple}==========  %s  ==========${reset}\n" "$@"
}

function info() {
    if [[ $# -lt 1 ]]; then
        error_exit "Usage: info <content>"
    fi

    printf "${cyan}[INFO] %s${reset}\n" "$*"
}

function note() {
    if [[ $# -lt 1 ]]; then
        error_exit "Usage: note <content>"
    fi

    printf "${blue_bold}Note:${reset}  ${blue}%s${reset}\n" "$*"
}

function error() {
    local _msg=${1:-"An error occurred"}
    printf "${red}${cross_mark} [ERROR] %s${reset}\n" "${_msg}" >&2
}

function error_exit() {
    local _msg=${1:-"An error occurred"}
    local _code=${2:-"1"}

    error "${_msg}"
    exit "${_code}"
}

function warn() {
    local _msg=${1:-"Something is amiss"}
    printf "${tan}${arrow} [WARN] %s${reset}\n" "${_msg}"
}

function success() {
    local _msg=${1:-"Operation was successful"}
    printf "${green}${check_mark} %s${reset}\n" "${_msg}"
}

function check_success() {
    if [[ $# -lt 1 ]]; then
        error_exit "Usage: check_success <code> [<msg>]"
    fi

    if [[ $1 != 0 ]]; then
        local _msg=${2:-"Operation failed"}
        error_exit "${_msg}"
    fi
}
