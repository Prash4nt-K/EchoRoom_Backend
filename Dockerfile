FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache nginx \
	&& addgroup -S app \
	&& adduser -S app -G app \
	&& mkdir -p /tmp/client_temp /tmp/proxy_temp /tmp/fastcgi_temp /tmp/uwsgi_temp /tmp/scgi_temp \
	&& chown -R app:app /app /tmp/client_temp /tmp/proxy_temp /tmp/fastcgi_temp /tmp/uwsgi_temp /tmp/scgi_temp

COPY --from=build /app/target/*.jar app.jar
COPY nginx.conf /app/nginx.conf
COPY nginx-render.conf.template /app/nginx-render.conf.template
COPY render-entrypoint.sh /app/render-entrypoint.sh

RUN chmod +x /app/render-entrypoint.sh

USER app

ENV JAVA_OPTS=""
ENV PORT=10000
ENV SERVER_PORT=8080
ENV SOCKETIO_PORT=9092

EXPOSE 10000

ENTRYPOINT ["/app/render-entrypoint.sh"]
