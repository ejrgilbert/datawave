#!/bin/bash

if [[ `uname` == "Darwin" ]]; then
    THIS_SCRIPT=`python -c 'import os,sys;print os.path.realpath(sys.argv[1])' $0`
    MKTEMP="mktemp -t `basename $0`"
else
    THIS_SCRIPT=`readlink -f $0`
    MKTEMP="mktemp -t `basename $0`.XXXXXXXX"
fi
THIS_DIR="${THIS_SCRIPT%/*}"
cd $THIS_DIR
script_name=$(basename ${0})

. ../ingest/ingest-env.sh

export METRICS_BIN=$THIS_DIR/..

FORCE="-force"
if [[ "$@" =~ ".*-cron.*" || "$@" =~ "-cron" ]]; then
   FORCE="";
fi


# now apply the appropriate system configuration
if [[ "$INGEST_HOST" == "localhost" || "$INGEST_HOST" == `hostname` || "$INGEST_HOST" == `hostname -s` ]]; then

  $METRICS_BIN/metrics/startMetricsIngest.sh ingest $FORCE
  $METRICS_BIN/metrics/startMetricsIngest.sh loader $FORCE
  $METRICS_BIN/metrics/startMetricsIngest.sh flagmaker $FORCE

else

  ingestHost=`$MKTEMP`
  trap 'rm -f "$ingestHost"; exit $?' INT TERM EXIT
  echo $INGEST_HOST > $ingestHost

  localhost=$(hostname -s)
  PDSH_OUT="${PDSH_LOG_DIR}/${script_name}/$$"
  rm -rf ${PDSH_OUT}
  pdsh -f 25 -w ^${ingestHost} "$METRICS_BIN/metrics/startMetricsIngest.sh ingest $FORCE" < /dev/null \
    1> >(dshbak -f -d ${PDSH_OUT}/ingest_cmd/stdout) \
    2> >(dshbak -f -d ${PDSH_OUT}/ingest_cmd/stderr); \
    cat ${PDSH_OUT}/ingest_cmd/stderr/pdsh\@${localhost} 2> /dev/null
  pdsh -f 25 -w ^${ingestHost} "$METRICS_BIN/metrics/startMetricsIngest.sh loader $FORCE" < /dev/null \
    1> >(dshbak -f -d ${PDSH_OUT}/loader_cmd/stdout) \
    2> >(dshbak -f -d ${PDSH_OUT}/loader_cmd/stderr); \
    cat ${PDSH_OUT}/loader_cmd/stderr/pdsh\@${localhost} 2> /dev/null
  pdsh -f 25 -w ^${ingestHost} "$METRICS_BIN/metrics/startMetricsIngest.sh flagmaker $FORCE" < /dev/null \
    1> >(dshbak -f -d ${PDSH_OUT}/flagmaker_cmd/stdout) \
    2> >(dshbak -f -d ${PDSH_OUT}/flagmaker_cmd/stderr); \
    cat ${PDSH_OUT}/flagmaker_cmd/stderr/pdsh\@${localhost} 2> /dev/null

  rm $ingestHost
  trap - INT TERM EXIT

fi

