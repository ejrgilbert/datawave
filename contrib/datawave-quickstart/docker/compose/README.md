# The Docker-Compose Datawave Deployment #

## Description ##
This directory houses all the resources necessary to start up your own
Docker-Compose Datawave deployment. Docker-compose starts up a cluster
of docker images inside a docker network on the localhost. You can think
of them as a normal cluster of machines running Accumulo, Hadoop, etc.
You can even `ssh` between the nodes!

Developers no longer have to install software locally! Rather, software
versions that correspond to the specific Datawave version get spun up
on the localhost inside a docker-compose network. You can read more about
this in the section describing the `build.sh` script.

### Components ###
There are quite a few components that work together to enable you to actually
perform a deployment into a docker-compose environment. Read on to get more
context on what these components are as you browse this directory.

#### Docker Images ####
The docker-compose environment is just a group of docker containers spun up
inside the network.

The following repositories house the code for the various Docker images:
- [Base image](https://github.com/ejrgilbert/compose-base-image)
- [Zookeeper](https://github.com/ejrgilbert/zookeeper-cdh-docker)
- [Hadoop](https://github.com/ejrgilbert/hadoop-cdh-docker)
- [Accumulo](https://github.com/ejrgilbert/accumulo-docker)
- [Datawave base image](https://github.com/ejrgilbert/datawave-docker)

#### `docker-compose.*.yml` ####
These files stub out the architecture for the deployment. Feel free to read
through them as they will probably help demystify the environment. They
basically define various nodes and services by specifying the image to use,
the name the container will have when running in the cluster (can also refer
to that name in DNS), mount volumes to the container, etc.

#### Configurations ###
The `conf` directory houses the various configurations for the docker-compose
network. There are some configurations that are shared by all the nodes (inside
the `common` subdirectory), then there are configurations that are software-
specific (inside the `flavor` subdirectory). We have also enabled the use of
`gender` inside the cluster. Read through the `conf/genders` file to see which
ones are available for use.

We mount these directories the docker containers as shown in the `docker-compose.*.yml`
files.

#### yum Repository ###
The `yum` directory contains a yum repo which is used when installing the Datawave RPM
on the ingest master. The `build.sh` script copies the RPM to the `yum` repository to
make it available for the ingest master installation.

See the `yumrepo` service in the `docker-compose.extras.yml` file for how we configure
the container that hosts the `yum` repository.
 
#### `build.sh` ####
Developers use this script to stub out a new Datawave deployment on their local box.
There are many options that can be used for configuring a new deployment. Run
`build.sh help` to see the usage for this script.

When a developer sets up a new configuration via `build.sh`, it outputs this configuration
to a `.env` file to enable starting/stopping the docker-compose deployment without having
to rerun `build.sh`. This script only needs to be rerun when the developer wants to do a
deployment with different configurations than before. It doesn't hurt to run it multiple
times, it's just more efficient to reuse the previous configuration if possible.

This script enables you to deploy the current state of your Datawave project on the localhost
OR starting up a released version of Datawave. If it is a deployment of your local project, it
will build an RPM, place it in the `yum` repo for the ingest master installation, and automatically
start up a stack of the corresponding software versions.

If it is a deployment of some Datawave release, it will pull the image from the docker registry,
automatically figure out which versions of the stack to startup, stub out that deployment in
the `.env` file, and start up the cluster!

#### `compose-ctl.sh` ####
As the name suggests, this script is to control the compose deployment. Once a new deployment
gets stubbed out and started by `build.sh`, developers can just use `compose-ctl.sh` to start/stop/etc
the deployment. It will always use the most-recent deployment built by `build.sh`.

Run `compose-ctl.sh help` to read the script usage.

### Example Use ###

#### ex1 ####

> "Boy, I would love to see if this code I'm writing actually works...
> I wonder if it even compiles?"

```bash
# Do a build and see if it is successful
./build.sh
```

> "Great! It compiles! But I need to see if I can actually ingest data with it..."

```bash
# We just built the RPM, so let's skip that part just do a deployment
./build.sh --skip-build --deploy
```

> "Oh shoot! I messed up something in my code...let's retry that...
> but I'd like to keep the Accumulo data I just ingested"

```bash
# Stop the compose cluster, build a new RPM and start up a new deployment that will persist the Accumulo data
./compose-ctl.sh stack stop
./build.sh --deploy --persist
```

#### ex2 ####

> "Looks like my compose cluster is down since my box got shut down when I left work...
> let's start that back up"

```bash
./compose-ctl.sh stack status
# Yep...it's definitely down, let's start it up...I'd like to keep my data though
./compose-ctl.sh stack start --persist
```

#### ex3 ####

> "Hmm...I wonder if Datawave 2.8.8 can handle this datatype I'm working on..."

```bash
# Start up a new docker-compose deployment for Datawave version 2.8.8
./build.sh --version 2.8.8
```

> "Let's see if the cluster finished starting up."

```bash
./compose-ctl.sh stack status
```

> "Looks like the ingest master is having a hard time...maybe I should restart it..."

```bash
./compose-ctl ingest down
./compose-ctl ingest up
```
