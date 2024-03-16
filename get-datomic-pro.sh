#!/bin/sh
VERSION=1.0.7075
curl -o datomic-pro.zip https://datomic-pro-downloads.s3.amazonaws.com/${VERSION}/datomic-pro-${VERSION}.zip

unzip datomic-pro.zip
mv datomic-pro-${VERSION} datomic-pro

pushd datomic-pro
mv datomic-transactor-pro-${VERSION}.jar datomic-transactor-pro.jar
popd
