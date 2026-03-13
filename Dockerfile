FROM docker-registry.service.telavox.se/telavox/corretto-jdk21-buildenv

RUN apt-get update && \
    apt-get -y install procps net-tools && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY server/target/lib /app/lib
COPY server/target/server-1.0-SNAPSHOT.jar /app/mediaserverloadbalancer.jar

CMD ["java", "-cp", "mediaserverloadbalancer.jar:lib/*", "se.telavox.mediaserverloadbalancer.server.MediaServerLoadbalancer"]