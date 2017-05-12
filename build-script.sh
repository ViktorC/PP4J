#!/bin/sh

chmod -R 777 /home
mvn clean install jacoco:report coveralls:report sonar:sonar