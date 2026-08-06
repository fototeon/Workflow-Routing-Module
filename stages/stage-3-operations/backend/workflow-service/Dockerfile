FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# `ca.crt` is optional (note the glob): drop your corporate root certificate — or a whole PEM bundle —
# next to this Dockerfile and every certificate in it is added to the JVM trust store. Without it,
# networks that intercept TLS fail the build with "PKIX path building failed".
COPY pom.xml ca.cr[t] ./
RUN if [ -f ca.crt ]; then \
      csplit -z -s -f /tmp/cert- -b '%03d.pem' ca.crt '/-----BEGIN CERTIFICATE-----/' '{*}' && \
      n=0; for cert in /tmp/cert-*.pem; do \
        n=$((n+1)); \
        keytool -importcert -cacerts -storepass changeit -noprompt -alias "extra-ca-$n" -file "$cert" >/dev/null 2>&1 || true; \
      done; \
      echo "Trusted $n extra certificate(s)"; \
    fi && \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/workflow-service-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
