#!/usr/bin/env bash

# create directory to store test results
mkdir ${REPORT_DIR}

# generate checkstyle report
vendor/bin/phpcs --config-set installed_paths vendor/cakephp/cakephp-codesniffer
vendor/bin/phpcs --standard=CakePHP --report=checkstyle --report-file=${REPORT_DIR}/checkstyle-result.xml src/

# generate duplicate code report (phpcpd not installed yet, incompatible with PHPUnit 6)
#vendor/bin/phpcpd

# generate mess detector report
vendor/bin/phpmd src/ xml codesize,design,unusedcode --reportfile ${REPORT_DIR}/pmd.xml

# update configuration file's timestamp
touch phpunit.xml.dist

# run tests
vendor/bin/phpunit --log-junit ${REPORT_DIR}/phpunit.xml --coverage-clover ${REPORT_DIR}/clover.xml --coverage-html ${REPORT_DIR}/clover.html