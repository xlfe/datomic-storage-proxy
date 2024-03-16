rm -rf target
mkdir target
clj -M:build-driver
cp -f target/net.xlfe.dsp.jdbc.driver-*.jar datomic-pro/lib/
pushd ./datomic-pro

XMX=-Xmx1g
XMS=-Xms1g
JAVA_OPTS='-XX:+UseG1GC -XX:MaxGCPauseMillis=50'
CP=$(bash bin/classpath)

echo "Launching with Java options -server $XMS $XMX $JAVA_OPTS"
exec java -server -cp "$CP" $XMX $XMS $JAVA_OPTS clojure.main --main datomic.launcher ../transactor.properties

popd
