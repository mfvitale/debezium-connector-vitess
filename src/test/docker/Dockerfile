# Use a temporary layer for the build stage.
FROM quay.io/debezium/vitess-base:v19.0.4 AS base

FROM quay.io/debezium/vitess-lite:v21.0.3

USER root

RUN apt-get update
RUN apt-get install -y sudo curl vim jq

# Install etcd
COPY install_local_dependencies.sh /vt/dist/install_local_dependencies.sh
RUN /vt/dist/install_local_dependencies.sh

# Copy binaries used by vitess components start-up scripts
COPY --from=base /vt/bin/vtctl /vt/bin/
COPY --from=base /vt/bin/mysqlctl /vt/bin/

# Copy vitess components start-up scripts
COPY local /vt/local

USER vitess
ENV PATH /vt/bin:$PATH
ENV PATH /var/opt/etcd:$PATH
CMD cd /vt/local && ./initial_cluster.sh && tail -f /dev/null
