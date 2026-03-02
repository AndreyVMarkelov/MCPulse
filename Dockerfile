FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build
COPY . .
RUN ./gradlew jar --no-daemon

# ─────────────────────────────────────────────
FROM alpine:3.19 AS jmeter-base

ARG JMETER_VERSION=5.6.3

RUN apk add --no-cache \
    openjdk17-jre \
    curl \
    bash \
    python3 \
    nodejs

# Install JMeter
RUN curl -sL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.tgz" \
    | tar -xz -C /opt && \
    ln -s /opt/apache-jmeter-${JMETER_VERSION} /opt/jmeter

ENV JMETER_HOME=/opt/jmeter
ENV PATH="${JMETER_HOME}/bin:${PATH}"

FROM jmeter-base AS mcpulse

# Copy built plugin jar
COPY --from=builder /build/build/libs/*.jar /opt/jmeter/lib/ext/

# Copy local mock MCP servers + test plans
COPY scripts/ /opt/mcpulse/scripts/
COPY test-plans/*.jmx /opt/mcpulse/plans/
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

WORKDIR /opt/mcpulse

ENTRYPOINT ["/entrypoint.sh"]
