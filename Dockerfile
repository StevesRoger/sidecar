FROM amazoncorretto:17.0.13
ARG JAR_FILE=app.jar
ARG JAVA_OPTS="-Xms2G -Xmx4G"
ENV JAVA_OPTS=${JAVA_OPTS}
ARG LISTEN_PORT=8080
ENV LISTEN_PORT=${LISTEN_PORT}
ENV TZ=Asia/Phnom_Penh
RUN yum update -y && yum upgrade -y
RUN yum install -y tzdata && yum install -y telnet && yum install -y curl
RUN yum install -y dnsutils && yum install -y iputils-ping
RUN yum install -y procps
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY ${JAR_FILE} app.jar
EXPOSE $LISTEN_PORT
ENTRYPOINT java $JAVA_OPTS -jar -Dserver.port=$LISTEN_PORT -Djava.security.egd=file:/dev/./urandom app.jar