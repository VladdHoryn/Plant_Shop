# CK (Chidamber & Kemerer metrics for Java) with every version pinned, like tools/lizard and tools/halstead.
# A metric may only change when the code changes, never because the analyzer or the runtime moved.
#   docker build -t plantshop/ck:0.7.0-jre21 tools/ck
#   docker run --rm -v "$PWD:/src" -w /src plantshop/ck:0.7.0-jre21 <dir> false 0 false <out-prefix>
# Arguments (CK CLI): <project dir> <use jars: false> <max files per partition: 0 = auto> <variables/fields
# metrics: false> <output dir prefix>. Output: <prefix>class.csv and <prefix>method.csv.
FROM eclipse-temurin:21.0.5_11-jre
ARG CK_VERSION=0.7.0
ARG CK_SHA1=4c6e012e0fe3525c98f4cd4794336ed93faa1378
ADD https://repo1.maven.org/maven2/com/github/mauricioaniche/ck/${CK_VERSION}/ck-${CK_VERSION}-jar-with-dependencies.jar /opt/ck/ck.jar
RUN echo "${CK_SHA1}  /opt/ck/ck.jar" | sha1sum -c - && chmod 644 /opt/ck/ck.jar
WORKDIR /src
USER nobody
ENTRYPOINT ["java", "-jar", "/opt/ck/ck.jar"]
